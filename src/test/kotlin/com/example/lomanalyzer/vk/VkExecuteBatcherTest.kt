/*
 * НАЗНАЧЕНИЕ
 * Тесты VkExecuteBatcher — упаковки нескольких вызовов VK API в один метод
 * execute (до 25 подзапросов за раз) для снижения числа HTTP-запросов и нагрузки
 * на rate limit (этап 2 — сбор данных).
 *
 * ЧТО ВНУТРИ
 * Класс VkExecuteBatcherTest (HTTP подменяется ktor-client-mock):
 *  - 50 вызовов группируются в 2 батча по 25 (2 HTTP-запроса, 50 результатов);
 *  - менее 25 вызовов — один батч (1 HTTP-запрос).
 *
 * МЕТОД
 * VK execute выполняет до 25 вложенных вызовов за один HTTP-запрос; батчер режет
 * список вызовов на группы по maxBatchSize и собирает результаты обратно.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (assert*). ktor-client-mock (MockEngine) — счётчик HTTP-запросов и
 * фиктивные JSON-ответы. kotlinx.coroutines.runBlocking — suspend-вызовы.
 *
 * СВЯЗИ
 * VkExecuteBatcher, BatchedCall, VkApiClient, VkRateLimiter, VkBackoff.
 */
package com.example.lomanalyzer.vk

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Тесты батчирования вызовов VK API через метод execute. */
class VkExecuteBatcherTest {

    /** Общие HTTP-заголовки JSON-ответа для MockEngine. */
    private val jsonHeaders = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

    /**
     * 50 вызовов при размере батча 25 должны дать 2 HTTP-запроса и 50 результатов.
     * Arrange: MockEngine считает запросы и отдаёт массив из 25 элементов.
     * Assert: requestCount==2 (два батча), результатов 50.
     */
    @Test
    fun `groups calls into batches of 25`() = runBlocking {
        var requestCount = 0
        // Ответ execute — массив из 25 значений (по числу подзапросов в батче)
        val body = """{"response":[""" +
            (1..25).joinToString(",") + "]}"

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    // Считаем число фактических HTTP-запросов
                    requestCount++
                    respond(body, HttpStatusCode.OK, jsonHeaders)
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }

        // Высокий лимит rate limiter, чтобы он не влиял на число запросов
        val rateLimiter = VkRateLimiter(maxRequestsPerSecond = 100)
        val backoff = VkBackoff(maxRetries = 1, maxDelaySeconds = 1)
        val apiClient = VkApiClient(client, rateLimiter, backoff)
        val batcher = VkExecuteBatcher(apiClient, maxBatchSize = 25)

        // 50 вызовов wall.get → должны разбиться на 2 батча
        val calls = (1..50).map {
            BatchedCall("wall.get", mapOf("owner_id" to "-$it", "count" to "1"))
        }

        val results = batcher.executeBatch(calls, "test_token")
        // Два HTTP-запроса (по 25 вызовов)
        assertEquals(2, requestCount)
        // Собрано 50 результатов
        assertEquals(50, results.size)
    }

    /**
     * Менее 25 вызовов укладываются в один батч: один HTTP-запрос и столько же
     * результатов, сколько вызовов (здесь 3).
     */
    @Test
    fun `single batch for fewer than 25 calls`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requestCount++
                    respond(
                        """{"response":["a","b","c"]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }

        val rateLimiter = VkRateLimiter(maxRequestsPerSecond = 100)
        val backoff = VkBackoff(maxRetries = 1, maxDelaySeconds = 1)
        val apiClient = VkApiClient(client, rateLimiter, backoff)
        // Размер батча по умолчанию
        val batcher = VkExecuteBatcher(apiClient)

        // Три вызова users.get — один батч
        val calls = listOf(
            BatchedCall("users.get", mapOf("user_ids" to "1")),
            BatchedCall("users.get", mapOf("user_ids" to "2")),
            BatchedCall("users.get", mapOf("user_ids" to "3")),
        )

        val results = batcher.executeBatch(calls, "tok")
        // Один HTTP-запрос
        assertEquals(1, requestCount)
        assertEquals(3, results.size)
    }
}
