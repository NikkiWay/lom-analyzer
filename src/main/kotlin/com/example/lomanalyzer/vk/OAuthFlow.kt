/*
 * НАЗНАЧЕНИЕ
 * Авторизация пользователя во ВКонтакте и получение access token для вызовов VK API
 * (модуль сбора данных). Поддерживает два потока: современный VK ID OAuth 2.1 +
 * PKCE (через id.vk.com) и устаревший Implicit Flow (через oauth.vk.com) как fallback.
 *
 * ЧТО ВНУТРИ
 * Класс OAuthFlow: построение URL авторизации, разбор redirect, обмен кода на токены
 * (exchangeCodeForTokens), извлечение токена из фрагмента URL (Implicit Flow), хранение
 * access/refresh токенов и PKCE-хелперы (code_verifier, code_challenge, state).
 * Внизу — DTO VkIdTokenResponse для ответа эндпойнта токенов VK ID.
 *
 * МЕТОД
 * OAuth 2.1 Authorization Code + PKCE: клиент генерирует code_verifier, отправляет его
 * SHA-256-хеш (code_challenge, метод S256) при авторизации и сам verifier — при обмене
 * кода на токен; параметр state защищает от CSRF. Implicit Flow возвращает access_token
 * прямо во #fragment redirect URL.
 *
 * ФРЕЙМВОРКИ / БИБЛИОТЕКИ
 * Ktor client (submitForm — POST формы на эндпойнт токенов), kotlinx.serialization
 * (разбор JSON ответа токенов), java.security (SecureRandom, MessageDigest SHA-256,
 * Base64 URL-safe) для PKCE. TokenVault — защищённое хранилище токенов.
 *
 * СВЯЗИ
 * Работает в паре с LocalCallbackServer (локальный HTTP-сервер ловит redirect). Logger
 * пишет событие VK_OAUTH_COMPLETED. Полученный токен далее передаётся в VkApiClient.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.security.TokenVault
import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * OAuth flow supporting / Поток авторизации, поддерживающий:
 * - VK ID OAuth 2.1 + PKCE (primary, via id.vk.com) — основной путь;
 * - Implicit Flow via oauth.vk.com (legacy fallback) — устаревший резервный путь.
 *
 * @param tokenVault защищённое хранилище access/refresh токенов.
 * @param logger логгер событий авторизации.
 * @param httpClient Ktor client для обмена кода на токены (нужен только для VK ID PKCE).
 */
