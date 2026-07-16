/*
 * НАЗНАЧЕНИЕ
 * Сбор постов с личных стен авторов из реестра сессии (этап 4 алгоритма, фаза C
 * трёхфазного сбора). Для каждого автора собираются два окна: BASELINE (фоновая
 * активность до тематического периода) и CURRENT (собственные посты автора в
 * тематическом периоде) через per-author wall.get.
 *
 * ЧТО ВНУТРИ
 * Класс AuthorWallCollector: collect (отбор топ-авторов, обход с чекпойнтами и
 * кулдауном), collectAuthorWall (пагинация стены одного автора и обработка ошибок),
 * data class CollectResult и WallResult.
 *
 * МЕТОД / ОБРАБОТКА ОШИБОК
 * Авторы ранжируются по числу тематических (CURRENT) постов и берётся топ-N. wall.get
 * листается по offset с фильтром по времени окна. Обрабатываются ошибки VK: 9 (flood
 * control) — повтор с нарастающими задержками, при исчерпании повторов фаза C прерывается;
 * 18 — удалённый/заблокированный аккаунт; 30 — закрытый профиль (помечается isClosed).
 * Чекпойнт каждые 10 авторов; между авторами выдерживается 2-минутный кулдаун с обратным
 * отсчётом в UI.
 *
 * ФРЕЙМВОРКИ / СВЯЗИ
 * kotlinx.coroutines.delay — паузы; java.time — окна по датам. DAO (PostDao, AuthorDao,
 * LinkDao) и таблицы Exposed — доступ к БД; VkApiClient — wall.get; CheckpointService —
 * контрольные точки; CooldownState/ProgressReporter — UI; SessionEventService/Logger — журнал.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.CooldownState
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.LinkDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.tables.Authors
import com.example.lomanalyzer.storage.tables.Posts
import com.example.lomanalyzer.storage.tables.SessionAuthors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Collects posts from personal walls of each author in the session registry.
 * Diploma 2.1.1, stage 4: for each author collect:
 * - Background posts (BASELINE window: 60 days before topic period)
 * - Topic-period posts (CURRENT window: author's own posts during the topic period)
 *
 * Handles:
 * - Closed/private profiles → skip with logging (error 30)
 * - Deactivated accounts → skip with logging
 * - Checkpoint every 10 authors (diploma 2.2.5)
 */
