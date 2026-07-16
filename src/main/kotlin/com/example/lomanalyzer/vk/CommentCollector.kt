/*
 * НАЗНАЧЕНИЕ
 * Сбор комментариев под тематическими постами (этап 4 алгоритма). Без комментариев
 * невозможна ось отклика аудитории (оценка Resp_a).
 *
 * ЧТО ВНУТРИ
 * Класс CommentCollector: collect (отбор топ-постов по числу комментариев и обход) и
 * collectForPost (пагинация комментариев одного поста через wall.getComments).
 *
 * МЕТОД / ОБРАБОТКА ОШИБОК
 * Из постов окна CURRENT отбираются те, у кого есть комментарии, и берётся топ-N по их числу.
 * wall.getComments листается по offset до ожидаемого числа комментариев. Ошибка 9 (flood
 * control) обрабатывается повтором с нарастающими задержками; прочие ошибки журналируются.
 *
 * СВЯЗИ
 * VkApiClient.wallGetComments — комментарии; PostDao — источник постов; CommentDao — запись;
 * SessionEventService/ProgressReporter/Logger — журнал и прогресс.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.storage.dao.CommentDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.tables.Posts
import kotlinx.coroutines.delay

/**
 * Collects comments under topic posts (diploma 2.1.1, stage 4).
 * Without comments, the "Audience Response" axis (Resp_a) is impossible.
 *
 * @see Подраздел_2_1_3_используемые_оценки.md — Resp_a formula
 */
