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

class BaselineCollector(
    private val paginationManager: PaginationManager,
    private val postDao: PostDao,
    private val checkpointDao: CheckpointDao,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
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

        val sinceTimestamp = Instant.now()
            .minus(baselineWindowDays.toLong(), ChronoUnit.DAYS)
            .epochSecond
        var totalPosts = 0

        for ((index, communityId) in communityIds.withIndex()) {
            val cpId = checkpointDao.insert(sessionId, "BASELINE", communityId)

            val posts = paginationManager.fetchAllPosts(
                ownerId = -communityId,
                accessToken = accessToken,
                maxPosts = 300,
                sinceTimestamp = sinceTimestamp,
            )

            for (post in posts) {
                persistPost(sessionId, post, "BASELINE")
                totalPosts++
            }

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

    private fun persistPost(sessionId: Int, post: VkPost, window: String) {
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
    }
}