class AuthorWallCollector(
    private val apiClient: VkApiClient,
    private val postDao: PostDao,
    private val authorDao: AuthorDao,
    private val linkDao: LinkDao,
    private val sessionEventService: SessionEventService,
    private val checkpointService: CheckpointService,
    private val cooldownState: CooldownState,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
    companion object {
        /** Максимум фоновых (BASELINE) постов на автора. */
        private const val MAX_BASELINE_POSTS = 200

        /** Максимум тематических (CURRENT) постов автора. */
        private const val MAX_CURRENT_POSTS = 100

        /** Сколько авторов (топ по активности) обрабатывать. */
        private const val MAX_AUTHORS_FOR_WALL = 100

        /** Кулдаун между авторами в секундах (защита от flood control). */
        private const val DELAY_BETWEEN_AUTHORS_SEC = 120

        /** Частота сохранения чекпойнта — каждые N авторов. */
        private const val CHECKPOINT_EVERY = 10

    }

    /**
     * For each author in the session, collect wall posts in both windows.
     * @param baselineWindowDays days before topic period for background activity
     * @param currentWindowDays days of the topic period
     * @return [CollectResult] with total posts and flood abort flag
     */
    suspend fun collect(
        sessionId: Int,
        baselineWindowDays: Int,
        currentWindowDays: Int,
        accessToken: String,
    ): CollectResult {
        val allAuthors = linkDao.getAuthorsForSession(sessionId)
        if (allAuthors.isEmpty()) {
            logger.info("No authors in session #$sessionId, skipping wall collection")
            return CollectResult(0, floodAborted = false)
        }

        // Считаем число тематических (CURRENT) постов на каждого автора — мера активности
        val postCounts = postDao.findBySessionAndWindow(sessionId, "CURRENT")
            .filter { it[Posts.fromId] > 0 }
            .groupingBy { it[Posts.fromId] }
            .eachCount()

        // Ранжируем авторов по активности (самые активные первыми) и берём топ-N
        val sessionAuthors = allAuthors
            .sortedByDescending { sa ->
                val authorRow = authorDao.findById(sa[SessionAuthors.authorId].value)
                val vkId = authorRow?.get(Authors.vkId) ?: 0
                postCounts[vkId] ?: 0
            }
            .take(MAX_AUTHORS_FOR_WALL)

        val now = Instant.now()
        // Границы окон: CURRENT = [now - currentWindowDays, now], BASELINE — раньше, перед CURRENT
        val currentStart = now.minus(currentWindowDays.toLong(), ChronoUnit.DAYS)
        val baselineStart = currentStart.minus(baselineWindowDays.toLong(), ChronoUnit.DAYS)

        var totalPosts = 0
        var processedAuthors = 0
        var skippedClosed = 0
        var floodAborted = false
        val total = sessionAuthors.size

        if (allAuthors.size > MAX_AUTHORS_FOR_WALL) {
            sessionEventService.logInfo(sessionId,
                "Отбор топ-$MAX_AUTHORS_FOR_WALL авторов из ${allAuthors.size} по числу тематических постов")
        }
        sessionEventService.logInfo(sessionId,
            "Сбор стен авторов: $total авторов, baseline=${baselineWindowDays}д, current=${currentWindowDays}д")

        for (sa in sessionAuthors) {
            val authorDbId = sa[SessionAuthors.authorId].value
            val authorRow = authorDao.findById(authorDbId)
            if (authorRow == null) {
                processedAuthors++
                continue // нет записи об авторе — пропускаем
            }

            val vkId = authorRow[Authors.vkId]
            val isClosed = authorRow[Authors.isClosed]

            // Закрытые профили не запрашиваем (их посты недоступны)
            if (isClosed) {
                skippedClosed++
                processedAuthors++
                continue
            }

            // Окно BASELINE — фоновая активность автора до тематического периода
            val baselineResult = collectAuthorWall(
                sessionId = sessionId,
                authorVkId = vkId,
                sinceTimestamp = baselineStart.epochSecond,
                untilTimestamp = currentStart.epochSecond,
                window = "BASELINE",
                maxPosts = MAX_BASELINE_POSTS,
                accessToken = accessToken,
            )

            // Flood control уже на BASELINE → немедленно прерываем фазу C
            if (baselineResult.floodBlocked) {
                sessionEventService.logInfo(sessionId,
                    "VK flood control при обработке автора $vkId. " +
                        "Этап C прерван после $processedAuthors авторов.")
                logger.warn("Phase C aborted: flood on author $vkId after $processedAuthors authors")
                floodAborted = true
                break
            }

            // Окно CURRENT — собственные посты автора в тематическом периоде
            val currentResult = collectAuthorWall(
                sessionId = sessionId,
                authorVkId = vkId,
                sinceTimestamp = currentStart.epochSecond,
                untilTimestamp = now.epochSecond,
                window = "CURRENT",
                maxPosts = MAX_CURRENT_POSTS,
                accessToken = accessToken,
            )

            // Flood control на CURRENT → также прерываем фазу C
            if (currentResult.floodBlocked) {
                sessionEventService.logInfo(sessionId,
                    "VK flood control при обработке автора $vkId (CURRENT). " +
                        "Этап C прерван после $processedAuthors авторов.")
                logger.warn("Phase C aborted: flood on author $vkId (CURRENT) after $processedAuthors authors")
                floodAborted = true
                break
            }

            totalPosts += baselineResult.collected + currentResult.collected
            processedAuthors++

            // Обновление индикатора прогресса в UI
            progressReporter.update(ProgressEvent(
                stage = "Стены авторов: $processedAuthors/$total, $totalPosts постов",
                completedItems = processedAuthors,
                totalItems = total,
            ))

            // Чекпойнт каждые CHECKPOINT_EVERY авторов — для возобновления после прерывания
            if (processedAuthors % CHECKPOINT_EVERY == 0) {
                sessionEventService.logProgress(sessionId, "Стены авторов", processedAuthors, total)
                checkpointService.saveCheckpoint(sessionId, "PHASE_2",
                    "AUTHOR_WALLS_${processedAuthors}_OF_$total")
            }

            // 2-минутный кулдаун между авторами с обратным отсчётом в UI (защита от flood control)
            val remainingAuthors = total - processedAuthors
            if (remainingAuthors > 0) {
                cooldownState.startCountdown(
                    DELAY_BETWEEN_AUTHORS_SEC,
                    "Ожидание: следующий автор",
                    "Обработано $processedAuthors/$total, осталось $remainingAuthors",
                )
                // Тикаем по секунде, обновляя счётчик в UI
                for (sec in DELAY_BETWEEN_AUTHORS_SEC downTo 1) {
                    cooldownState.tick(sec)
                    delay(1_000L)
                }
                cooldownState.clear()
            }
        }

        if (skippedClosed > 0) {
            sessionEventService.logInfo(sessionId,
                "Пропущено закрытых профилей: $skippedClosed из $total")
        }

        sessionEventService.logInfo(sessionId,
            "Сбор стен завершён: $totalPosts постов от ${processedAuthors - skippedClosed} авторов" +
                if (floodAborted) " (прерван из-за flood control)" else "")

        logger.event(AppEvent.COLLECTION_COMPLETED, mapOf(
            "session_id" to sessionId,
            "phase" to "AUTHOR_WALLS",
            "total_posts" to totalPosts,
            "authors_processed" to processedAuthors,
            "authors_skipped_closed" to skippedClosed,
            "flood_aborted" to floodAborted,
        ))

        return CollectResult(totalPosts, floodAborted)
    }

    /** Итог сбора стен: всего собрано постов и был ли сбор прерван из-за flood control. */
    data class CollectResult(val totalPosts: Int, val floodAborted: Boolean)

    /** Итог сбора одного окна стены автора: число постов и флаг блокировки flood control. */
    private data class WallResult(val collected: Int, val floodBlocked: Boolean)

    /**
     * Collect posts from a single author's wall within a time range.
     * Handles error 30 (private profile) gracefully.
     * Returns [WallResult] with flood status so caller can skip remaining windows.
     */
    private suspend fun collectAuthorWall(
        sessionId: Int,
        authorVkId: Int,
        sinceTimestamp: Long,
        untilTimestamp: Long,
        window: String,
        maxPosts: Int,
        accessToken: String,
    ): WallResult {
        var offset = 0 // смещение пагинации
        var collected = 0
        // Свои повторы на каждое окно автора: попытки не переносятся между авторами
        val floodControl = VkFloodControlPolicy(logger, "wall.get")
        val pageSize = 100

        while (collected < maxPosts) {
            // Запрос страницы стены автора; сетевое исключение прерывает сбор этого окна
            val response = try {
                apiClient.wallGet(
                    ownerId = authorVkId,
                    offset = offset,
                    count = pageSize,
                    accessToken = accessToken,
                )
            } catch (e: Exception) {
                sessionEventService.logError(sessionId,
                    "wall.get failed for author $authorVkId: ${e.message}")
                break
            }

            // Обработка ошибок VK по коду
            if (response.error != null) {
                when (response.error.errorCode) {
                    // Flood control — ждём по нарастающей и повторяем ту же страницу
                    VkFloodControlPolicy.FLOOD_CONTROL_ERROR_CODE -> {
                        if (!floodControl.awaitRetry("author $authorVkId ($window)")) {
                            // Исчерпали повторы — сигнализируем блокировку наверх (прервёт фазу C)
                            sessionEventService.logApiError(sessionId, "wall.get",
                                VkFloodControlPolicy.FLOOD_CONTROL_ERROR_CODE,
                                "Flood control: max retries for author $authorVkId ($window)")
                            return WallResult(collected, floodBlocked = true)
                        }
                        continue // повтор без смены offset
                    }
                    18 -> {
                        // Аккаунт удалён или заблокирован — постов не будет
                        sessionEventService.logInfo(sessionId,
                            "Автор $authorVkId: аккаунт удалён или заблокирован")
                    }
                    30 -> {
                        // Закрытый профиль — фиксируем и помечаем автора isClosed в БД
                        sessionEventService.logClosedAccount(sessionId, authorVkId)
                        val authorRow = authorDao.findByVkId(authorVkId)
                        if (authorRow != null) {
                            authorDao.update(authorRow[Authors.id].value) {
                                this[Authors.isClosed] = true
                            }
                        }
                    }
                    else -> {
                        // Прочие ошибки VK — просто журналируем
                        sessionEventService.logApiError(sessionId, "wall.get",
                            response.error.errorCode, response.error.errorMsg)
                    }
                }
                break // на любой ошибке (кроме повтора flood) прекращаем сбор окна
            }
            floodControl.reset() // успешная страница сбрасывает счётчик повторов

            val wall = response.response ?: break
            if (wall.items.isEmpty()) break // пустая страница — конец стены

            var reachedOldPosts = false
            for (post in wall.items) {
                if (post.id == 0 && post.ownerId == 0) continue // пропуск «пустышек»

                // Пост старше нижней границы окна — дальше только старее, останавливаемся
                if (post.date < sinceTimestamp) {
                    reachedOldPosts = true
                    break
                }

                // Пост новее верхней границы окна — не относится к окну, пропускаем
                if (post.date > untilTimestamp) continue

                postDao.insert(
                    sessionId = sessionId,
                    vkId = post.id,
                    ownerId = post.ownerId,
                    fromId = post.fromId,
                    publishedAt = post.date,
                    text = post.text,
                    window = window,
                    ownTextLength = post.text.length,
                    likes = post.likes?.count ?: 0,
                    reposts = post.reposts?.count ?: 0,
                    comments = post.comments?.count ?: 0,
                    views = post.views?.count,
                    containsMedia = !post.attachments.isNullOrEmpty(),
                    hasCopyHistory = !post.copyHistory.isNullOrEmpty(),
                )
                collected++
                if (collected >= maxPosts) break // достигнут лимит постов окна
            }

            if (reachedOldPosts) break // вышли за нижнюю границу окна — конец сбора

            offset += pageSize // следующая страница
            if (offset >= wall.count) break // обошли всю стену

            delay(500) // консервативная пауза между страницами (rate limit)
        }

        return WallResult(collected, floodBlocked = false)
    }
}
