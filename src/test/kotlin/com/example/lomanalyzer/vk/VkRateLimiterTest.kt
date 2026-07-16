/*
 * НАЗНАЧЕНИЕ
 * Тесты VkRateLimiter — ограничителя частоты обращений к VK API (не более N
 * запросов в секунду, по умолчанию 3 — лимит VK). Защищает от flood control и
 * блокировок при сборе данных (этап 2).
 *
 * ЧТО ВНУТРИ
 * Класс VkRateLimiterTest:
 *  - первые maxRequestsPerSecond запросов проходят без заметной задержки;
 *  - (N+1)-й запрос ожидает до конца односекундного окна;
 *  - конкурентные запросы корректно сериализуются (6 запросов при 3/с — >=1с).
 *
 * МЕТОД
 * Скользящее окно в 1 секунду: при достижении лимита acquire() приостанавливает
 * корутину до освобождения квоты. Проверка по фактически измеренному времени.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (assert*). kotlinx.coroutines (runBlocking, async, awaitAll) —
 * параллельные suspend-вызовы acquire(). System.currentTimeMillis — замер времени.
 *
 * СВЯЗИ
 * VkRateLimiter.
 */
package com.example.lomanalyzer.vk

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Тесты ограничителя частоты запросов к VK API. */
class VkRateLimiterTest {

    /**
     * В пределах квоты (3 запроса при лимите 3/с) acquire() не вносит задержек:
     * три вызова завершаются быстро (< 500 мс).
     */
    @Test
    fun `allows up to maxRequestsPerSecond without delay`() = runBlocking {
        val limiter = VkRateLimiter(maxRequestsPerSecond = 3)
        val start = System.currentTimeMillis()
        // Три запроса укладываются в квоту окна
        repeat(3) { limiter.acquire() }
        val elapsed = System.currentTimeMillis() - start
        assertTrue(elapsed < 500, "3 requests should complete quickly, took ${elapsed}ms")
    }

    /**
     * 4-й запрос превышает квоту окна и должен ждать ~1 с до его обновления.
     * Assert: суммарное время не меньше ~900 мс.
     */
    @Test
    fun `4th request is delayed past the 1-second window`() = runBlocking {
        val limiter = VkRateLimiter(maxRequestsPerSecond = 3)
        val start = System.currentTimeMillis()
        // 4-й вызов вынужден дождаться следующего окна
        repeat(4) { limiter.acquire() }
        val elapsed = System.currentTimeMillis() - start
        assertTrue(elapsed >= 900, "4th request should wait ~1s, took ${elapsed}ms")
    }

    /**
     * Параллельные запросы корректно сериализуются ограничителем: 6 одновременных
     * acquire() при лимите 3/с занимают не менее ~1 с (потокобезопасность квоты).
     */
    @Test
    fun `concurrent requests are serialized correctly`() = runBlocking {
        val limiter = VkRateLimiter(maxRequestsPerSecond = 3)
        // Запускаем 6 параллельных захватов квоты
        val jobs = (1..6).map {
            async { limiter.acquire() }
        }
        val start = System.currentTimeMillis()
        jobs.awaitAll()
        val elapsed = System.currentTimeMillis() - start
        // 6 requests at 3/s should take at least ~1s
        // 6 запросов при 3/с требуют как минимум ~1 секунды
        assertTrue(elapsed >= 900, "6 requests at 3/s should take >=1s, took ${elapsed}ms")
    }
}
