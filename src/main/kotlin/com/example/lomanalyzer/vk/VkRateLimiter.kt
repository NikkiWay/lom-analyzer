/*
 * НАЗНАЧЕНИЕ
 * Ограничитель частоты запросов к VK API (rate limit). VK разрешает лишь несколько
 * вызовов в секунду; превышение приводит к ошибкам. Лимитер вызывается перед каждым
 * запросом в VkApiClient и при необходимости приостанавливает корутину, чтобы не
 * выйти за допустимый темп.
 *
 * ЧТО ВНУТРИ
 * Класс VkRateLimiter с методом acquire(). Реализует скользящее окно в 1 секунду
 * по меткам времени последних запросов.
 *
 * АЛГОРИТМ
 * Sliding-window: хранятся метки времени запросов за последнюю секунду; если их число
 * достигло лимита, корутина ждёт, пока самый старый запрос не выйдет из окна.
 *
 * ФРЕЙМВОРКИ
 * kotlinx.coroutines: Mutex с withLock — потокобезопасный доступ к окну меток из разных
 * корутин; delay — неблокирующее ожидание. Logger пишет событие RATE_LIMIT_HIT.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ограничитель частоты обращений к VK API методом скользящего окна.
 *
 * @param maxRequestsPerSecond максимум запросов в секунду (по умолчанию 2 — консервативно).
 * @param logger опциональный логгер для фиксации факта ожидания (RATE_LIMIT_HIT).
 */
class VkRateLimiter(
    private val maxRequestsPerSecond: Int = 2,
    private val logger: Logger? = null,
) {
    /** Мьютекс для потокобезопасного доступа к окну меток времени из разных корутин. */
    private val mutex = Mutex()

    /** Метки времени (мс) запросов в пределах текущего секундного окна. */
    private val timestamps = ArrayDeque<Long>(maxRequestsPerSecond)

    /**
     * Регистрирует намерение выполнить запрос: при необходимости ждёт, пока темп
     * не опустится до допустимого, затем записывает текущую метку времени.
     */
    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            // Удаляем метки старше 1 секунды — они вышли из скользящего окна
            while (timestamps.isNotEmpty() && now - timestamps.first() >= 1000) {
                timestamps.removeFirst()
            }
            // Если в окне уже достигнут лимит — нужно подождать освобождения слота
            if (timestamps.size >= maxRequestsPerSecond) {
                // Момент, когда самый старый запрос выйдет из секундного окна
                val waitUntil = timestamps.first() + 1000
                val waitMs = waitUntil - now
                if (waitMs > 0) {
                    logger?.event(AppEvent.RATE_LIMIT_HIT, mapOf("wait_ms" to waitMs))
                    delay(waitMs) // неблокирующая пауза до освобождения слота
                }
                // После ожидания повторно чистим окно от устаревших меток
                val nowAfter = System.currentTimeMillis()
                while (timestamps.isNotEmpty() && nowAfter - timestamps.first() >= 1000) {
                    timestamps.removeFirst()
                }
            }
            // Фиксируем текущий запрос в окне
            timestamps.addLast(System.currentTimeMillis())
        }
    }
}
