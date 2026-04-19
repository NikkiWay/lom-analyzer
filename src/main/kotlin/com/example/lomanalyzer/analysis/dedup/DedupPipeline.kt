package com.example.lomanalyzer.analysis.dedup

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.DedupGroupDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.ProcessedTextDao
import com.example.lomanalyzer.storage.tables.Posts
import com.example.lomanalyzer.storage.tables.ProcessedTexts
import kotlinx.serialization.json.Json

class DedupPipeline(
    private val postDao: PostDao,
    private val processedTextDao: ProcessedTextDao,
    private val dedupGroupDao: DedupGroupDao,
    private val boundedJaccard: BoundedJaccard,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) : StageExecutor {

    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        val allPosts = postDao.findBySession(sessionId)
        val hashablePosts = allPosts.map { row ->
            HashablePost(
                postId = row[Posts.id].value,
                fromId = row[Posts.fromId],
                publishedAt = row[Posts.publishedAt],
                cleanText = row[Posts.textClean] ?: row[Posts.text] ?: "",
                ownTextLength = row[Posts.ownTextLength],
                isTopicRelevant = row[Posts.isTopicRelevant],
            )
        }

        val detectedCopyIds = runStage1(sessionId, hashablePosts)
        runStage2(sessionId, hashablePosts, detectedCopyIds)
    }

    private fun runStage1(sessionId: Int, posts: List<HashablePost>): Set<Int> {
        val detectedCopyIds = mutableSetOf<Int>()
        val exactGroups = ExactHasher.findDuplicateGroups(posts)

        for ((_, group) in exactGroups) {
            val leader = group.first()
            for (dup in group.drop(1)) {
                dedupGroupDao.insert(sessionId, leader.postId, dup.postId, 1.0f, "EXACT")
                detectedCopyIds.add(dup.postId)
            }
            logger.event(AppEvent.DEDUP_GROUP_FORMED, mapOf(
                "session_id" to sessionId, "method" to "EXACT", "group_size" to group.size,
            ))
        }

        logger.event(AppEvent.DEDUP_STAGE1_COMPLETED, mapOf(
            "session_id" to sessionId, "groups" to exactGroups.size, "copies" to detectedCopyIds.size,
        ))
        return detectedCopyIds
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private suspend fun runStage2(sessionId: Int, posts: List<HashablePost>, excludeIds: Set<Int>) {
        val candidates = posts.filter {
            boundedJaccard.isEligible(it.ownTextLength, it.isTopicRelevant) && it.postId !in excludeIds
        }

        val lemmaCache = buildLemmaCache(candidates)
        val nearDupPairs = mutableSetOf<Pair<Int, Int>>()

        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                val sim = comparePair(candidates[i], candidates[j], lemmaCache) ?: continue
                recordNearDup(sessionId, candidates[i], candidates[j], sim, nearDupPairs)
            }
            reportProgress(i, candidates.size)
        }

        logger.event(AppEvent.DEDUP_STAGE2_COMPLETED, mapOf(
            "session_id" to sessionId, "near_dup_pairs" to nearDupPairs.size,
        ))
    }

    private fun buildLemmaCache(candidates: List<HashablePost>): Map<Int, List<String>> {
        val cache = mutableMapOf<Int, List<String>>()
        for (post in candidates) {
            val processed = processedTextDao.findByPostId(post.postId)
            val json = processed?.get(ProcessedTexts.lemmasJson)
            cache[post.postId] = if (json != null) Json.decodeFromString(json) else emptyList()
        }
        return cache
    }

    @Suppress("ReturnCount")
    private fun comparePair(a: HashablePost, b: HashablePost, cache: Map<Int, List<String>>): Float? {
        val lemmasA = cache[a.postId] ?: return null
        val lemmasB = cache[b.postId] ?: return null
        val (isDup, sim) = boundedJaccard.isNearDuplicate(lemmasA, lemmasB, a.publishedAt, b.publishedAt)
        return if (isDup) sim else null
    }

    private fun recordNearDup(
        sessionId: Int,
        a: HashablePost,
        b: HashablePost,
        sim: Float,
        seen: MutableSet<Pair<Int, Int>>,
    ) {
        val (leader, dup) = if (a.publishedAt <= b.publishedAt) a to b else b to a
        val key = minOf(leader.postId, dup.postId) to maxOf(leader.postId, dup.postId)
        if (key !in seen) {
            seen.add(key)
            dedupGroupDao.insert(sessionId, leader.postId, dup.postId, sim, "NEAR_DUPLICATE")
        }
    }

    private suspend fun reportProgress(index: Int, total: Int) {
        if ((index + 1) % 50 == 0 || index == total - 1) {
            progressReporter.update(ProgressEvent("DEDUPLICATION", index + 1, total))
        }
    }
}
