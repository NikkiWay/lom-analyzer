/*
 * НАЗНАЧЕНИЕ
 * Тесты NLP-инфраструктуры (этап 3 пайплайна — препроцессинг/лингвистика).
 * Проверяют выбор источника NLP (Python FastAPI sidecar или локальный Kotlin-
 * fallback), ограничение параллелизма обращений к sidecar и базовые операции
 * локального сервиса (лемматизация, определение языка, словарная тональность).
 *
 * ЧТО ВНУТРИ
 * Класс NlpTest с пятью @Test:
 *  - fallback NlpServiceSelector при недоступном sidecar;
 *  - семафор PythonSidecarNlpService ограничивает параллелизм до 4;
 *  - лемматизация LocalKotlinNlpService;
 *  - определение русского языка LocalKotlinNlpService;
 *  - метод тональности LocalKotlinNlpService — словарный (DICTIONARY).
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (org.junit.jupiter) — движок тестов, assertEquals/assertTrue.
 * ktor-client-mock (MockEngine) — подмена HTTP-ответов sidecar без реальной сети.
 * kotlinx.coroutines (runBlocking, async, awaitAll, delay) — тесты suspend-кода.
 * AtomicInteger — потокобезопасный подсчёт одновременных запросов в тесте семафора.
 *
 * СВЯЗИ
 * NlpServiceSelector, PythonServiceManager, PythonSidecarNlpService,
 * LocalKotlinNlpService, LemmatizerProxy, LanguageDetectorProxy.
 */
package com.example.lomanalyzer.nlp

import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PythonServiceManager
import com.example.lomanalyzer.preprocessing.LanguageDetectorProxy
import com.example.lomanalyzer.preprocessing.LemmatizerProxy
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

/** Набор тестов выбора и работы NLP-сервисов (sidecar и локальный fallback). */
class NlpTest {

    /**
     * Инвариант: если Python sidecar не может стартовать, селектор обязан
     * переключиться в режим FALLBACK_ONLY и вернуть локальный Kotlin-сервис.
     * Arrange: MockEngine всегда отвечает 503 (ServiceUnavailable), путь к python
     * заведомо несуществующий, maxRetries=1 — чтобы быстро исчерпать попытки старта.
     * Act: selector.initialize(). Assert: режим FALLBACK_ONLY и тип LocalKotlinNlpService.
     */
    @Test
    fun `NlpServiceSelector falls back when sidecar cannot start`() = runBlocking {
        val logger = Logger("test")
        // MockEngine, имитирующий недоступный sidecar — на любой запрос отдаёт 503
        val mockClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respondError(HttpStatusCode.ServiceUnavailable)
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Менеджер процесса Python с заведомо несуществующим путём — старт обречён
        val pythonManager = PythonServiceManager(
            pythonEnvPath = Paths.get("/nonexistent/python/path"),
            httpClient = mockClient,
            logger = logger,
            sidecarLogFile = Files.createTempDirectory("sidecar_log_").resolve("python_sidecar.log"),
            maxRetries = 1,
        )

        // Локальный Kotlin-сервис, на который должен переключиться селектор
        val localService = LocalKotlinNlpService(
            LemmatizerProxy(),
            LanguageDetectorProxy(),
        )

        val selector = NlpServiceSelector(
            pythonServiceManager = pythonManager,
            localService = localService,
            httpClient = mockClient,
            logger = logger,
        )

        // Act: инициализация выбирает доступный сервис
        val service = selector.initialize()
        // Assert: sidecar недоступен → режим только fallback
        assertEquals("FALLBACK_ONLY", selector.mode)
        // Should be the local service
        // и возвращён именно локальный сервис
        assertTrue(service is LocalKotlinNlpService)
    }

    /**
     * Инвариант: PythonSidecarNlpService через семафор не допускает более
     * maxConcurrency=4 одновременных запросов к sidecar (защита от перегрузки).
     * Arrange: MockEngine считает текущее и максимальное число параллельных
     * обработчиков (delay(100) удерживает запрос «в работе»). Запускаем 8 задач.
     * Assert: зафиксированный пик одновременных запросов не превышает 4.
     */
    @Test
    fun `PythonSidecarNlpService semaphore limits concurrency to 4`() = runBlocking {
        // Счётчики: текущее число активных запросов и наблюдаемый максимум
        val concurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val mockClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    // Вход в обработчик — увеличиваем счётчик активных
                    val current = concurrent.incrementAndGet()
                    // Обновляем наблюдаемый пик параллелизма
                    maxConcurrent.updateAndGet { max -> maxOf(max, current) }
                    // Удерживаем запрос «в работе», чтобы задачи реально пересеклись
                    delay(100)
                    // Выход из обработчика — уменьшаем счётчик активных
                    concurrent.decrementAndGet()
                    respond(
                        """{"lemmas":["test"]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val service = PythonSidecarNlpService(
            httpClient = mockClient,
            baseUrl = "http://localhost:9999",
            secret = "test",
            logger = Logger("test"),
            maxConcurrency = 4,
        )

        // Launch 8 concurrent requests
        // Запускаем 8 параллельных лемматизаций — вдвое больше лимита семафора
        val jobs = (1..8).map {
            async { service.lemmatize("word $it") }
        }
        jobs.awaitAll()

        // Assert: семафор удержал параллелизм в пределах 4
        assertTrue(
            maxConcurrent.get() <= 4,
            "Max concurrent was ${maxConcurrent.get()}, expected <= 4"
        )
    }

    /**
     * Проверка базовой лемматизации локального сервиса: для непустого текста
     * возвращается непустой список лемм (Snowball-стеммер всегда что-то выдаёт).
     */
    @Test
    fun `LocalKotlinNlpService lemmatize works`() = runBlocking {
        val service = LocalKotlinNlpService(
            LemmatizerProxy(),
            LanguageDetectorProxy(),
        )
        // Лемматизация русской фразы — ожидаем хотя бы одну лемму
        val result = service.lemmatize("читал книги")
        assertTrue(result.lemmas.isNotEmpty())
    }

    /**
     * Проверка определения языка: явно русский текст должен классифицироваться
     * как "ru" локальным детектором (по доле кириллицы/стоп-слов).
     */
    @Test
    fun `LocalKotlinNlpService detectLanguage works for Russian`() = runBlocking {
        val service = LocalKotlinNlpService(
            LemmatizerProxy(),
            LanguageDetectorProxy(),
        )
        // Длинная русская фраза — уверенное определение языка
        val result = service.detectLanguage("это был хороший день для всех людей в мире")
        assertEquals("ru", result.language)
    }

    /**
     * Проверка, что в fallback-режиме тональность считается словарным методом:
     * поле method == "DICTIONARY" (нет нейросетевой модели dostoevsky/RuBERT).
     */
    @Test
    fun `LocalKotlinNlpService sentiment uses dictionary`() = runBlocking {
        val service = LocalKotlinNlpService(
            LemmatizerProxy(),
            LanguageDetectorProxy(),
        )
        // Тональность в локальном сервисе всегда словарная
        val result = service.scoreSentiment("текст")
        assertEquals("DICTIONARY", result.method)
    }
}
