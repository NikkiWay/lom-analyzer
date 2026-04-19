package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.tables.Posts
import kotlin.math.ln

class DiscoveryEngine(
    private val postDao: PostDao,
    private val authorDao: AuthorDao,
    private val logger: Logger,
) {
    companion object {
        private const val MAX_DISCOVERY_AUTHORS = 30
        private const val REPOST_THRESHOLD = 50
    }

    fun discover(sessionId: Int): List<Int> {
        // Rule 1 only: authors with any original topical post having reposts >= 50
        val currentPosts = postDao.findBySessionAndWindow(sessionId, "CURRENT")

        // Group by fromId, find candidates
        val candidates = mutableMapOf<Int, Double>()

        for (post in currentPosts) {
            val reposts = post[Posts.reposts]
            if (reposts >= REPOST_THRESHOLD) {
                val fromId = post[Posts.fromId]
                // Skip if already a session author
                val existing = authorDao.findByVkId(fromId)
                if (existing != null) continue

                // DPS = ln(1 + F) + sum(ln(1 + M_reach_p))
                val followersCount = 0 // unknown for discovery candidates
                val dps = ln(1.0 + followersCount) + ln(1.0 + reposts)
                candidates[fromId] = (candidates[fromId] ?: 0.0) + dps
            }
        }

        // Sort by DPS, take top MAX_DISCOVERY_AUTHORS
        val topCandidates = candidates.entries
            .sortedByDescending { it.value }
            .take(MAX_DISCOVERY_AUTHORS)
            .map { it.key }

        for (vkId in topCandidates) {
            logger.event(AppEvent.DISCOVERY_AUTHOR_ADDED, mapOf(
                "session_id" to sessionId,
                "vk_id" to vkId,
            ))
        }

        return topCandidates
    }
}
