/*
 * НАЗНАЧЕНИЕ
 * Локальный HTTP-сервер, перехватывающий redirect браузера после авторизации во
 * ВКонтакте (модуль сбора данных, OAuth). Принимает либо authorization code (VK ID),
 * либо access_token из #fragment (Implicit Flow), и передаёт результат в OAuthFlow.
 *
 * ЧТО ВНУТРИ
 * data class CallbackResult (результат VK ID), класс LocalCallbackServer (запуск/останов
 * сервера, два эндпойнта и ожидание результата), исключение VkIdAuthException и набор
 * HTML-страниц (успех, ошибка, страница-перехватчик фрагмента) с встроенным JS.
 *
 * МЕТОД
 * Сервер слушает фиксированный порт на 127.0.0.1. Эндпойнт /callback различает три
 * случая: пришёл access_token (Implicit, переслан через JS), пришёл code (VK ID), пришла
 * ошибка. Если ни кода, ни ошибки нет, отдаётся HTML с JS, который читает токен из
 * #fragment (невидимого серверу) и пересылает его на /callback/token.
 *
 * ФРЕЙМВОРКИ / БИБЛИОТЕКИ
 * com.sun.net.httpserver.HttpServer — встроенный JDK HTTP-сервер; kotlinx.coroutines
 * (CompletableDeferred — асинхронное ожидание результата, withTimeoutOrNull — таймаут).
 *
 * СВЯЗИ
 * redirectUri передаётся в OAuthFlow при построении URL авторизации; результат
 * (код/токен) затем обрабатывается в OAuthFlow.
 */
package com.example.lomanalyzer.vk

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress

/** Результат потока VK ID: authorization code и сопутствующие device_id и state. */
data class CallbackResult(val code: String, val deviceId: String?, val state: String?)

/**
 * Локальный сервер для приёма OAuth-redirect от браузера на время авторизации.
 */
class LocalCallbackServer {
    companion object {
        /** Путь основного callback-эндпойнта. */
        private const val CALLBACK_PATH = "/callback"

        /** Путь, на который JS пересылает токен, извлечённый из #fragment. */
        private const val TOKEN_PATH = "/callback/token"

        /** Таймаут ожидания результата авторизации (мс). */
        private const val TIMEOUT_MS = 120_000L

        /** Фиксированный порт сервера (должен совпадать с зарегистрированным redirect_uri). */
        const val FIXED_PORT = 53921
    }

    private var server: HttpServer? = null

    /** Будущее с результатом VK ID (authorization code). */
    private val resultDeferred = CompletableDeferred<CallbackResult>()

    /** Будущее с access_token из Implicit Flow. */
    private val implicitTokenDeferred = CompletableDeferred<String>()

    /** Фактический порт запущенного сервера (0, если не запущен). */
    val port: Int get() = server?.address?.port ?: 0

    /** redirect_uri, который нужно передать VK и зарегистрировать в приложении. */
    val redirectUri: String get() = "http://localhost:$FIXED_PORT$CALLBACK_PATH"

