package com.example.lomanalyzer.vk

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VkRateLimiterTest {

    @Test
    fun `allows up to maxRequestsPerSecond without delay`() = runBlocking {
        val limiter = VkRateLimiter(maxRequestsPerSecond = 3)
        val start = System.currentTimeMillis()
        repeat(3) { limiter.acquire() }
        val elapsed = System.currentTimeMillis() - start
        assertTrue(elapsed < 500, "3 requests should complete quickly, took ${elapsed}ms")
    }

    @Test
    fun `4th request is delayed past the 1-second window`() = runBlocking {
        val limiter = VkRateLimiter(maxRequestsPerSecond = 3)
        val start = System.currentTimeMillis()
        repeat(4) { limiter.acquire() }
        val elapsed = System.currentTimeMillis() - start
        assertTrue(elapsed >= 900, "4th request should wait ~1s, took ${elapsed}ms")
    }

    @Test
    fun `concurrent requests are serialized correctly`() = runBlocking {
        val limiter = VkRateLimiter(maxRequestsPerSecond = 3)
        val jobs = (1..6).map {
            async { limiter.acquire() }
        }
        val start = System.currentTimeMillis()
        jobs.awaitAll()
        val elapsed = System.currentTimeMillis() - start
        // 6 requests at 3/s should take at least ~1s
        assertTrue(elapsed >= 900, "6 requests at 3/s should take >=1s, took ${elapsed}ms")
    }
}
