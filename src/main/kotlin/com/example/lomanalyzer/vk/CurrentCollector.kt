package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.storage.dao.CheckpointDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.vk.models.VkPost
import java.time.Instant
import java.time.temporal.ChronoUnit

class CurrentCollector(
    private val paginationManager: PaginationManager,
    private val postDao: PostDao,
    private val checkpointDao: CheckpointDao,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
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

        val sinceTimestamp = Instant.now()
            .minus(currentWindowDays.toLong(), ChronoUnit.DAYS)
            .epochSecond
        var totalPosts = 0

        for ((index, communityId) in communityIds.withIndex()) {
            val cpId = checkpointDao.insert(sessionId, "CURRENT", communityId)

            val posts = paginationManager.fetchAllPosts(
                ownerId = -communityId,
                accessToken = accessToken,
                sinceTimestamp = sinceTimestamp,
            )

            for (post in posts) {
                persistPost(sessionId, post)
                totalPosts++
            }

            checkpointDao.updateProgress(cpId, null, posts.size, "COMPLETED")
            progressReporter.update(ProgressEvent(
                stage = "COLLECT_CURRENT",
                completedItems = index + 1,
                totalItems = communityIds.size,
            ))
        }

        logger.event(AppEvent.COLLECTION_COMPLETED, mapOf(
            "session_id" to sessionId,
            "phase" to "CURRENT",
            "total_posts" to totalPosts,
        ))
        return totalPosts
    }

    private fun persistPost(sessionId: Int, post: VkPost) {
        postDao.insert(
            sessionId = sessionId,
            vkId = post.id,
            ownerId = post.ownerId,
            fromId = post.fromId,
            publishedAt = post.date * 1000,
            text = post.text,
            window = "CURRENT",
            ownTextLength = post.text.length,
        )
    }
}
