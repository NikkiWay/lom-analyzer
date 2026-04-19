package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VkRateLimiter(
    private val maxRequestsPerSecond: Int = 3,
    private val logger: Logger? = null,
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>(maxRequestsPerSecond)

    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            // Remove timestamps older than 1 second
            while (timestamps.isNotEmpty() && now - timestamps.first() >= 1000) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= maxRequestsPerSecond) {
                val waitUntil = timestamps.first() + 1000
                val waitMs = waitUntil - now
                if (waitMs > 0) {
                    logger?.event(AppEvent.RATE_LIMIT_HIT, mapOf("wait_ms" to waitMs))
                    delay(waitMs)
                }
                // Re-clean after waiting
                val nowAfter = System.currentTimeMillis()
                while (timestamps.isNotEmpty() && nowAfter - timestamps.first() >= 1000) {
                    timestamps.removeFirst()
                }
            }
            timestamps.addLast(System.currentTimeMillis())
        }
    }
}
