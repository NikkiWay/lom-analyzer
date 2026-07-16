/*
 * НАЗНАЧЕНИЕ
 * Сбор публикаций сообществ (этап 2 алгоритма) в одно из двух окон наблюдения:
 * CURRENT — тематический период, BASELINE — фоновый период до него, служащий
 * базой сравнения. Окно задаётся аргументом вызова.
 *
 * ЧТО ВНУТРИ
 * enum CollectionWindow — окно сбора и его параметры (лимит, подписи).
 * class CommunityPostCollector: collect — обход сообществ с чекпойнтами и прогрессом.
 *
 * МЕТОД
 * Для каждого сообщества через PaginationManager.fetchAllPosts выгружаются посты
 * до нижней границы по времени (now − windowDays). owner_id сообщества
 * передаётся отрицательным — так VK отличает сообщество от пользователя.
 * Между сообществами выдерживается пауза: защита от flood control (ошибка 9).
 *
 * СВЯЗИ
 * PaginationManager/VkApiClient — выгрузка; PostDao.insertVkPost — запись;
 * CheckpointDao — контрольные точки; ProgressReporter — UI; Logger — события
 * COLLECTION_STARTED/COMPLETED. Вызывается из PipelineWiring (стадия
 * DATA_COLLECTION, ветка A1: сообщества заданы).
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.storage.dao.CheckpointDao
import com.example.lomanalyzer.storage.dao.PostDao
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Окно сбора постов сообществ.
 *
 * Лимиты различаются намеренно: фоновое окно длиннее тематического (по умолчанию
 * 60 суток против 30), поэтому на сообщество допускается больше постов.
 *
 * @property dbValue значение колонки post.window — по нему оценки делят посты на
 *   фоновые и тематические (ER_bg против ER_top).
 * @property maxPostsPerCommunity верхняя граница выгрузки на одно сообщество.
 * @property progressLabel подпись окна в строке прогресса UI.
 */
enum class CollectionWindow(
    val dbValue: String,
    val maxPostsPerCommunity: Int,
    val progressLabel: String,
) {
    /** Фоновый период до тематического: база сравнения активности. */
    BASELINE("BASELINE", 10_000, "baseline"),

    /** Тематический период: публикации, среди которых ищется тема. */
    CURRENT("CURRENT", 5_000, "current"),
}

/**
 * Коллектор постов сообществ в заданное окно наблюдения.
 */
class CommunityPostCollector(
    private val paginationManager: PaginationManager,
    private val postDao: PostDao,
    private val checkpointDao: CheckpointDao,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
    companion object {
        /** Пауза между сообществами (мс) — снижает риск flood control. */
        private const val DELAY_BETWEEN_COMMUNITIES_MS = 3_000L
    }

    /**
     * Собирает посты заданных сообществ за последние [windowDays] суток.
     *
     * @param communityIds id сообществ (положительные; owner_id берётся отрицательным).
     * @param windowDays глубина окна в сутках.
     * @param window окно сбора: фон либо тематический период.
     * @return общее число сохранённых постов.
     */
    suspend fun collect(
        sessionId: Int,
        communityIds: List<Int>,
        windowDays: Int,
        window: CollectionWindow,
        accessToken: String,
    ): Int {
        logger.event(AppEvent.COLLECTION_STARTED, mapOf(
            "session_id" to sessionId,
            "phase" to window.dbValue,
        ))

        // Нижняя граница по времени: посты не старше windowDays суток назад
        val sinceTimestamp = Instant.now()
            .minus(windowDays.toLong(), ChronoUnit.DAYS)
            .epochSecond
        var totalPosts = 0

        for ((index, communityId) in communityIds.withIndex()) {
            // Регистрируем чекпойнт обработки этого сообщества
            val checkpointId = checkpointDao.insert(sessionId, window.dbValue, communityId)

            // Выгружаем все посты сообщества до границы по времени (owner_id отрицательный)
            val posts = paginationManager.fetchAllPosts(
                ownerId = -communityId,
                accessToken = accessToken,
                maxPosts = window.maxPostsPerCommunity,
                sinceTimestamp = sinceTimestamp,
            )

            for (post in posts) {
                postDao.insertVkPost(sessionId, post, window.dbValue)
                totalPosts++
            }

            // Помечаем чекпойнт сообщества как завершённый и обновляем прогресс
            checkpointDao.updateProgress(checkpointId, null, posts.size, "COMPLETED")
            progressReporter.update(ProgressEvent(
                stage = "Сбор ${window.progressLabel}: ${index + 1}/${communityIds.size} " +
                    "сообществ, $totalPosts постов",
                completedItems = index + 1,
                totalItems = communityIds.size,
            ))

            // Пауза между сообществами; после последнего ждать незачем
            if (index < communityIds.size - 1) delay(DELAY_BETWEEN_COMMUNITIES_MS)
        }

        logger.event(AppEvent.COLLECTION_COMPLETED, mapOf(
            "session_id" to sessionId,
            "phase" to window.dbValue,
            "total_posts" to totalPosts,
        ))
        return totalPosts
    }
}
