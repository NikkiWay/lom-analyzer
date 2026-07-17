/*
 * НАЗНАЧЕНИЕ
 * Выбор реализации NLP-модуля и оборачивание её в кэширующий слой (диплом 2.2.7,
 * architecture.md). Реализует двухрежимность: пробует поднять Python sidecar; если
 * удалось — работает в режиме FULL, иначе деградирует в FALLBACK_ONLY (Kotlin).
 *
 * ЧТО ВНУТРИ
 *  NlpServiceSelector — управляет жизненным циклом: initialize() (выбор и старт),
 *  getService()/getServiceOrNull() (доступ), shutdown() (остановка sidecar);
 *  свойство mode хранит текущий режим.
 *
 * МЕТОД
 *  initialize() запускает PythonServiceManager. При успехе создаётся
 *  PythonSidecarNlpService (pymorphy3 + Dostoevsky + RuBERT-tiny2, modelVersion="python_v1"),
 *  при неудаче — LocalKotlinNlpService (Snowball + RuSentiLex, "kotlin_fallback") с событием
 *  NLP_MODE_DOWNGRADED. Выбранный сервис оборачивается в CachingNlpService, если задан DAO.
 *
 * БИБЛИОТЕКИ / СВЯЗИ
 *  Ktor HttpClient (HTTP к sidecar), PythonServiceManager (управление процессом),
 *  NlpResultDao (кэш). Версия модели — часть ключа кэша.
 */
package com.example.lomanalyzer.nlp

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PythonServiceManager
import com.example.lomanalyzer.storage.dao.NlpResultDao
import io.ktor.client.*

/**
 * Выбирает реализацию NLP и оборачивает её в кэширующий слой (диплом 2.2.7).
 *
 * Основной режим: Python sidecar (pymorphy3 + Dostoevsky + RuBERT-tiny2).
 * Fallback: Kotlin (Snowball stemmer + словарь RuSentiLex).
 * Кэш: таблица nlp_result по ключу SHA-256(text) + версия модели.
 */
class NlpServiceSelector(
    private val pythonServiceManager: PythonServiceManager,
    private val localService: LocalKotlinNlpService,
    private val httpClient: HttpClient,
    private val logger: Logger,
    private val nlpResultDao: NlpResultDao? = null,
) {
    /** Текущий режим NLP: "FULL" (sidecar) или "FALLBACK_ONLY" (Kotlin). По умолчанию fallback. */
    var mode: String = "FALLBACK_ONLY"
        private set

    /**
     * Версии используемых моделей (JSON) — для записи в сессию.
     *
     * В режиме FULL их отдаёт sidecar при прогреве; в режиме FALLBACK_ONLY модели
     * нет, и указывается словарная реализация.
     */
    val modelVersions: String
        get() = if (mode == "FULL") {
            pythonServiceManager.modelVersions ?: """{"models":"неизвестны: прогрев не удался"}"""
        } else {
            """{"models":{"sentiment":"kotlin_rusentilex","lemmatizer":"lucene_snowball"}}"""
        }

    /** Выбранный (и, возможно, кэширующий) сервис; заполняется в initialize(). */
    private var selectedService: NlpService? = null

    /**
     * Инициализирует NLP: пробует поднять Python sidecar, иначе переходит на Kotlin-fallback.
     * @return готовый к работе NlpService (с кэшем, если доступен DAO).
     */
    suspend fun initialize(): NlpService {
        // Пытаемся запустить Python sidecar
        val started = pythonServiceManager.start()
        val rawService: NlpService
        val modelVersion: String

        if (started) {
            // Режим FULL: sidecar поднят, обращаемся к нему по HTTP
            mode = "FULL"
            modelVersion = "python_v1"
            rawService = PythonSidecarNlpService(
                httpClient = httpClient,
                baseUrl = pythonServiceManager.baseUrl(),
                secret = pythonServiceManager.secret,
                logger = logger,
            )
        } else {
            // Режим FALLBACK_ONLY: sidecar недоступен — деградируем на Kotlin и логируем событие
            mode = "FALLBACK_ONLY"
            modelVersion = "kotlin_fallback"
            rawService = localService
            logger.event(AppEvent.NLP_MODE_DOWNGRADED, mapOf("reason" to "sidecar_unavailable"))
        }

        // Оборачиваем в кэширующий слой, если доступен DAO кэша
        selectedService = if (nlpResultDao != null) {
            CachingNlpService(rawService, nlpResultDao, modelVersion, logger)
        } else {
            rawService
        }

        return selectedService!!
    }

    /** Возвращает выбранный сервис; до initialize() — локальный Kotlin-сервис. */
    fun getService(): NlpService = selectedService ?: localService

    /** Возвращает сервис sidecar, если режим FULL, иначе null (для функций, требующих ML). */
    fun getServiceOrNull(): NlpService? = if (mode == "FULL") selectedService else null

    /** Останавливает Python sidecar при завершении работы. */
    fun shutdown() {
        pythonServiceManager.stop()
    }
}
