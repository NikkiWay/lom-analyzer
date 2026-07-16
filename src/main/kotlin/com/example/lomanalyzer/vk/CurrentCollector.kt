/*
 * НАЗНАЧЕНИЕ
 * Сбор тематических публикаций сообществ за текущий (тематический) период — окно CURRENT
 * (этап 2 алгоритма). Обходит заданные сообщества и сохраняет их посты в БД.
 *
 * ЧТО ВНУТРИ
 * Класс CurrentCollector: collect (обход сообществ с чекпойнтами и прогрессом) и
 * persistPost (сохранение поста в окно CURRENT).
 *
 * МЕТОД
 * Для каждого сообщества через PaginationManager.fetchAllPosts выгружаются посты до
 * границы по времени (now - currentWindowDays). owner_id сообщества передаётся отрицательным.
 * Между сообществами выдерживается пауза для защиты от flood control.
 *
 * СВЯЗИ
 * PaginationManager/VkApiClient — выгрузка постов; PostDao — запись; CheckpointDao —
 * контрольные точки сбора; ProgressReporter — UI; Logger — события COLLECTION_STARTED/COMPLETED.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.storage.dao.CheckpointDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.vk.models.VkPost
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Коллектор тематических постов сообществ за текущий период (окно CURRENT).
 */
class CurrentCollector(
    private val paginationManager: PaginationManager,
    private val postDao: PostDao,
    private val checkpointDao: CheckpointDao,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
    /**
     * Собирает посты заданных сообществ за последние currentWindowDays суток.
     * @param communityIds id сообществ (положительные; внутри owner_id берётся отрицательным).
     * @return общее число сохранённых постов.
     */
    suspend fun collect(
        sessionId: Int,
        communityIds: List<Int>,
        currentWindowDays: Int,
        accessToken: String,
    ): Int {
        logger.event(AppEvent.COLLECTION_STARTED, mapOf(
            "session_id" to sessionId,
            "phase" to "CURRENT",
        ))

        // Нижняя граница по времени: посты не старше currentWindowDays суток назад
        val sinceTimestamp = Instant.now()
            .minus(currentWindowDays.toLong(), ChronoUnit.DAYS)
            .epochSecond
        var totalPosts = 0

        for ((index, communityId) in communityIds.withIndex()) {
            // Регистрируем чекпойнт обработки этого сообщества
            val cpId = checkpointDao.insert(sessionId, "CURRENT", communityId)

            // Выгружаем все посты сообщества до границы по времени (owner_id отрицательный)
            val posts = paginationManager.fetchAllPosts(
                ownerId = -communityId,
                accessToken = accessToken,
                maxPosts = 5000,
                sinceTimestamp = sinceTimestamp,
            )

            for (post in posts) {
                persistPost(sessionId, post)
                totalPosts++
            }

            // Помечаем чекпойнт сообщества как завершённый и обновляем прогресс
            checkpointDao.updateProgress(cpId, null, posts.size, "COMPLETED")
            progressReporter.update(ProgressEvent(
                stage = "Сбор current: ${index + 1}/${communityIds.size} сообществ, $totalPosts постов",
                completedItems = index + 1,
                totalItems = communityIds.size,
            ))

            // Пауза между сообществами — снижает риск flood control
            if (index < communityIds.size - 1) delay(3000)
        }

        logger.event(AppEvent.COLLECTION_COMPLETED, mapOf(
            "session_id" to sessionId,
            "phase" to "CURRENT",
            "total_posts" to totalPosts,
        ))
        return totalPosts
    }

    /** Сохраняет пост сообщества в окно CURRENT (метаданные, счётчики, признаки медиа/репоста). */
    private fun persistPost(sessionId: Int, post: VkPost) {
        postDao.insertVkPost(sessionId, post, "CURRENT")
    }
}
