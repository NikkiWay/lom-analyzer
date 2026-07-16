/*
 * НАЗНАЧЕНИЕ
 * Сбор тематических публикаций по ключевым словам через newsfeed.search (этап 2
 * алгоритма, режим без заданных сообществ — поиск по всему VK). Найденные посты
 * сохраняются в окно CURRENT.
 *
 * ЧТО ВНУТРИ
 * Класс NewsfeedSearchCollector: collect (обход всего периода по сегментам),
 * collectSegment (пагинация и обработка ошибок внутри одного диапазона дат),
 * persistPost (сохранение в БД) и buildSegments (нарезка периода на окна).
 *
 * МЕТОД / ОГРАНИЧЕНИЯ VK
 * newsfeed.search по умолчанию отдаёт лишь сутки (нужны start_time/end_time), не более
 * 1000 результатов на один диапазон и до 200 на страницу; пагинация — непрозрачным
 * курсором start_from/next_from. Стратегия: период делится на недельные сегменты; если
 * сегмент упирается в лимит 1000, он дробится на дневные окна, чтобы не терять посты в
 * периоды высокой активности. Между страницами и сегментами выдерживаются паузы, а на
 * ошибку 9 (flood control) применяется повтор с нарастающими задержками.
 *
 * ФРЕЙМВОРКИ / СВЯЗИ
 * kotlinx.coroutines.delay — паузы; java.time — работа с датами/Unix-временем.
 * VkApiClient — вызовы API; PostDao — запись постов; SessionEventService и
 * ProgressReporter — журнал событий и индикатор прогресса; Logger — события приложения.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.vk.models.VkPost
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Collects topic posts via VK newsfeed.search when no communities are specified.
 *
 * Key constraints of newsfeed.search (VK API):
 * - Default returns only last 24h → MUST set start_time/end_time
 * - Max 1000 results per query (even with pagination)
 * - Pagination via opaque cursor (start_from / next_from)
 * - Max 200 per page
 *
 * Strategy: segment the time range into weekly windows.
 * If a window yields 1000 results (cap hit), subdivide into daily windows.
 * This ensures we don't miss posts in high-volume periods.
 */
