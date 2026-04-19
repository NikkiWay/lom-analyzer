package com.example.lomanalyzer.vk

import com.example.lomanalyzer.nlp.NlpService
import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.RepostRelationDao
import com.example.lomanalyzer.storage.tables.Posts
import com.example.lomanalyzer.storage.tables.RepostRelations
import org.jetbrains.exposed.sql.ResultRow
import kotlin.math.ln

/**
 * Discovery engine per v6 §9.3 — all 3 rules + DPS scoring.
 * Top-30 by DPS. Discovery authors get shortened 30-day baseline.
 */
class DiscoveryEngine(
    private val postDao: PostDao,
    private val authorDao: AuthorDao,
    private val repostRelationDao: RepostRelationDao? = null,
    private val nlpService: NlpService? = null,
    private val logger: Logger,
) {
    companion object {
        private const val MAX_DISCOVERY = 30
        private const val REPOST_THRESHOLD = 50
        private const val RULE2_MIN_POSTS = 5
        private const val RULE2_MIN_AUTHORS = 3
        private const val RULE3_MIN_MENTIONS = 3
    }

    suspend fun discover(sessionId: Int): List<DiscoveryCandidate> {
        val currentPosts = postDao.findBySessionAndWindow(sessionId, "CURRENT")
        val candidates = mutableMapOf<Int, DiscoveryCandidate>()

        applyRule1(currentPosts, candidates)
        applyRule2(sessionId, candidates)
        applyRule3(currentPosts)

        return selectTop(candidates)
    }

    private fun applyRule1(
        posts: List<ResultRow>,
        candidates: MutableMap<Int, DiscoveryCandidate>,
    ) {
        val eligible = posts.filter {
            it[Posts.reposts] >= REPOST_THRESHOLD && !isKnownAuthor(it[Posts.fromId])
        }
        for (post in eligible) {
            val reposts = post[Posts.reposts]
            val fromId = post[Posts.fromId]
            val c = candidates.getOrPut(fromId) { DiscoveryCandidate(fromId) }
            c.dps += ln(1.0 + reposts)
            c.rules.add("RULE_1")
            logger.event(AppEvent.DISCOVERY_RULE_1_MATCHED, mapOf("vk_id" to fromId))
        }
    }

    private fun applyRule2(
        sessionId: Int,
        candidates: MutableMap<Int, DiscoveryCandidate>,
    ) {
        if (repostRelationDao == null) return
        val relations = repostRelationDao.findBySession(sessionId)
        val stats = buildReposterStats(relations)

        for ((reposterId, postAuthors) in stats) {
            if (isKnownAuthor(reposterId)) continue
            val distinctPosts = postAuthors.map { it.second }.distinct().size
            val distinctAuthors = postAuthors.map { it.first }.distinct().size
            if (distinctPosts >= RULE2_MIN_POSTS && distinctAuthors >= RULE2_MIN_AUTHORS) {
                val c = candidates.getOrPut(reposterId) { DiscoveryCandidate(reposterId) }
                c.dps += ln(1.0 + distinctPosts)
                c.rules.add("RULE_2")
                logger.event(AppEvent.DISCOVERY_RULE_2_MATCHED, mapOf("vk_id" to reposterId))
            }
        }
    }

    private fun buildReposterStats(
        relations: List<ResultRow>,
    ): Map<Int, Set<Pair<Int, Int>>> {
        val result = mutableMapOf<Int, MutableSet<Pair<Int, Int>>>()
        for (rel in relations) {
            val reposterId = rel[RepostRelations.reposterVkId]
            val origPostId = rel[RepostRelations.originalPostId].value
            val origPost = postDao.findById(origPostId)
            val origAuthor = origPost?.get(Posts.fromId) ?: continue
            result.getOrPut(reposterId) { mutableSetOf() }.add(origAuthor to origPostId)
        }
        return result
    }

    private suspend fun applyRule3(posts: List<ResultRow>) {
        if (nlpService == null) return
        val mentionCounts = mutableMapOf<String, Int>()
        for (post in posts) {
            val text = post[Posts.textClean] ?: post[Posts.text] ?: ""
            if (text.isBlank()) continue
            for (entity in nlpService.extractEntities(text)) {
                if (entity.type == "PER") {
                    val key = entity.text.lowercase().trim()
                    mentionCounts[key] = (mentionCounts[key] ?: 0) + 1
                }
            }
        }
        for ((name, count) in mentionCounts) {
            if (count >= RULE3_MIN_MENTIONS) {
                logger.event(AppEvent.DISCOVERY_RULE_3_MATCHED, mapOf("name" to name, "count" to count))
            }
        }
    }

    private fun selectTop(candidates: Map<Int, DiscoveryCandidate>): List<DiscoveryCandidate> {
        val top = candidates.values.sortedByDescending { it.dps }.take(MAX_DISCOVERY)
        for (c in top) {
            logger.event(AppEvent.DISCOVERY_AUTHOR_ADDED, mapOf(
                "vk_id" to c.vkId, "dps" to c.dps, "rules" to c.rules.joinToString(","),
            ))
        }
        return top
    }

    private fun isKnownAuthor(vkId: Int): Boolean = authorDao.findByVkId(vkId) != null
}

data class DiscoveryCandidate(
    val vkId: Int,
    var dps: Double = 0.0,
    val rules: MutableSet<String> = mutableSetOf(),
)
