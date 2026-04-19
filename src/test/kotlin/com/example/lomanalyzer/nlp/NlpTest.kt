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
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

class NlpTest {

    @Test
    fun `NlpServiceSelector falls back when sidecar cannot start`() = runBlocking {
        val logger = Logger("test")
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

        val pythonManager = PythonServiceManager(
            pythonEnvPath = Paths.get("/nonexistent/python/path"),
            httpClient = mockClient,
            logger = logger,
            maxRetries = 1,
        )

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

        val service = selector.initialize()
        assertEquals("FALLBACK_ONLY", selector.mode)
        // Should be the local service
        assertTrue(service is LocalKotlinNlpService)
    }

    @Test
    fun `PythonSidecarNlpService semaphore limits concurrency to 4`() = runBlocking {
        val concurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val mockClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    val current = concurrent.incrementAndGet()
                    maxConcurrent.updateAndGet { max -> maxOf(max, current) }
                    delay(100)
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
        val jobs = (1..8).map {
            async { service.lemmatize("word $it") }
        }
        jobs.awaitAll()

        assertTrue(
            maxConcurrent.get() <= 4,
            "Max concurrent was ${maxConcurrent.get()}, expected <= 4"
        )
    }

    @Test
    fun `LocalKotlinNlpService lemmatize works`() = runBlocking {
        val service = LocalKotlinNlpService(
            LemmatizerProxy(),
            LanguageDetectorProxy(),
        )
        val result = service.lemmatize("читал книги")
        assertTrue(result.lemmas.isNotEmpty())
    }

    @Test
    fun `LocalKotlinNlpService detectLanguage works for Russian`() = runBlocking {
        val service = LocalKotlinNlpService(
            LemmatizerProxy(),
            LanguageDetectorProxy(),
        )
        val result = service.detectLanguage("это был хороший день для всех людей в мире")
        assertEquals("ru", result.language)
    }

    @Test
    fun `LocalKotlinNlpService sentiment returns stub`() = runBlocking {
        val service = LocalKotlinNlpService(
            LemmatizerProxy(),
            LanguageDetectorProxy(),
        )
        val result = service.scoreSentiment("текст")
        assertEquals("NEUTRAL", result.label)
        assertEquals("DICTIONARY_STUB", result.method)
    }
}
