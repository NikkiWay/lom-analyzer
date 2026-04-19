package com.example.lomanalyzer.vk

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VkBackoffTest {

    @Test
    fun `succeeds on first attempt without delay`() = runBlocking {
        val backoff = VkBackoff(maxRetries = 5, maxDelaySeconds = 1)
        var attempts = 0
        val result = backoff.withRetry {
            attempts++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retries on 429 and succeeds`() = runBlocking {
        val backoff = VkBackoff(maxRetries = 5, maxDelaySeconds = 1)
        var attempts = 0
        val result = backoff.withRetry {
            attempts++
            if (attempts < 3) {
                throw VkApiException("Rate limited", 429)
            }
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retries on 500 and succeeds`() = runBlocking {
        val backoff = VkBackoff(maxRetries = 5, maxDelaySeconds = 1)
        var attempts = 0
        val result = backoff.withRetry {
            attempts++
            if (attempts < 2) {
                throw VkApiException("Server error", 500)
            }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `throws after max retries exceeded`() = runBlocking {
        val backoff = VkBackoff(maxRetries = 3, maxDelaySeconds = 1)
        var attempts = 0
        val ex = assertThrows(VkApiException::class.java) {
            runBlocking {
                backoff.withRetry<String> {
                    attempts++
                    throw VkApiException("Always fails", 429)
                }
            }
        }
        assertEquals(4, attempts) // 1 initial + 3 retries
        assertTrue(ex.message!!.contains("Max retries"))
    }

    @Test
    fun `does not retry on 400 client error`() = runBlocking {
        val backoff = VkBackoff(maxRetries = 5, maxDelaySeconds = 1)
        var attempts = 0
        val ex = assertThrows(VkApiException::class.java) {
            runBlocking {
                backoff.withRetry<String> {
                    attempts++
                    throw VkApiException("Bad request", 400)
                }
            }
        }
        assertEquals(1, attempts)
        assertEquals(400, ex.httpStatus)
    }
}