class OAuthFlow(
    private val tokenVault: TokenVault,
    private val logger: Logger,
    private val httpClient: HttpClient? = null,
) {
    companion object {
        // VK ID (OAuth 2.1): эндпойнты авторизации и обмена кода на токены
        private const val VKID_AUTH_URL = "https://id.vk.com/authorize"
        private const val VKID_TOKEN_URL = "https://id.vk.com/oauth2/auth"

        // Legacy (oauth.vk.com): URL авторизации и стандартный redirect-«заглушка»
        private const val LEGACY_AUTH_URL = "https://oauth.vk.com/authorize"
        private const val LEGACY_REDIRECT_URI = "https://oauth.vk.com/blank.html"

        // Запрашиваемые права (scope): стена, друзья, группы, статистика
        private const val VKID_SCOPE = "wall friends groups stats" // VK ID — через пробел
        private const val LEGACY_SCOPE = "wall,friends,groups,stats" // legacy — через запятую
        private const val VERIFIER_LENGTH = 64 // длина случайного code_verifier (байт) для PKCE
    }

    /** JSON-парсер ответа токенов; игнорирует неизвестные поля для устойчивости к изменениям API. */
    private val json = Json { ignoreUnknownKeys = true }

    /** PKCE code_verifier текущей сессии авторизации (генерируется при построении URL). */
    private var codeVerifier: String? = null

    /** Идентификатор устройства для VK ID; обновляется на каждую попытку авторизации. */
    private var deviceId: String = UUID.randomUUID().toString()

    /** Параметр state (anti-CSRF), сверяется/передаётся при обмене кода на токены. */
    private var oauthState: String? = null

    // ── VK ID (OAuth 2.1 + PKCE) ──

    /**
     * Строит URL авторизации VK ID. Генерирует code_verifier, его challenge (S256),
     * state и device_id, затем собирает строку запроса со всеми параметрами OAuth 2.1.
     * @return URL, который нужно открыть в браузере для входа пользователя.
     */
    fun buildVkIdAuthUrl(clientId: String, redirectUri: String = LEGACY_REDIRECT_URI): String {
        // Генерируем секреты PKCE и state для текущей попытки авторизации
        codeVerifier = generateCodeVerifier()
        deviceId = UUID.randomUUID().toString()
        oauthState = generateState()
        // code_challenge = base64url(SHA-256(code_verifier)) — отправляется при авторизации
        val challenge = generateCodeChallenge(codeVerifier!!)

        // URL-кодируем scope (пробелы как %20) и redirect_uri
        val encodedScope = java.net.URLEncoder.encode(VKID_SCOPE, "UTF-8").replace("+", "%20")
        val encodedRedirect = java.net.URLEncoder.encode(redirectUri, "UTF-8")

        val uuid = UUID.randomUUID().toString()

        // Собираем полный URL авторизации со всеми параметрами OAuth 2.1 + PKCE
        return "$VKID_AUTH_URL?uuid=$uuid" +
            "&client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=$encodedRedirect" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&scope=$encodedScope" +
            "&state=$oauthState" +
            "&device_id=$deviceId"
    }

    /**
     * Разбирает redirect VK ID, извлекая authorization code, device_id и state из query-строки.
     * @return CallbackResult с кодом или null, если кода нет.
     */
    fun parseVkIdRedirect(redirectUrl: String): CallbackResult? {
        // Берём часть URL после "?" — query-строку с параметрами
        val queryString = redirectUrl.substringAfter("?", "")
        if (queryString.isBlank()) return null
        // Разбираем пары key=value, декодируя значения из percent-encoding
        val params = queryString.split("&").mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            if (pieces.size == 2) {
                pieces[0] to java.net.URLDecoder.decode(pieces[1], "UTF-8")
            } else {
                null
            }
        }.toMap()
        val code = params["code"] ?: return null
        return CallbackResult(
            code = code,
            deviceId = params["device_id"],
            state = params["state"],
        )
    }

    /**
     * Обменивает authorization code на access/refresh токены (VK ID, grant_type=authorization_code).
     * Отправляет POST-форму на эндпойнт токенов, передавая code_verifier (PKCE) и state.
     * При успехе сохраняет токены и логирует VK_OAUTH_COMPLETED.
     * @param callbackDeviceId device_id из redirect (если пришёл), иначе используется локально сгенерированный.
     * @throws VkIdAuthException если в ответе нет access_token.
     */
    @Suppress("ReturnCount")
    suspend fun exchangeCodeForTokens(
        code: String,
        clientId: String,
        redirectUri: String,
        callbackDeviceId: String? = null,
    ): VkIdTokenResponse {
        // Для обмена кода нужен HTTP-клиент; без него продолжать нельзя
        val client = httpClient ?: throw IllegalStateException("HttpClient required for VK ID token exchange")

        // POST application/x-www-form-urlencoded на эндпойнт токенов VK ID
        val response = client.submitForm(
            url = VKID_TOKEN_URL,
            formParameters = io.ktor.http.parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("client_id", clientId)
                append("device_id", callbackDeviceId ?: deviceId)
                append("redirect_uri", redirectUri)
                append("code_verifier", codeVerifier ?: "") // подтверждение PKCE
                append("state", oauthState ?: "")
            },
        )

        // Читаем и разбираем JSON-ответ с токенами
        val body = response.bodyAsText()
        val tokenResponse = json.decodeFromString<VkIdTokenResponse>(body)

        // Нет access_token — собираем подробное сообщение об ошибке и бросаем исключение
        if (tokenResponse.accessToken.isNullOrBlank()) {
            val errorDetail = buildString {
                append(tokenResponse.error ?: "unknown_error")
                if (!tokenResponse.errorDescription.isNullOrBlank()) {
                    append(": ${tokenResponse.errorDescription}")
                }
                append(" [raw: $body]")
            }
            throw VkIdAuthException(errorDetail)
        }

        // Успех: сохраняем токены и фиксируем завершение авторизации
        storeTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
        logger.event(AppEvent.VK_OAUTH_COMPLETED, mapOf("method" to "vk_id_pkce"))
        return tokenResponse
    }

    // ── Legacy Implicit Flow (oauth.vk.com) ──

    /**
     * Строит URL авторизации устаревшего Implicit Flow (response_type=token):
     * access_token вернётся прямо во #fragment redirect URL.
     */
    fun buildImplicitAuthUrl(clientId: String, redirectUri: String = LEGACY_REDIRECT_URI): String {
        val encodedRedirect = java.net.URLEncoder.encode(redirectUri, "UTF-8")
        return "$LEGACY_AUTH_URL?client_id=$clientId" +
            "&display=page" +
            "&redirect_uri=$encodedRedirect" +
            "&scope=$LEGACY_SCOPE" +
            "&response_type=token" +
            "&v=5.199"
    }

    /** Совместимый псевдоним: по умолчанию строит URL Implicit Flow. */
    fun buildAuthUrl(clientId: String, redirectUri: String = LEGACY_REDIRECT_URI): String =
        buildImplicitAuthUrl(clientId, redirectUri)

    /**
     * Извлекает access_token из #fragment redirect URL (Implicit Flow) и сохраняет его.
     * @return токен или null, если фрагмент пуст либо токен отсутствует.
     */
    @Suppress("ReturnCount")
    fun handleImplicitRedirect(redirectUrl: String): String? {
        // В Implicit Flow токен находится в части URL после "#"
        val fragment = redirectUrl.substringAfter("#", "")
        if (fragment.isBlank()) return null
        // Разбираем пары key=value из фрагмента
        val params = fragment.split("&").associate { part ->
            val (k, v) = part.split("=", limit = 2)
            k to v
        }
        val token = params["access_token"] ?: return null
        // Сохраняем токен в защищённое хранилище
        tokenVault.store(token.toByteArray(Charsets.UTF_8))
        logger.event(AppEvent.VK_OAUTH_COMPLETED, mapOf("method" to "implicit"))
        return token
    }

    // ── Token storage ──

    /**
     * Сохраняет access token (и опционально refresh token) в TokenVault.
     * Два токена хранятся в двух строках, разделённых переводом строки.
     */
    fun storeTokens(accessToken: String, refreshToken: String? = null) {
        val tokenData = if (refreshToken != null) {
            "$accessToken\n$refreshToken"
        } else {
            accessToken
        }
        tokenVault.store(tokenData.toByteArray(Charsets.UTF_8))
    }

    /** Возвращает сохранённый access token (первая строка хранилища) или null. */
    fun getStoredToken(): String? {
        val bytes = tokenVault.get() ?: return null
        return String(bytes, Charsets.UTF_8).lines().first()
    }

    /** Возвращает сохранённый refresh token (вторая строка хранилища) или null, если его нет. */
    fun getStoredRefreshToken(): String? {
        val bytes = tokenVault.get() ?: return null
        val lines = String(bytes, Charsets.UTF_8).lines()
        return if (lines.size >= 2) lines[1] else null
    }

    /** true, если есть refresh token и возможно обновление access token. */
    fun shouldRefresh(): Boolean = getStoredRefreshToken() != null

    // ── PKCE helpers ──

    /**
     * Генерирует криптостойкий code_verifier: VERIFIER_LENGTH случайных байт из SecureRandom,
     * закодированных в base64url без выравнивания.
     */
    internal fun generateCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Вычисляет code_challenge по методу S256: base64url(SHA-256(verifier)).
     * Отправляется на этапе авторизации, тогда как сам verifier — при обмене кода.
     */
    internal fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    /** Генерирует случайный параметр state (16 байт base64url) для защиты от CSRF. */
    private fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/**
 * DTO ответа эндпойнта токенов VK ID. Содержит access/refresh токены, срок жизни,
 * id пользователя и scope, либо поля error/error_description при ошибке.
 */
@Serializable
data class VkIdTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    val state: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)
