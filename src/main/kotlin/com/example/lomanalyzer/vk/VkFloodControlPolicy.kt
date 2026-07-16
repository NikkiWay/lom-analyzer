/*
 * НАЗНАЧЕНИЕ
 * Единая политика повторов при flood control VK (ошибка API с кодом 9). VK
 * возвращает её, когда запросы идут слишком часто: сам запрос корректен, его
 * нужно просто повторить позже. Раньше эта логика была скопирована в трёх
 * коллекторах (AuthorWallCollector, CommentCollector, NewsfeedSearchCollector)
 * вместе с константами, поэтому менять задержки приходилось в трёх местах.
 *
 * ЧТО ВНУТРИ
 * class VkFloodControlPolicy — счётчик попыток и метод awaitRetry(), который
 * выжидает нужную паузу и сообщает, стоит ли повторять запрос.
 *
 * МЕТОД
 * Нарастающие паузы 10с → 30с → 60с, не более трёх повторов. Экземпляр хранит
 * состояние, поэтому создаётся на ту же область, где раньше жил счётчик
 * floodRetries: свой на каждого автора / пост / сегмент, чтобы попытки не
 * перетекали между независимыми единицами сбора.
 *
 * Класс намеренно НЕ управляет потоком выполнения: он лишь отвечает «повторять
 * или сдаться». Реакция на исчерпание повторов у вызывающих разная и
 * содержательная — AuthorWallCollector сигнализирует блокировку наверх
 * (floodBlocked), а CommentCollector и NewsfeedSearchCollector прекращают сбор
 * текущей единицы, — поэтому она остаётся на стороне вызывающего.
 *
 * ОТЛИЧИЕ ОТ VkBackoff
 * VkBackoff повторяет транспортные сбои (HTTP 429/5xx) вокруг любого запроса.
 * Здесь — прикладная ошибка VK внутри успешного HTTP-ответа, и повторяется не
 * произвольный вызов, а та же страница пагинации без сдвига offset.
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
     * Бюджет повторов считается на ПОДРЯД идущую серию flood control, а не на
     * весь сбор: длинная выгрузка, изредка натыкающаяся на ошибку 9 и каждый раз
     * из неё выходящая, не должна исчерпать бюджет и оборваться.
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
