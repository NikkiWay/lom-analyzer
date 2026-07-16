/*
 * НАЗНАЧЕНИЕ
 * Политика повторов при flood control VK — ошибке API с кодом 9. VK возвращает
 * её, когда запросы идут слишком часто: запрос корректен и подлежит повтору
 * после паузы.
 *
 * ЧТО ВНУТРИ
 * class VkFloodControlPolicy — счётчик попыток, метод awaitRetry() (выжидает
 * паузу и сообщает, повторять ли запрос) и reset() (сброс после успеха).
 *
 * МЕТОД
 * Нарастающие паузы 10с → 30с → 60с, не более трёх повторов подряд. Экземпляр
 * хранит состояние и создаётся на каждую независимую единицу сбора — автора,
 * пост, сегмент, — чтобы попытки не перетекали между ними.
 *
 * Класс отвечает на один вопрос: повторять запрос или прекратить. Что делать
 * при исчерпании повторов, решает вызывающий, и решения разные:
 * AuthorWallCollector сигнализирует блокировку наверх (floodBlocked) и
 * прерывает фазу C, CommentCollector и NewsfeedSearchCollector прекращают сбор
 * текущей единицы.
 *
 * ОТЛИЧИЕ ОТ VkBackoff
 * VkBackoff повторяет транспортные сбои (HTTP 429/5xx) вокруг произвольного
 * запроса. Здесь — прикладная ошибка VK внутри успешного HTTP-ответа, и
 * повторяется конкретно та же страница пагинации, без сдвига offset.
 *
 * БИБЛИОТЕКИ
 * kotlinx.coroutines.delay — неблокирующая пауза.
 *
 * СВЯЗИ
 * Используется коллекторами пакета vk/. Журналирует через Logger
 * (observability/); фиксацию события в БД делает вызывающий, у которого есть
 * sessionId и имя метода VK.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.delay

/**
 * Политика повторов при flood control VK (ошибка 9).
 *
 * @param logger журнал для диагностики повторов.
 * @param methodName имя метода VK ("wall.get", "newsfeed.search", ...) — попадает в лог.
 * @param maxRetries максимум повторов; по умолчанию [MAX_RETRIES].
 * @param retryDelaysMs нарастающие паузы; по умолчанию [RETRY_DELAYS_MS].
 * @param sleep функция ожидания; подменяется в тестах, чтобы не ждать реальные секунды.
 */
class VkFloodControlPolicy(
    private val logger: Logger,
    private val methodName: String,
    private val maxRetries: Int = MAX_RETRIES,
    private val retryDelaysMs: LongArray = RETRY_DELAYS_MS,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    companion object {
        /** Код ошибки VK, означающий flood control. */
        const val FLOOD_CONTROL_ERROR_CODE = 9

        /** Максимум повторов одной и той же страницы при flood control. */
        const val MAX_RETRIES = 3

        /** Нарастающие задержки повторов: 10с, 30с, 60с. */
        val RETRY_DELAYS_MS = longArrayOf(10_000L, 30_000L, 60_000L)
    }

    /** Сколько повторов уже израсходовано. */
    var attemptsUsed: Int = 0
        private set

    /**
     * Сбрасывает счётчик после успешно полученной страницы.
     *
     * Бюджет в три повтора относится к серии подряд идущих ошибок 9. Успешная
     * страница означает, что серия прервалась, и следующая начинается заново.
     */
    fun reset() {
        attemptsUsed = 0
    }

    /**
     * Выжидает очередную паузу и сообщает, можно ли повторить запрос.
     *
     * @param context человекочитаемое уточнение для лога (например, «author 123 (BASELINE)»).
     * @return true — пауза выдержана, повторяйте ту же страницу; false — повторы
     *         исчерпаны подряд, вызывающий решает, что делать дальше.
     */
    suspend fun awaitRetry(context: String): Boolean {
        attemptsUsed++
        if (attemptsUsed > maxRetries) return false

        // Задержку берём по номеру попытки; если пауз объявлено меньше, чем
        // повторов, держимся последней вместо выхода за границы массива.
        val delayMs = retryDelaysMs[(attemptsUsed - 1).coerceAtMost(retryDelaysMs.lastIndex)]
        logger.warn(
            "VK flood control on $methodName ($context), " +
                "retry $attemptsUsed/$maxRetries, waiting ${delayMs / 1000}s",
        )
        sleep(delayMs)
        return true
    }
}