    /**
     * Запускает HTTP-сервер и регистрирует обработчики эндпойнтов /callback и /callback/token.
     * @return этот же экземпляр (для цепочки вызовов).
     */
    fun start(): LocalCallbackServer {
        // Поднимаем встроенный JDK HTTP-сервер на локальном адресе и фиксированном порту
        server = HttpServer.create(InetSocketAddress("127.0.0.1", FIXED_PORT), 0).apply {
            // ── VK ID (Authorization Code) callback ──
            createContext(CALLBACK_PATH) { exchange ->
                // Разбираем query-параметры redirect
                val query = exchange.requestURI.query ?: ""
                val params = parseQuery(query)

                val code = params["code"]
                val error = params["error"]
                val accessToken = params["access_token"]

                // Выбираем ответ в зависимости от того, что пришло в redirect
                val html = when {
                    // JS posted the implicit token back to us
                    accessToken != null -> {
                        implicitTokenDeferred.complete(accessToken)
                        SUCCESS_HTML
                    }
                    // Authorization code flow (VK ID)
                    code != null -> {
                        resultDeferred.complete(
                            CallbackResult(
                                code = code,
                                deviceId = params["device_id"],
                                state = params["state"],
                            ),
                        )
                        SUCCESS_HTML
                    }
                    // Error from VK
                    error != null -> {
                        resultDeferred.completeExceptionally(
                            VkIdAuthException(error),
                        )
                        implicitTokenDeferred.completeExceptionally(
                            VkIdAuthException(error),
                        )
                        errorHtml(params["error_description"] ?: error)
                    }
                    // No code and no error — Implicit Flow redirect:
                    // token is in # fragment, serve JS to capture it
                    else -> FRAGMENT_CAPTURE_HTML
                }

                // Отдаём HTML-ответ браузеру (UTF-8)
                val bytes = html.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            // ── Endpoint where JS posts the captured implicit token ──
            // Эндпойнт, на который JS пересылает токен, извлечённый из #fragment
            createContext(TOKEN_PATH) { exchange ->
                val query = exchange.requestURI.query ?: ""
                val params = parseQuery(query)
                val token = params["access_token"]

                val html = if (token != null) {
                    // Токен получен — завершаем ожидание успехом
                    implicitTokenDeferred.complete(token)
                    SUCCESS_HTML
                } else {
                    // Токена нет — завершаем ожидание ошибкой
                    val err = params["error"] ?: "no_token"
                    implicitTokenDeferred.completeExceptionally(VkIdAuthException(err))
                    errorHtml(params["error_description"] ?: err)
                }

                val bytes = html.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            start() // запуск прослушивания
        }
        return this
    }

    /** Ждёт результат VK ID (authorization code) с таймаутом; null при истечении времени. */
    suspend fun awaitResult(): CallbackResult? =
        withTimeoutOrNull(TIMEOUT_MS) { resultDeferred.await() }

    /** Ждёт access_token Implicit Flow с таймаутом; null при истечении времени. */
    suspend fun awaitImplicitToken(): String? =
        withTimeoutOrNull(TIMEOUT_MS) { implicitTokenDeferred.await() }

    /** Останавливает сервер и освобождает порт. */
    fun stop() {
        server?.stop(0)
        server = null
    }

    /** Разбирает query-строку в Map, декодируя значения из percent-encoding. */
    private fun parseQuery(query: String): Map<String, String> =
        query.split("&").mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            if (pieces.size == 2) {
                pieces[0] to java.net.URLDecoder.decode(pieces[1], "UTF-8")
            } else {
                null
            }
        }.toMap()
}

/** Исключение авторизации VK ID: пробрасывается при ошибке в redirect или отсутствии токена. */
class VkIdAuthException(message: String) : RuntimeException(message)

/** Общие CSS-стили карточки для HTML-страниц обратной связи в браузере. */
private const val CARD_STYLE = """
body{font-family:system-ui,sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0;background:#0f172a;color:#e2e8f0}
.card{background:#1e293b;border-radius:16px;padding:48px;text-align:center;max-width:400px;box-shadow:0 20px 60px rgba(0,0,0,.4)}
.icon{font-size:48px;margin-bottom:16px}
h1{margin:0 0 8px;font-size:22px;color:#60a5fa}
p{margin:0;color:#94a3b8;font-size:14px}
"""

/** HTML-страница успешной авторизации, показываемая пользователю в браузере. */
@Suppress("MaxLineLength")
private val SUCCESS_HTML = """
<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>LOM Analyzer</title>
<style>$CARD_STYLE</style></head>
<body><div class="card"><div class="icon">&#10003;</div><h1>Авторизация успешна</h1><p>Вернитесь в приложение LOM Analyzer.<br>Это окно можно закрыть.</p></div></body></html>
""".trimIndent()

/** HTML-страница ошибки авторизации с текстом описания; красный акцент вместо синего. */
@Suppress("MaxLineLength")
private fun errorHtml(description: String) = """
<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>LOM Analyzer</title>
<style>${CARD_STYLE.replace("60a5fa", "f87171")}</style></head>
<body><div class="card"><div class="icon">&#10007;</div><h1>Ошибка авторизации</h1><p>$description</p></div></body></html>
""".trimIndent()

/**
 * HTML page served when implicit flow redirects to localhost.
 * The token lives in the URL #fragment which is invisible to the server,
 * so this page uses JavaScript to read window.location.hash and POST it
 * back to /callback/token.
 */
@Suppress("MaxLineLength")
private val FRAGMENT_CAPTURE_HTML = """
<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>LOM Analyzer</title>
<style>$CARD_STYLE .spinner{border:3px solid #334155;border-top:3px solid #60a5fa;border-radius:50%;width:32px;height:32px;animation:spin 1s linear infinite;margin:0 auto 16px}@keyframes spin{to{transform:rotate(360deg)}}</style></head>
<body><div class="card"><div class="spinner"></div><h1>Завершение авторизации…</h1><p>Подождите, идёт обработка.</p></div>
<script>
(function(){
  var h = window.location.hash.substring(1);
  if (!h) { document.querySelector('h1').textContent = 'Ошибка'; document.querySelector('p').textContent = 'Токен не найден в URL'; return; }
  var params = {};
  h.split('&').forEach(function(p){ var kv=p.split('='); if(kv.length===2) params[kv[0]]=decodeURIComponent(kv[1]); });
  var token = params['access_token'];
  if (token) {
    var img = new Image();
    img.src = '/callback/token?access_token=' + encodeURIComponent(token);
    document.querySelector('.spinner').style.display='none';
    document.querySelector('h1').textContent='Авторизация успешна';
    document.querySelector('h1').style.color='#60a5fa';
    document.querySelector('p').textContent='Вернитесь в приложение LOM Analyzer. Это окно можно закрыть.';
  } else {
    var err = params['error'] || 'unknown';
    var img2 = new Image();
    img2.src = '/callback/token?error=' + encodeURIComponent(err) + '&error_description=' + encodeURIComponent(params['error_description']||err);
    document.querySelector('.spinner').style.display='none';
    document.querySelector('h1').textContent='Ошибка авторизации';
    document.querySelector('h1').style.color='#f87171';
    document.querySelector('p').textContent=params['error_description']||err;
  }
})();
</script></body></html>
""".trimIndent()
