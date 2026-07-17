/*
 * НАЗНАЧЕНИЕ
 * Управление жизненным циклом Python NLP sidecar — отдельного процесса FastAPI,
 * который выполняет лемматизацию, sentiment и эмбеддинги (dostoevsky, pymorphy3,
 * natasha, rubert-tiny2). Менеджер запускает процесс, выбирает свободный порт,
 * генерирует секрет авторизации, ждёт готовности по health-эндпоинту и умеет
 * перезапускать sidecar при сбое. Останавливается из App.kt при выходе.
 *
 * ЧТО ВНУТРИ
 * Класс PythonServiceManager: свойства port/secret/process/modelVersions (только
 * чтение извне), колбэк onPermanentFailure; методы start (с ретраями), stop,
 * baseUrl; приватные findFreePort, generateSecret, waitForHealth, warmUpModels,
 * resolvePythonExecutable; константы диапазона портов и таймаутов.
 *
 * АЛГОРИТМ / ПОТОК ВЫПОЛНЕНИЯ
 * start пытается до maxRetries раз: выбрать порт → сгенерировать секрет → запустить
 * процесс python с main.py (порт+секрет) → дождаться health → прогреть модели. Из
 * окружения процесса вычищаются прокси-переменные (SOCKS-прокси ломает загрузку
 * моделей huggingface). При исчерпании попыток вызывается onPermanentFailure и
 * возвращается false.
 *
 * Health отвечает сразу, но модели sidecar грузятся лениво, и холодная загрузка
 * длится дольше рабочих таймаутов. Прогрев (/warmup) выполняет её сразу после
 * старта, под отдельным таймаутом, и отдаёт версии моделей для записи в сессию.
 *
 * БИБЛИОТЕКИ
 * Ktor HttpClient — health-запросы к sidecar с заголовком X-Auth-Token; java.net.ServerSocket
 * — поиск свободного порта; java.security.SecureRandom — генерация секрета;
 * java.lang.ProcessBuilder — запуск процесса; kotlinx.coroutines.delay — опрос health.
 *
 * СВЯЗИ
 * Использует venv, подготовленный BootstrapPythonInstaller; baseUrl/secret/port
 * используются NLP-клиентом для обращения к sidecar.
 */
package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import io.ktor.client.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import java.net.ServerSocket
import java.nio.file.Path
import java.security.SecureRandom

/**
 * Запускает и контролирует Python NLP sidecar.
 *
 * @param pythonEnvPath путь к venv с установленным Python и NLP-зависимостями.
 * @param httpClient Ktor-клиент для health-проверок sidecar.
 * @param logger логгер событий старта/перезапуска/отказа.
 * @param maxRetries сколько раз пытаться запустить sidecar перед фатальным отказом.
 */
