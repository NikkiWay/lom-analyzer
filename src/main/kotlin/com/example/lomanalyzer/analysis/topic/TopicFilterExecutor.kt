package com.example.lomanalyzer.analysis.topic

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.ProcessedTextDao
import com.example.lomanalyzer.storage.tables.Posts
import com.example.lomanalyzer.storage.tables.ProcessedTexts
import kotlinx.serialization.json.Json

class TopicFilterExecutor(
    private val postDao: PostDao,
    private val processedTextDao: ProcessedTextDao,
    private val ngramMatcher: NgramMatcher,
    private val topicFilter: TopicRelevanceFilter,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) : StageExecutor {

    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        logger.event(AppEvent.TOPIC_FILTER_APPLIED, mapOf("session_id" to sessionId))

        // Apply to BOTH baseline and current windows per §11.5
        val posts = postDao.findBySession(sessionId)
        val total = posts.size

        for ((index, post) in posts.withIndex()) {
            val postId = post[Posts.id].value
            val cleanText = post[Posts.textClean] ?: ""

            // Get lemmas from ProcessedText
            val processed = processedTextDao.findByPostId(postId)
            val lemmas: List<String> = if (processed != null) {
                val json = processed[ProcessedTexts.lemmasJson]
                if (json != null) Json.decodeFromString(json) else emptyList()
            } else {
                emptyList()
            }

            val matchResult = ngramMatcher.match(lemmas)
            val scoreResult = topicFilter.score(matchResult, cleanText)

            postDao.updateTopicRelevance(
                id = postId,
                relevant = scoreResult.relevant,
                l1 = scoreResult.l1,
                l2 = scoreResult.l2,
                combined = scoreResult.combined,
            )

            if ((index + 1) % 100 == 0 || index == total - 1) {
                progressReporter.update(ProgressEvent(
                    stage = "TOPIC_FILTERING",
                    completedItems = index + 1,
                    totalItems = total,
                ))
            }
        }
    }
}
