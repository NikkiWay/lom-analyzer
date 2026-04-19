package com.example.lomanalyzer.preprocessing

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.ProcessedTextDao
import com.example.lomanalyzer.storage.tables.Posts
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PreprocessingExecutor(
    private val postDao: PostDao,
    private val processedTextDao: ProcessedTextDao,
    private val languageDetector: LanguageDetectorProxy,
    private val lemmatizer: LemmatizerProxy,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) : StageExecutor {

    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        logger.event(AppEvent.PREPROCESSING_STARTED, mapOf("session_id" to sessionId))

        val posts = postDao.findBySession(sessionId)
        val total = posts.size

        for ((index, post) in posts.withIndex()) {
            val postId = post[Posts.id].value
            val rawText = post[Posts.text] ?: ""

            val cleaned = TextCleaner.clean(rawText)
            val tokens = Tokenizer.tokenize(cleaned.cleanText)
            val langResult = languageDetector.detectFallback(tokens)

            if (langResult.flag == "FILTERED_OUT_LANGUAGE") {
                logger.event(AppEvent.FILTERED_OUT_LANGUAGE, mapOf(
                    "post_id" to postId,
                    "confidence" to langResult.confidence,
                ))
            }

            val filtered = StopWords.filter(tokens)
            val lemmas = lemmatizer.stemFallback(filtered)
            val lemmaStrings = lemmas.map { it.lemma }

            processedTextDao.insert(
                postId = postId,
                lemmasJson = Json.encodeToString(lemmaStrings),
                language = langResult.language,
                cleanText = cleaned.cleanText,
            )

            // Update post fields
            postDao.updatePostPreprocessing(
                id = postId,
                textClean = cleaned.cleanText,
                ownTextLength = cleaned.cleanText.length,
                truncated = cleaned.truncated,
                truncationReason = cleaned.truncationReason,
                detectedLanguage = langResult.language,
                languageConfidence = langResult.confidence,
                languageFlag = langResult.flag,
                hashtagsCount = cleaned.hashtagsCount,
                mentionsCount = cleaned.mentionsCount,
                urlsCount = cleaned.urlsCount,
                containsMedia = post[Posts.containsMedia],
            )

            if ((index + 1) % 50 == 0 || index == total - 1) {
                progressReporter.update(ProgressEvent(
                    stage = "PREPROCESSING",
                    completedItems = index + 1,
                    totalItems = total,
                ))
            }
        }

        logger.event(AppEvent.PREPROCESSING_COMPLETED, mapOf(
            "session_id" to sessionId,
            "posts_processed" to total,
        ))
    }
}