class NewsfeedSearchCollector(
    private val apiClient: VkApiClient,
    private val postDao: PostDao,
    private val sessionEventService: SessionEventService,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
    companion object {
        /** Верхний предел собираемых постов за всю сессию. */
        private const val MAX_TOTAL_POSTS = 100

        /** Лимит VK на число результатов в одном диапазоне дат (1000). */
        private const val MAX_PER_QUERY = 1000

        /** Максимум постов на страницу newsfeed.search (200). */
        private const val PAGE_SIZE = 200

        /** Начальный размер сегмента периода — неделя. */
        private const val SEGMENT_DAYS_INITIAL = 7

        /** Размер мелкого сегмента при дроблении — сутки. */
        private const val SEGMENT_DAYS_FINE = 1

        /** Пауза между страницами одного сегмента (мс). */
        private const val DELAY_BETWEEN_PAGES_MS = 350L

        /** Пауза между сегментами (мс). */
        private const val DELAY_BETWEEN_SEGMENTS_MS = 500L

    }

    /**
     * Search for topic posts across all of VK by keywords.
     * @param query topic query string (keywords)
     * @param periodDays how many days back to search
     * @return total posts collected
     */
    suspend fun collect(
        sessionId: Int,
        query: String,
        periodDays: Int,
        accessToken: String,
    ): Int {
        val now = Instant.now()
        // Начало периода поиска: now минус заданное число дней
        val periodStart = now.minus(periodDays.toLong(), ChronoUnit.DAYS)
        var totalPosts = 0

        // Нарезаем период на недельные сегменты
        val segments = buildSegments(periodStart, now, SEGMENT_DAYS_INITIAL)
        val totalSegments = segments.size

        sessionEventService.logInfo(sessionId,
            "newsfeed.search: запрос='$query', период=${periodDays}д, сегментов=$totalSegments")

        for ((segIdx, segment) in segments.withIndex()) {
            if (totalPosts >= MAX_TOTAL_POSTS) break // достигнут общий лимит — выходим

            // Собираем посты внутри одного недельного сегмента
            val collected = collectSegment(sessionId, query, segment.first, segment.second, accessToken)

            // Если упёрлись в лимит 1000 и сегмент шире двух суток — дробим на дневные окна
            if (collected >= MAX_PER_QUERY && segment.second.epochSecond - segment.first.epochSecond > 86400 * 2) {
                sessionEventService.logInfo(sessionId,
                    "Сегмент ${segIdx + 1}: лимит 1000 достигнут, разбиваю на дни")
                val fineSegments = buildSegments(segment.first, segment.second, SEGMENT_DAYS_FINE)
                for (fine in fineSegments) {
                    if (totalPosts >= MAX_TOTAL_POSTS) break
                    val fineCollected = collectSegment(sessionId, query, fine.first, fine.second, accessToken)
                    totalPosts += fineCollected
                    delay(DELAY_BETWEEN_SEGMENTS_MS) // пауза между дневными окнами
                }
            } else {
                totalPosts += collected
            }

            progressReporter.update(ProgressEvent(
                stage = "Поиск публикаций: сегмент ${segIdx + 1}/$totalSegments, найдено $totalPosts",
                completedItems = segIdx + 1,
                totalItems = totalSegments,
            ))

            delay(DELAY_BETWEEN_SEGMENTS_MS) // пауза между недельными сегментами
        }

        if (totalPosts >= MAX_TOTAL_POSTS) {
            sessionEventService.logInfo(sessionId,
                "Достигнут лимит $MAX_TOTAL_POSTS публикаций, сбор остановлен")
        }

        sessionEventService.logInfo(sessionId, "newsfeed.search завершён: $totalPosts публикаций")
        logger.event(AppEvent.COLLECTION_COMPLETED, mapOf(
            "session_id" to sessionId, "phase" to "NEWSFEED_SEARCH", "total_posts" to totalPosts,
        ))

        return totalPosts
    }

    /**
     * Collect posts within a single time segment, paginating through start_from.
     */
    private suspend fun collectSegment(
        sessionId: Int,
        query: String,
        segStart: Instant,
        segEnd: Instant,
        accessToken: String,
    ): Int {
        var collected = 0
        var startFrom: String? = null // курсор пагинации (next_from), null для первой страницы
        // Свои повторы на каждый сегмент: попытки не переносятся между сегментами
        val floodControl = VkFloodControlPolicy(logger, "newsfeed.search")

        while (collected < MAX_PER_QUERY) {
            // Запрос страницы постов за диапазон [segStart, segEnd] с курсором startFrom
            val response = apiClient.newsfeedSearch(
                query = query,
                count = PAGE_SIZE,
                startFrom = startFrom,
                startTime = segStart.epochSecond,
                endTime = segEnd.epochSecond,
                accessToken = accessToken,
            )

            if (response.error != null) {
                // Ошибка 9 — flood control: ждём по нарастающей и повторяем эту же страницу
                if (response.error.errorCode == VkFloodControlPolicy.FLOOD_CONTROL_ERROR_CODE) {
                    if (!floodControl.awaitRetry("segment $segStart..$segEnd")) {
                        sessionEventService.logApiError(sessionId, "newsfeed.search",
                            VkFloodControlPolicy.FLOOD_CONTROL_ERROR_CODE,
                            "Flood control: max retries exceeded")
                        break
                    }
                    continue // повтор без смены курсора
                }
                // Прочие ошибки VK — фиксируем и прекращаем сбор сегмента
                sessionEventService.logApiError(sessionId, "newsfeed.search",
                    response.error.errorCode, response.error.errorMsg)
                break
            }
            floodControl.reset() // успешная страница сбрасывает счётчик повторов

            val items = response.response?.items ?: break
            if (items.isEmpty()) break // пустая страница — конец сегмента

            for (post in items) {
                if (post.id == 0 && post.ownerId == 0) continue // пропуск «пустышек»
                persistPost(sessionId, post)
                collected++
            }

            // Берём курсор следующей страницы; если его нет — страниц больше нет
            startFrom = response.response?.nextFrom
            if (startFrom == null) break

            delay(DELAY_BETWEEN_PAGES_MS) // пауза между страницами
        }

        return collected
    }

    /** Сохраняет один пост newsfeed.search в окно CURRENT (метаданные, счётчики, признаки медиа/репоста). */
    private fun persistPost(sessionId: Int, post: VkPost) {
        postDao.insertVkPost(sessionId, post, "CURRENT")
    }

    /**
     * Делит интервал [start, end] на последовательные окна по segmentDays суток.
     * Последнее окно обрезается границей end.
     * @return список пар (начало, конец) каждого сегмента.
     */
    private fun buildSegments(start: Instant, end: Instant, segmentDays: Int): List<Pair<Instant, Instant>> {
        val segments = mutableListOf<Pair<Instant, Instant>>()
        var segStart = start
        while (segStart.isBefore(end)) {
            // Конец сегмента = начало + segmentDays, но не дальше общей границы end
            val segEnd = segStart.plus(segmentDays.toLong(), ChronoUnit.DAYS).let {
                if (it.isAfter(end)) end else it
            }
            segments.add(segStart to segEnd)
            segStart = segEnd // следующий сегмент начинается с конца текущего
        }
        return segments
    }
}