class PythonServiceManager(
    private val pythonEnvPath: Path,
    private val httpClient: HttpClient,
    private val logger: Logger,
    private val sidecarLogFile: Path,
    private val maxRetries: Int = 3,
) {
    /** Порт, на котором слушает запущенный sidecar (0, пока не запущен). */
    var port: Int = 0
        private set
    /** Секрет авторизации (hex), передаётся sidecar и в заголовке X-Auth-Token. */
    var secret: String = ""
        private set
    /**
     * Версии моделей, загруженных при прогреве (JSON от /warmup); null, если
     * прогрев не удался. Сохраняется в сессию, чтобы по её результатам можно было
     * сказать, чем именно она посчитана.
     */
    var modelVersions: String? = null
        private set

    /** Ссылка на запущенный процесс sidecar (null, если не запущен). */
    var process: Process? = null
        private set
    /** Колбэк, вызываемый при окончательном отказе запустить sidecar. */
    var onPermanentFailure: () -> Unit = {}

    /**
     * Запускает sidecar с ретраями.
     * @return true, если процесс поднялся и ответил health OK; иначе false.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun start(): Boolean {
        // До maxRetries попыток поднять sidecar
        for (attempt in 1..maxRetries) {
            try {
                // Каждая попытка — свежий порт и новый секрет авторизации
                port = findFreePort()
                secret = generateSecret()

                val pythonExe = resolvePythonExecutable()

                // Команда запуска: python main.py --port <порт> --secret <секрет>
                val pb = ProcessBuilder(
                    pythonExe, SIDECAR_SCRIPT,
                    "--port", port.toString(),
                    "--secret", secret,
                )
                // Clear proxy env vars — SOCKS proxy breaks huggingface downloads
                // Вычищаем прокси из окружения: SOCKS-прокси ломает загрузку моделей huggingface
                pb.environment().apply {
                    remove("http_proxy"); remove("https_proxy"); remove("all_proxy")
                    remove("HTTP_PROXY"); remove("HTTPS_PROXY"); remove("ALL_PROXY")
                    put("NO_PROXY", "*")
                }
                // Весь вывод sidecar (stdout + stderr) уходит в файл, а не в конвейер.
                //
                // Это не удобство, а условие работоспособности: вывод дочернего процесса
                // в неоткачиваемый конвейер накапливается в буфере ОС (в Windows — 4 КБ),
                // и по его заполнении процесс встаёт на очередной записи в stdout
                // навсегда. uvicorn печатает строку на КАЖДЫЙ запрос, а transformers —
                // прогресс загрузки моделей, так что предел выбирается за один прогон:
                // sidecar переставал отвечать в середине сессии, включая /health.
                //
                // Файл заодно даёт диагностику: traceback'и Python иначе не видны нигде.
                pb.redirectErrorStream(true)
                sidecarLogFile.parent?.let { java.nio.file.Files.createDirectories(it) }
                pb.redirectOutput(ProcessBuilder.Redirect.to(sidecarLogFile.toFile()))
                process = pb.start()

                // Ждём, пока sidecar ответит health OK
                if (waitForHealth()) {
                    // Первая удачная попытка — STARTED, последующие — RESTARTED
                    logger.event(
                        if (attempt == 1) AppEvent.PYTHON_STARTED
                        else AppEvent.PYTHON_RESTARTED,
                        mapOf("port" to port, "attempt" to attempt),
                    )
                    // Health означает лишь, что процесс отвечает: модели грузятся
                    // лениво, и первым их ждёт рабочий запрос — под своим таймаутом.
                    warmUpModels()
                    return true
                }

                // Health не дождались — принудительно убиваем процесс и пробуем снова
                process?.destroyForcibly()
            } catch (e: Exception) {
                // Любой сбой попытки логируем и убиваем недозапущенный процесс
                logger.warn("Python sidecar start attempt $attempt failed: ${e.message}")
                process?.destroyForcibly()
            }
        }

        // Все попытки исчерпаны — фатальный отказ, уведомляем подписчика
        logger.event(AppEvent.PYTHON_FAILED_PERMANENT)
        onPermanentFailure()
        return false
    }

    /** Принудительно останавливает sidecar (вызывается при выходе из приложения). */
    fun stop() {
        process?.destroyForcibly()
        process = null
    }

    /** Базовый URL запущенного sidecar (localhost + выбранный порт). */
    fun baseUrl(): String = "http://127.0.0.1:$port"

    /** Ищет свободный порт в заданном диапазоне; при неудаче отдаёт выбор ОС. */
    private fun findFreePort(): Int {
        // Пробуем порты диапазона по очереди: занятый порт бросит исключение
        for (p in PORT_RANGE_START..PORT_RANGE_END) {
            try {
                ServerSocket(p).use { return p }
            } catch (_: Exception) {
                continue
            }
        }
        // Fallback: let OS pick
        // Запасной вариант: попросить ОС выдать любой свободный порт (порт 0)
        return ServerSocket(0).use { it.localPort }
    }

    /** Генерирует случайный секрет авторизации в hex (SECRET_LENGTH байт). */
    private fun generateSecret(): String {
        val bytes = ByteArray(SECRET_LENGTH)
        // Криптостойкий генератор — секрет не должен быть предсказуемым
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Заранее загружает модели sidecar и запоминает их версии.
     *
     * Модели грузятся при первом обращении к эндпоинту, и холодная загрузка длится
     * дольше рабочих таймаутов: замер даёт около 82 секунд при лимите запроса в 30.
     * Без прогрева её ждёт первый же рабочий вызов, отваливается по таймауту, и
     * вызывающая сторона считает модель недоступной.
     *
     * Отказ прогрева не мешает старту: sidecar жив, а деградацию фиксирует тот
     * этап, который с ней столкнётся.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun warmUpModels() {
        try {
            val resp = httpClient.post("http://127.0.0.1:$port/warmup") {
                header("X-Auth-Token", secret)
                // Свой таймаут: общий (30 с) рассчитан на рабочие запросы к готовой
                // модели, а здесь ожидание включает загрузку, а то и скачивание весов.
                timeout { requestTimeoutMillis = WARMUP_TIMEOUT_MS }
            }
            val body = resp.bodyAsText()
            modelVersions = body
            logger.event(AppEvent.PYTHON_MODELS_WARMED, mapOf("models" to body))
        } catch (e: Exception) {
            // Модели остались незагруженными: первый рабочий вызов рискует упереться в таймаут
            logger.warn("Sidecar model warm-up failed: ${e.message}")
        }
    }

    /** Опрашивает /health до таймаута; @return true, как только sidecar готов. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun waitForHealth(): Boolean {
        // Крайний срок ожидания готовности sidecar
        val deadline = System.currentTimeMillis() + HEALTH_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            try {
                // Health-запрос с тем же секретом, что передан процессу
                val resp = httpClient.get("http://127.0.0.1:$port/health") {
                    header("X-Auth-Token", secret)
                }
                if (resp.status == HttpStatusCode.OK) return true
            } catch (_: Exception) {
                // Not ready yet
                // Sidecar ещё не поднялся — игнорируем и повторяем опрос
            }
            // Пауза между опросами, чтобы не нагружать процесс
            delay(HEALTH_POLL_INTERVAL_MS)
        }
        return false
    }

    /** Возвращает путь к python внутри venv в зависимости от ОС. */
    private fun resolvePythonExecutable(): String {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) {
            pythonEnvPath.resolve("Scripts/python.exe").toString()
        } else {
            pythonEnvPath.resolve("bin/python").toString()
        }
    }

    companion object {
        /** Нижняя граница диапазона портов для поиска свободного. */
        private const val PORT_RANGE_START = 8300
        /** Верхняя граница диапазона портов для поиска свободного. */
        private const val PORT_RANGE_END = 8399
        /** Максимальное время ожидания готовности sidecar, мс. */
        private const val HEALTH_TIMEOUT_MS = 5000L
        /** Интервал между опросами health, мс. */
        private const val HEALTH_POLL_INTERVAL_MS = 500L
        /**
         * Предел ожидания прогрева моделей, мс (10 минут).
         *
         * Много больше рабочих таймаутов намеренно: при первом запуске сюда входит
         * скачивание весов с HuggingFace, а на холодной машине — ещё и распаковка.
         * Ограничение нужно лишь как страховка от зависшего процесса.
         */
        private const val WARMUP_TIMEOUT_MS = 600_000L
        /** Длина секрета авторизации в байтах (в hex даёт вдвое длиннее). */
        private const val SECRET_LENGTH = 32
        /** Путь к скрипту запуска sidecar (FastAPI). */
        private const val SIDECAR_SCRIPT = "nlp/python/main.py"
    }
}
