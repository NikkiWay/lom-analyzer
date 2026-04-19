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

class VkExecuteBatcherTest {

    private val jsonHeaders = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

    @Test
    fun `groups calls into batches of 25`() = runBlocking {
        var requestCount = 0
        val body = """{"response":[""" +
            (1..25).joinToString(",") + "]}"

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requestCount++
                    respond(body, HttpStatusCode.OK, jsonHeaders)
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }

        val rateLimiter = VkRateLimiter(maxRequestsPerSecond = 100)
        val backoff = VkBackoff(maxRetries = 1, maxDelaySeconds = 1)
        val apiClient = VkApiClient(client, rateLimiter, backoff)
        val batcher = VkExecuteBatcher(apiClient, maxBatchSize = 25)

        val calls = (1..50).map {
            BatchedCall("wall.get", mapOf("owner_id" to "-$it", "count" to "1"))
        }

        val results = batcher.executeBatch(calls, "test_token")
        assertEquals(2, requestCount)
        assertEquals(50, results.size)
    }

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
        val batcher = VkExecuteBatcher(apiClient)

        val calls = listOf(
            BatchedCall("users.get", mapOf("user_ids" to "1")),
            BatchedCall("users.get", mapOf("user_ids" to "2")),
            BatchedCall("users.get", mapOf("user_ids" to "3")),
        )

        val results = batcher.executeBatch(calls, "tok")
        assertEquals(1, requestCount)
        assertEquals(3, results.size)
    }
}
