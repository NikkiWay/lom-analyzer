/*
 * НАЗНАЧЕНИЕ
 * Тесты VkBackoff — стратегии повторов с экспоненциальной задержкой для вызовов
 * VK API (этап 2 — сбор данных). Проверяют ретраи на временных ошибках (429, 500),
 * отсутствие лишних задержек при успехе, ограничение числа попыток и отказ от
 * ретраев на клиентских ошибках (4xx, кроме 429).
 *
 * ЧТО ВНУТРИ
 * Класс VkBackoffTest:
 *  - успех с первой попытки без задержки;
 *  - ретрай на 429 (rate limit) и успешное восстановление;
 *  - ретрай на 500 (ошибка сервера) и успех;
 *  - бросок исключения после исчерпания maxRetries (1 первичная + N ретраев);
 *  - отсутствие ретраев на 400 (клиентская ошибка), сохранение httpStatus.
 *
 * МЕТОД
 * Повторяемые статусы — 429 и 5xx (временные); неповторяемые — прочие 4xx.
 * maxDelaySeconds=1 в тестах сокращает паузы, не меняя логику ретраев.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (assert*, assertThrows). kotlinx.coroutines.runBlocking — suspend-вызовы.
 *
 * СВЯЗИ
 * VkBackoff, VkApiException (поле httpStatus).
 */
package com.example.lomanalyzer.vk

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Тесты стратегии повторов VkBackoff для вызовов VK API. */
class VkBackoffTest {

    /** При успехе с первой попытки блок выполняется один раз, без задержек. */
    @Test
    fun `succeeds on first attempt without delay`() = runBlocking {
        val backoff = VkBackoff(maxRetries = 5, maxDelaySeconds = 1)
        var attempts = 0
        // Блок успешен сразу
        val result = backoff.withRetry {
            attempts++
            "ok"
        }
        assertEquals("ok", result)
        // Ровно одна попытка
        assertEquals(1, attempts)
    }

    /**
     * Ретрай на 429 (rate limit): первые две попытки бросают исключение, третья
     * успешна. Assert: вернулось "recovered" за 3 попытки.
     */
    @Test
    fun `retries on 429 and succeeds`() = runBlocking {
        val backoff = VkBackoff(maxRetries = 5, maxDelaySeconds = 1)
        var attempts = 0
        val result = backoff.withRetry {
            attempts++
            // Первые две попытки имитируют rate limit
            if (attempts < 3) {
                throw VkApiException("Rate limited", 429)
            }
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, attempts)
    }

    /** Ретрай на 500 (ошибка сервера): успех со второй попытки. */
    @Test
    fun `retries on 500 and succeeds`() = runBlocking {
        val backoff = VkBackoff(maxRetries = 5, maxDelaySeconds = 1)
        var attempts = 0
        val result = backoff.withRetry {
            attempts++
            // Первая попытка — серверная ошибка
            if (attempts < 2) {
                throw VkApiException("Server error", 500)
            }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    /**
     * Если ошибка повторяется всегда, после исчерпания попыток (1 первичная + 3
     * ретрая = 4) бросается VkApiException с сообщением о превышении лимита ретраев.
     */
    @Test
    fun `throws after max retries exceeded`() = runBlocking {
        val backoff = VkBackoff(maxRetries = 3, maxDelaySeconds = 1)
        var attempts = 0
        val ex = assertThrows(VkApiException::class.java) {
            runBlocking {
                backoff.withRetry<String> {
                    attempts++
                    // Всегда падаем с 429
                    throw VkApiException("Always fails", 429)
                }
            }
        }
        assertEquals(4, attempts) // 1 initial + 3 retries
        // Сообщение указывает на исчерпание ретраев
        assertTrue(ex.message!!.contains("Max retries"))
    }

    /**
     * Клиентская ошибка 400 не повторяется: блок выполняется один раз, исключение
     * пробрасывается сразу с сохранённым httpStatus=400 (ретраи бессмысленны).
     */
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
        // Без ретраев — единственная попытка
        assertEquals(1, attempts)
        assertEquals(400, ex.httpStatus)
    }
}
