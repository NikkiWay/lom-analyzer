/*
 * НАЗНАЧЕНИЕ
 * Сбор фоновых публикаций сообществ за период до тематического — окно BASELINE
 * (этап 2 алгоритма). Фон нужен как база сравнения для оценки активности и аномалий.
 *
 * ЧТО ВНУТРИ
 * Класс BaselineCollector: collect (обход сообществ с чекпойнтами и прогрессом) и
 * persistPost (сохранение поста в окно BASELINE).
 *
 * МЕТОД
 * Для каждого сообщества через PaginationManager.fetchAllPosts выгружаются посты до
 * границы по времени (now - baselineWindowDays). owner_id сообщества — отрицательный.
 * Между сообществами выдерживается пауза для защиты от flood control.
 *
 * СВЯЗИ
 * PaginationManager/VkApiClient — выгрузка; PostDao — запись; CheckpointDao — чекпойнты;
 * ProgressReporter — UI; Logger — события COLLECTION_STARTED/COMPLETED. Симметричен CurrentCollector.
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
 * Коллектор фоновых постов сообществ (окно BASELINE).
 */
class BaselineCollector(
    private val paginationManager: PaginationManager,
    private val postDao: PostDao,
    private val checkpointDao: CheckpointDao,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
    /**
     * Собирает фоновые посты заданных сообществ за последние baselineWindowDays суток.
     * @param communityIds id сообществ (положительные; внутри owner_id берётся отрицательным).
     * @return общее число сохранённых постов.
     */
    @Suppress("LongParameterList")
    suspend fun collect(
        sessionId: Int,
        communityIds: List<Int>,
        baselineWindowDays: Int,
        accessToken: String,
    ): Int {
        logger.event(AppEvent.COLLECTION_STARTED, mapOf(
            "session_id" to sessionId,
            "phase" to "BASELINE",
        ))

        // Нижняя граница по времени: фоновые посты не старше baselineWindowDays суток назад
        val sinceTimestamp = Instant.now()
            .minus(baselineWindowDays.toLong(), ChronoUnit.DAYS)
            .epochSecond
        var totalPosts = 0

        for ((index, communityId) in communityIds.withIndex()) {
            // Регистрируем чекпойнт обработки этого сообщества
            val cpId = checkpointDao.insert(sessionId, "BASELINE", communityId)

            // Выгружаем все посты сообщества до границы по времени (owner_id отрицательный)
            val posts = paginationManager.fetchAllPosts(
                ownerId = -communityId,
                accessToken = accessToken,
                maxPosts = 10000,
                sinceTimestamp = sinceTimestamp,
            )

            for (post in posts) {
                persistPost(sessionId, post, "BASELINE")
                totalPosts++
            }

            // Помечаем чекпойнт сообщества как завершённый
            checkpointDao.updateProgress(cpId, null, posts.size, "COMPLETED")
            progressReporter.update(ProgressEvent(
                stage = "Сбор baseline: ${index + 1}/${communityIds.size} сообществ, $totalPosts постов",
                completedItems = index + 1,
                totalItems = communityIds.size,
            ))

            // Pause between communities to avoid VK flood control
            if (index < communityIds.size - 1) delay(3000)
        }

        logger.event(AppEvent.COLLECTION_COMPLETED, mapOf(
            "session_id" to sessionId,
            "phase" to "BASELINE",
            "total_posts" to totalPosts,
        ))
        return totalPosts
    }

    /** Сохраняет фоновый пост сообщества в указанное окно (отображение — см. insertVkPost). */
    private fun persistPost(sessionId: Int, post: VkPost, window: String) {
        postDao.insertVkPost(sessionId, post, window)
    }
}
