/*
 * НАЗНАЧЕНИЕ
 * Тесты единой политики повторов при flood control VK (ошибка 9). Политика
 * общая для всех коллекторов; паузы задаются таблицей, поэтому проверяются без
 * реального ожидания.
 *
 * ЧТО ВНУТРИ
 * Класс VkFloodControlPolicyTest: проверки числа разрешённых повторов, точной
 * последовательности пауз, отсутствия ожидания после исчерпания повторов и
 * независимости счётчиков у разных экземпляров.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5; kotlinx.coroutines.runBlocking. Функция ожидания подменена на запись
 * в список — тесты проверяют выдержанные паузы, не тратя времени.
 *
 * СВЯЗИ
 * VkFloodControlPolicy (vk/), Logger (observability/).
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VkFloodControlPolicyTest {
    private val logger = Logger("flood-test")

    /** Собирает выдержанные паузы вместо реального ожидания. */
    private class RecordingSleeper {
        val slept = mutableListOf<Long>()
        suspend fun sleep(ms: Long) {
            slept.add(ms)
        }
    }

    private fun policy(sleeper: RecordingSleeper) = VkFloodControlPolicy(
        logger = logger,
        methodName = "wall.get",
        sleep = sleeper::sleep,
    )

    /** Разрешено ровно три повтора — четвёртый обращение отклоняет. */
    @Test
    fun `allows exactly three retries then gives up`() = runBlocking {
        val sleeper = RecordingSleeper()
        val floodPolicy = policy(sleeper)

        assertTrue(floodPolicy.awaitRetry("author 1"), "1st retry allowed")
        assertTrue(floodPolicy.awaitRetry("author 1"), "2nd retry allowed")
        assertTrue(floodPolicy.awaitRetry("author 1"), "3rd retry allowed")
        assertFalse(floodPolicy.awaitRetry("author 1"), "4th must be refused")
    }

    /** Паузы нарастают в задокументированном порядке: 10с, 30с, 60с. */
    @Test
    fun `waits 10s then 30s then 60s`() = runBlocking {
        val sleeper = RecordingSleeper()
        val floodPolicy = policy(sleeper)

        repeat(3) { floodPolicy.awaitRetry("author 1") }

        assertEquals(listOf(10_000L, 30_000L, 60_000L), sleeper.slept)
    }

    /** После исчерпания повторов ожидания не происходит — вызывающий сдаётся сразу. */
    @Test
    fun `does not sleep once retries are exhausted`() = runBlocking {
        val sleeper = RecordingSleeper()
        val floodPolicy = policy(sleeper)

        repeat(3) { floodPolicy.awaitRetry("author 1") }
        val before = sleeper.slept.size
        floodPolicy.awaitRetry("author 1")

        assertEquals(before, sleeper.slept.size, "Refused retry must not wait")
    }

    /**
     * Счётчики экземпляров независимы: у каждого автора/поста/сегмента свой набор
     * повторов, и исчерпание повторов на одном не должно мешать следующему.
     */
    @Test
    fun `separate instances keep independent counters`() = runBlocking {
        val firstSleeper = RecordingSleeper()
        val secondSleeper = RecordingSleeper()
        val firstAuthor = policy(firstSleeper)
        val secondAuthor = policy(secondSleeper)

        repeat(4) { firstAuthor.awaitRetry("author 1") }
        assertFalse(firstAuthor.awaitRetry("author 1"), "first is exhausted")

        assertTrue(secondAuthor.awaitRetry("author 2"), "second author starts fresh")
    }

    /**
     * Успешная страница сбрасывает счётчик: бюджет считается на ПОДРЯД идущую
     * серию flood control. Длинная выгрузка, изредка натыкающаяся на ошибку 9 и
     * каждый раз из неё выходящая, не должна исчерпать бюджет и оборваться.
     */
    @Test
    fun `reset restores the full retry budget after a successful page`() = runBlocking {
        val sleeper = RecordingSleeper()
        val floodPolicy = policy(sleeper)

        repeat(3) { floodPolicy.awaitRetry("author 1") }
        assertFalse(floodPolicy.awaitRetry("author 1"), "budget exhausted before reset")

        floodPolicy.reset()

        assertEquals(0, floodPolicy.attemptsUsed)
        assertTrue(floodPolicy.awaitRetry("author 1"), "budget restored after a successful page")
        assertEquals(10_000L, sleeper.slept.last(), "delays restart from the first step")
    }

    /** Число израсходованных повторов доступно для диагностики. */
    @Test
    fun `reports how many attempts were used`() = runBlocking {
        val sleeper = RecordingSleeper()
        val floodPolicy = policy(sleeper)

        assertEquals(0, floodPolicy.attemptsUsed)
        floodPolicy.awaitRetry("author 1")
        floodPolicy.awaitRetry("author 1")
        assertEquals(2, floodPolicy.attemptsUsed)
    }

    /**
     * Если пауз объявлено меньше, чем разрешено повторов, политика держится
     * последней паузы, а не падает с выходом за границы массива.
     */
    @Test
    fun `reuses the last delay when retries outnumber delays`() = runBlocking {
        val sleeper = RecordingSleeper()
        val floodPolicy = VkFloodControlPolicy(
            logger = logger,
            methodName = "wall.get",
            maxRetries = 4,
            retryDelaysMs = longArrayOf(1_000L, 2_000L),
            sleep = sleeper::sleep,
        )

        repeat(4) { floodPolicy.awaitRetry("author 1") }

        assertEquals(listOf(1_000L, 2_000L, 2_000L, 2_000L), sleeper.slept)
    }
}
