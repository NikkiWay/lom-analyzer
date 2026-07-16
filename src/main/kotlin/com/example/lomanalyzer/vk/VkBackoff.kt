/*
 * НАЗНАЧЕНИЕ
 * Повтор сетевых вызовов VK API при временных сбоях — экспоненциальный backoff
 * с джиттером. Оборачивает каждый запрос в VkApiClient: при HTTP 429 (Too Many
 * Requests) и 5xx (серверные ошибки) повторяет вызов с растущей паузой, а на
 * клиентских ошибках (кроме 429) пробрасывает исключение сразу.
 *
 * ЧТО ВНУТРИ
 * Исключение VkApiException (несёт HTTP-статус) и класс VkBackoff с методом
 * withRetry, оборачивающим произвольный suspend-блок.
 *
 * АЛГОРИТМ
 * Экспоненциальная задержка base = min(2^attempt, maxDelaySeconds) секунд + случайный
 * джиттер до 1 с (чтобы развести повторные запросы во времени). До maxRetries попыток,
 * затем — VkApiException.
 *
 * ФРЕЙМВОРКИ
 * Ktor (ResponseException — источник HTTP-статуса), kotlinx.coroutines.delay —
 * неблокирующая пауза. Событие BACKOFF_APPLIED пишется в Logger.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import io.ktor.client.plugins.*
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

/**
 * Исключение, сигнализирующее об окончательной ошибке вызова VK API.
 * @param httpStatus HTTP-статус, если он известен (для классификации повторяемости).
 */
class VkApiException(message: String, val httpStatus: Int? = null) : RuntimeException(message)

/**
 * Стратегия повторов запросов с экспоненциальным backoff и джиттером.
 *
 * @param maxRetries максимальное число повторов после первой неудачной попытки.
 * @param maxDelaySeconds верхняя граница базовой задержки в секундах.
 * @param logger опциональный логгер для события BACKOFF_APPLIED.
 */
class VkBackoff(
    private val maxRetries: Int = 5,
    private val maxDelaySeconds: Long = 30,
    private val logger: Logger? = null,
) {
    /**
     * Выполняет [block], повторяя его при временных ошибках (HTTP 429 и 5xx).
     * Невосстановимые ошибки пробрасываются немедленно; при исчерпании попыток
     * выбрасывается VkApiException.
     * @return результат успешного выполнения блока.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (@Suppress("SwallowedException") e: Exception) {
                val status = extractHttpStatus(e)
                // Клиентские ошибки (кроме 429) считаем неповторяемыми — пробрасываем сразу
                if (status != null && status != 429 && status !in 500..599) {
                    throw e
                }
                attempt++
                // Исчерпали лимит повторов — окончательная ошибка
                if (attempt > maxRetries) {
                    throw VkApiException(
                        "Max retries ($maxRetries) exceeded: ${e.message}",
                        status,
                    )
                }
                // Экспоненциальная задержка 2^attempt с, но не больше maxDelaySeconds
                val baseDelay = min(1L shl attempt, maxDelaySeconds)
                // Случайный джиттер до 1 с — чтобы повторы не синхронизировались
                val jitter = (Random.nextDouble() * 1000).toLong()
                val totalMs = baseDelay * 1000 + jitter
                logger?.event(AppEvent.BACKOFF_APPLIED, mapOf(
                    "attempt" to attempt,
                    "delay_ms" to totalMs,
                    "status" to (status ?: "unknown"),
                ))
                delay(totalMs) // ждём перед следующей попыткой
            }
        }
    }

    /**
     * Извлекает HTTP-статус из исключения: из Ktor ResponseException или из VkApiException.
     * @return код статуса либо null, если статус неизвестен (например, сетевой сбой).
     */
    private fun extractHttpStatus(e: Exception): Int? = when (e) {
        is ResponseException -> e.response.status.value
        is VkApiException -> e.httpStatus
        else -> null
    }
}
