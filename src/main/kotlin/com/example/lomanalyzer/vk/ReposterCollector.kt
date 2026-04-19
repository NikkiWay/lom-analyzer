package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.RepostRelationDao
import com.example.lomanalyzer.storage.tables.Posts
import org.jetbrains.exposed.sql.ResultRow

class ReposterCollector(
    private val apiClient: VkApiClient,
    private val postDao: PostDao,
    private val repostRelationDao: RepostRelationDao,
    private val logger: Logger,
) {
    companion object {
        private const val MAX_REPOSTS_TO_COLLECT = 200
    }

    suspend fun collect(sessionId: Int, accessToken: String): Int {
        logger.event(AppEvent.COLLECTION_STARTED, mapOf(
            "session_id" to sessionId,
            "phase" to "REPOSTERS",
        ))

        val posts = postDao.findBySessionAndWindow(sessionId, "CURRENT")
        val eligiblePosts = posts.filter { isEligible(it) }
        var totalReposters = 0

        for (post in eligiblePosts) {
            val ownerId = post[Posts.ownerId]
            val vkId = post[Posts.vkId]
            val postId = post[Posts.id].value

            val response = apiClient.likesGetList(ownerId, vkId, accessToken)
            val reposters = response.response?.items ?: continue

            for (reposterId in reposters) {
                repostRelationDao.insert(
                    sessionId = sessionId,
                    originalPostId = postId,
                    repostPostId = postId,
                    reposterVkId = reposterId,
                    repostedAt = post[Posts.publishedAt],
                )
                totalReposters++
            }
        }

        logger.event(AppEvent.COLLECTION_COMPLETED, mapOf(
            "session_id" to sessionId,
            "phase" to "REPOSTERS",
            "total_reposters" to totalReposters,
        ))
        return totalReposters
    }

    private fun isEligible(post: ResultRow): Boolean {
        val reposts = post[Posts.reposts]
        return reposts in 1..MAX_REPOSTS_TO_COLLECT
    }
}