class CommentCollector(
    private val apiClient: VkApiClient,
    private val postDao: PostDao,
    private val commentDao: CommentDao,
    private val sessionEventService: SessionEventService,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
    companion object {
        /** Число комментариев на страницу wall.getComments (максимум VK — 100). */
        private const val PAGE_SIZE = 100

        /** Максимум постов, для которых собираются комментарии (топ по числу комментариев). */
        private const val MAX_POSTS_FOR_COMMENTS = 200

        /** Пауза между постами (мс). */
        private const val DELAY_BETWEEN_POSTS_MS = 500L

        /** Максимум повторов при flood control (ошибка 9). */
        private const val MAX_FLOOD_RETRIES = 3

        /** Нарастающие задержки повторов при flood control: 10с, 30с, 60с. */
        private val FLOOD_RETRY_DELAYS_MS = longArrayOf(10_000L, 30_000L, 60_000L)
    }

    /**
     * For top [MAX_POSTS_FOR_COMMENTS] posts (by comment count) that have comments > 0,
     * fetch all comments via wall.getComments and persist them.
     */
    suspend fun collect(sessionId: Int, accessToken: String): Int {
        // Из постов CURRENT берём те, у кого есть комментарии, и сортируем по их числу (убыв.)
        val allWithComments = postDao.findBySessionAndWindow(sessionId, "CURRENT")
            .filter { (it[Posts.comments]) > 0 }
            .sortedByDescending { it[Posts.comments] }

        // При избытке постов ограничиваемся топ-N самых обсуждаемых
        val posts = if (allWithComments.size > MAX_POSTS_FOR_COMMENTS) {
            sessionEventService.logInfo(sessionId,
                "Отбор топ-$MAX_POSTS_FOR_COMMENTS постов из ${allWithComments.size} по числу комментариев")
            allWithComments.take(MAX_POSTS_FOR_COMMENTS)
        } else {
            allWithComments
        }

        if (posts.isEmpty()) {
            logger.info("No posts with comments to collect for session #$sessionId")
            return 0
        }

        var totalComments = 0
        val totalPosts = posts.size
        var processedPosts = 0

        sessionEventService.logInfo(sessionId, "Начат сбор комментариев для $totalPosts публикаций")

        for (post in posts) {
            // Идентификаторы поста: внутренний id в БД, owner_id и id поста в VK, ожидаемое число комментариев
            val postDbId = post[Posts.id].value
            val ownerId = post[Posts.ownerId]
            val vkPostId = post[Posts.vkId]
            val expectedCount = post[Posts.comments]

            val collected = collectForPost(sessionId, postDbId, ownerId, vkPostId, expectedCount, accessToken)
            totalComments += collected
            processedPosts++

            // Логируем прогресс примерно каждые 10% постов
            if (totalPosts >= 10 && processedPosts % (totalPosts / 10).coerceAtLeast(1) == 0) {
                sessionEventService.logProgress(sessionId, "Комментарии", processedPosts, totalPosts)
            }

            progressReporter.update(ProgressEvent(
                stage = "Сбор комментариев: $processedPosts/$totalPosts постов, $totalComments комментариев",
                completedItems = processedPosts,
                totalItems = totalPosts,
            ))

            delay(DELAY_BETWEEN_POSTS_MS) // пауза между постами (rate limit)
        }

        sessionEventService.logInfo(
            sessionId,
            "Сбор комментариев завершён: $totalComments комментариев из $totalPosts публикаций",
        )
        logger.event(AppEvent.COLLECTION_COMPLETED, mapOf(
            "session_id" to sessionId,
            "phase" to "COMMENTS",
            "total_comments" to totalComments,
            "posts_with_comments" to totalPosts,
        ))

        return totalComments
    }

    /**
     * Собирает все комментарии одного поста через wall.getComments с пагинацией по offset.
     * @param postDbId внутренний id поста в БД (для привязки комментариев).
     * @param ownerId/vkPostId владелец и id поста в VK.
     * @param expectedCount ожидаемое число комментариев (верхняя граница цикла).
     * @return фактически сохранённое число комментариев.
     */
    private suspend fun collectForPost(
        sessionId: Int,
        postDbId: Int,
        ownerId: Int,
        vkPostId: Int,
        expectedCount: Int,
        accessToken: String,
    ): Int {
        var offset = 0 // смещение пагинации
        var collected = 0
        var floodRetries = 0

        while (collected < expectedCount) {
            // Запрос страницы комментариев поста
            val response = apiClient.wallGetComments(
                ownerId = ownerId,
                postId = vkPostId,
                offset = offset,
                count = PAGE_SIZE,
                accessToken = accessToken,
            )

            if (response.error != null) {
                // Ошибка 9 — flood control: ждём по нарастающей и повторяем ту же страницу
                if (response.error.errorCode == 9) {
                    floodRetries++
                    if (floodRetries > MAX_FLOOD_RETRIES) {
                        sessionEventService.logApiError(sessionId, "wall.getComments",
                            9, "Flood control: max retries for post $vkPostId")
                        break
                    }
                    val delayMs = FLOOD_RETRY_DELAYS_MS[floodRetries - 1]
                    delay(delayMs)
                    continue // повтор без смены offset
                }
                // Прочие ошибки VK — журналируем и прекращаем сбор по этому посту
                sessionEventService.logApiError(
                    sessionId,
                    "wall.getComments",
                    response.error.errorCode,
                    response.error.errorMsg,
                )
                break
            }
            floodRetries = 0 // успешная страница сбрасывает счётчик повторов

            val comments = response.response?.items ?: break
            if (comments.isEmpty()) break // пустая страница — конец комментариев

            for (comment in comments) {
                if (comment.id == 0) continue // пропуск «пустышек»
                // Сохраняем комментарий; пустой текст нормализуем в null
                commentDao.insert(
                    sessionId = sessionId,
                    postId = postDbId,
                    vkId = comment.id,
                    fromId = comment.fromId,
                    text = comment.text.ifBlank { null },
                    publishedAt = comment.date,
                    likes = comment.likes?.count ?: 0,
                )
                collected++
            }

            offset += PAGE_SIZE // следующая страница
            // Вышли за общее число комментариев под постом — выходим
            if (offset >= (response.response?.count ?: 0)) break
        }

        return collected
    }
}
