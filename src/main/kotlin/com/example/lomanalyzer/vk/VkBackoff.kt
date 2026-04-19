package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import io.ktor.client.plugins.*
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

class VkApiException(message: String, val httpStatus: Int? = null) : RuntimeException(message)

class VkBackoff(
    private val maxRetries: Int = 5,
    private val maxDelaySeconds: Long = 30,
    private val logger: Logger? = null,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (@Suppress("SwallowedException") e: Exception) {
                val status = extractHttpStatus(e)
                if (status != null && status != 429 && status !in 500..599) {
                    throw e
                }
                attempt++
                if (attempt > maxRetries) {
                    throw VkApiException(
                        "Max retries ($maxRetries) exceeded: ${e.message}",
                        status,
                    )
                }
                val baseDelay = min(1L shl attempt, maxDelaySeconds)
                val jitter = (Random.nextDouble() * 1000).toLong()
                val totalMs = baseDelay * 1000 + jitter
                logger?.event(AppEvent.BACKOFF_APPLIED, mapOf(
                    "attempt" to attempt,
                    "delay_ms" to totalMs,
                    "status" to (status ?: "unknown"),
                ))
                delay(totalMs)
            }
        }
    }

    private fun extractHttpStatus(e: Exception): Int? = when (e) {
        is ResponseException -> e.response.status.value
        is VkApiException -> e.httpStatus
        else -> null
    }
}
