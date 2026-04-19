package com.example.lomanalyzer.analysis.dedup

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.DedupGroupDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.tables.DedupGroups
import com.example.lomanalyzer.storage.tables.Posts

class OriginalityExecutor(
    private val postDao: PostDao,
    private val dedupGroupDao: DedupGroupDao,
    private val logger: Logger,
) : StageExecutor {

    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        val posts = postDao.findBySession(sessionId)
        val dedupGroups = dedupGroupDao.findBySession(sessionId)

        // Build set of duplicate post IDs (those that are not group leaders)
        val detectedCopyIds = dedupGroups
            .filter { it[DedupGroups.method] == "EXACT" }
            .map { it[DedupGroups.duplicatePostId].value }
            .toSet()

        var classified = 0
        for (post in posts) {
            val postId = post[Posts.id].value
            val hasCopyHistory = post[Posts.hasCopyHistory]
            val ownTextLength = post[Posts.ownTextLength]
            val containsMedia = post[Posts.containsMedia]
            val isDetectedCopy = postId in detectedCopyIds

            OriginalityClassifier.classify(
                hasCopyHistory = hasCopyHistory,
                ownTextLength = ownTextLength,
                containsMedia = containsMedia,
                isDetectedCopy = isDetectedCopy,
            )
            classified++
        }

        logger.event(AppEvent.ORIGINALITY_CLASSIFIED, mapOf(
            "session_id" to sessionId,
            "posts_classified" to classified,
        ))
    }
}
