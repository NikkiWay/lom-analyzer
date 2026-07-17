/*
 * НАЗНАЧЕНИЕ
 * Исполнитель этапа 5 пайплайна (см. docs/algorithm.md): препроцессинг собранного
 * текста — очистка, токенизация, определение языка, лемматизация и анализ тональности
 * (sentiment) постов и комментариев. Результаты сохраняются в БД для последующих
 * этапов (тематическая фильтрация, оценки).
 *
 * ЧТО ВНУТРИ
 *  PreprocessingExecutor — реализация StageExecutor; локальный CleanedPost; метод execute()
 *  из 4 шагов: (1) очистка+язык, (2) пакетная лемматизация, (3) sentiment постов CURRENT,
 *  (4) sentiment комментариев.
 *
 * МЕТОД / ДВУХРЕЖИМНОСТЬ (architecture.md, 2.2.7)
 *  Через NlpServiceSelector выбирается режим: Python FastAPI sidecar (батч-обработка
 *  через PythonSidecarNlpService — pymorphy3, dostoevsky/RuBERT) либо Kotlin-fallback
 *  (Snowball stemmer + словарный sentiment). Батчи по BATCH_SIZE амортизируют IPC-накладные
 *  расходы; при ошибке батча — мягкая деградация на Kotlin/нейтральную метку.
 *  Sentiment считается только для постов окна CURRENT (BASELINE пропускается ради экономии).
 *
 * БИБЛИОТЕКИ / СВЯЗИ
 *  Exposed DAO (Posts/Comments/ProcessedTexts/SentimentResults), kotlinx.serialization
 *  (леммы -> JSON), корутины (suspend), Logger/ProgressReporter (события и прогресс UI).
 *  Использует TextCleaner, Tokenizer, StopWords, LanguageDetectorProxy, LemmatizerProxy.
 */
package com.example.lomanalyzer.preprocessing

import com.example.lomanalyzer.nlp.NlpServiceSelector
import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.CommentDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.ProcessedTextDao
import com.example.lomanalyzer.storage.dao.SentimentResultDao
import com.example.lomanalyzer.storage.tables.SentimentEntityType
import com.example.lomanalyzer.storage.tables.Comments
import com.example.lomanalyzer.storage.tables.Posts
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Исполнитель этапа 5: очистка, лемматизация, язык и тональность текстов сессии. */
class PreprocessingExecutor(
    private val postDao: PostDao,
    private val processedTextDao: ProcessedTextDao,
    private val commentDao: CommentDao,
    private val sentimentResultDao: SentimentResultDao,
    private val nlpServiceSelector: NlpServiceSelector,
    private val languageDetector: LanguageDetectorProxy,
    private val lemmatizer: LemmatizerProxy,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) : StageExecutor {

    companion object {
        /** Размер батча для пакетных вызовов NLP (амортизация IPC к Python sidecar). */
        private const val BATCH_SIZE = 50
    }

    /**
     * Выполняет препроцессинг всех постов и комментариев сессии.
     * @param stage текущая стадия пайплайна.
     */
    @Suppress("LongMethod", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        logger.event(AppEvent.PREPROCESSING_STARTED, mapOf("session_id" to sessionId))
        // Режим — у селектора, а не по типу сервиса: getService() возвращает
        // декоратор CachingNlpService, и проверка приведением к типу sidecar
        // всегда давала бы null, отключая пакетный режим при живом sidecar.
        val nlpService = nlpServiceSelector.getService()
        val usePython = nlpServiceSelector.mode == "FULL"

        val posts = postDao.findBySession(sessionId)
        val total = posts.size

        // ── Шаг 1: очистка + определение языка (быстрая Kotlin-эвристика) ──
        data class CleanedPost(
            val postId: Int, val rawText: String, val cleanText: String,
            val tokens: List<String>, val window: String, val containsMedia: Boolean,
            val langResult: LanguageResult,
        )

        val cleaned = posts.mapIndexed { index, post ->
            val postId = post[Posts.id].value
            val rawText = post[Posts.text] ?: ""
            // Очищаем текст, токенизируем, определяем язык быстрой эвристикой
            val cl = TextCleaner.clean(rawText)
            val tokens = Tokenizer.tokenize(cl.cleanText)
            val lang = languageDetector.detectFallback(tokens)

            // Сохраняем результаты препроцессинга поста в БД
            postDao.updatePostPreprocessing(
                id = postId, textClean = cl.cleanText, ownTextLength = cl.cleanText.length,
                truncated = cl.truncated, truncationReason = cl.truncationReason,
                detectedLanguage = lang.language, languageConfidence = lang.confidence,
                languageFlag = lang.flag, hashtagsCount = cl.hashtagsCount,
                mentionsCount = cl.mentionsCount, urlsCount = cl.urlsCount,
                containsMedia = post[Posts.containsMedia],
            )

            // Обновляем прогресс UI каждые 100 постов и на последнем
            if ((index + 1) % 100 == 0 || index == total - 1) {
                progressReporter.update(ProgressEvent(
                    "Очистка текстов: ${index + 1}/$total", index + 1, total,
                ))
            }

            CleanedPost(postId, rawText, cl.cleanText, tokens,
                post[Posts.window] ?: "CURRENT", post[Posts.containsMedia], lang)
        }

        // ── Шаг 2: пакетная лемматизация ──
        // Перед лемматизацией убираем стоп-слова и собираем токены обратно в строку
        val textsForLemma = cleaned.map { StopWords.filter(it.tokens).joinToString(" ") }

        val allLemmas: List<List<String>> = if (usePython) {
            // Основной режим: батчевая lemmatization через Python sidecar
            logger.info("Batch lemmatization via Python ($total texts, batch=$BATCH_SIZE)")
            val result = mutableListOf<List<String>>()
            for (chunk in textsForLemma.chunked(BATCH_SIZE)) {
                try {
                    // Запрос к sidecar через кэширующий слой; леммы — в нижний регистр
                    val batch = nlpService.batchLemmatize(chunk)
                    result.addAll(batch.map { it.map { l -> l.lowercase() } })
                } catch (e: Exception) {
                    // Мягкая деградация: при сбое батча стеммим этот чанк локально в Kotlin
                    logger.warn("Batch lemmatize failed, falling back: ${e.message}")
                    result.addAll(chunk.map { text ->
                        lemmatizer.stemFallback(text.split(" ").filter { it.isNotBlank() }).map { it.lemma }
                    })
                }
                progressReporter.update(ProgressEvent(
                    "Лемматизация: ${result.size}/$total", result.size, total,
                ))
            }
            result
        } else {
            // Fallback-режим: лемматизация (стемминг) целиком в Kotlin.
            // Прогресс обновляется чанками той же величины, что и в режиме sidecar:
            // без этого экран замирал на последнем сообщении шага 1 до конца этапа.
            val result = mutableListOf<List<String>>()
            for (chunk in textsForLemma.chunked(BATCH_SIZE)) {
                result.addAll(
                    chunk.map { text ->
                        lemmatizer.stemFallback(text.split(" ").filter { it.isNotBlank() })
                            .map { it.lemma }
                    },
                )
                progressReporter.update(ProgressEvent(
                    "Лемматизация: ${result.size}/$total", result.size, total,
                ))
            }
            result
        }

        // Сохраняем леммы (как JSON) в processed_texts
        for ((i, cp) in cleaned.withIndex()) {
            processedTextDao.insert(
                postId = cp.postId,
                lemmasJson = Json.encodeToString(allLemmas[i]),
                language = cp.langResult.language,
                cleanText = cp.cleanText,
            )
        }

        // ── Шаг 3: тональность только для постов окна CURRENT ──
        // BASELINE-посты пропускаем (sentiment для фонового окна не нужен — экономим ресурсы)
        val currentPosts = cleaned.filter { it.window == "CURRENT" && it.cleanText.isNotBlank() }
        logger.info("Sentiment analysis for ${currentPosts.size} CURRENT posts (skipping ${total - currentPosts.size} BASELINE)")

        if (usePython) {
            // Основной режим: батчевый sentiment через Python sidecar (RuBERT)
            val texts = currentPosts.map { it.cleanText }
            for ((chunkIdx, chunk) in texts.chunked(BATCH_SIZE).withIndex()) {
                try {
                    val scores = nlpService.batchSentiment(chunk)
                    // offset — смещение чанка в исходном списке для сопоставления результатов
                    val offset = chunkIdx * BATCH_SIZE
                    for ((j, score) in scores.withIndex()) {
                        sentimentResultDao.insert(
                            entityType = SentimentEntityType.POST,
                            entityId = currentPosts[offset + j].postId,
                            sentiment = score.label.uppercase(),
                            score = score.score,
                            method = "python_rubert",
                            probabilities = score.probabilities,
                        )
                    }
                } catch (e: Exception) {
                    // Сбой батча: записываем нейтральную метку как индикатор ошибки
                    logger.warn("Batch sentiment failed: ${e.message}")
                    val offset = chunkIdx * BATCH_SIZE
                    for (j in chunk.indices) {
                        sentimentResultDao.insert(
                            entityType = SentimentEntityType.POST,
                            entityId = currentPosts[offset + j].postId,
                            sentiment = "NEUTRAL", score = 0.5f, method = "fallback_error",
                        )
                    }
                }
                val done = ((chunkIdx + 1) * BATCH_SIZE).coerceAtMost(currentPosts.size)
                progressReporter.update(ProgressEvent(
                    "Сентимент постов: $done/${currentPosts.size}", done, currentPosts.size,
                ))
            }
        } else {
            // Fallback-режим: поштучный словарный sentiment в Kotlin.
            // Прогресс отчитывается чанками — иначе экран стоит до конца этапа.
            for ((index, cp) in currentPosts.withIndex()) {
                try {
                    val s = nlpService.scoreSentiment(cp.cleanText)
                    sentimentResultDao.insert(
                        entityType = SentimentEntityType.POST,
                        entityId = cp.postId,
                        sentiment = s.label.uppercase(),
                        score = s.score,
                        method = "kotlin_rusentilex",
                        probabilities = s.probabilities,
                    )
                } catch (_: Exception) {
                    sentimentResultDao.insert(
                        entityType = SentimentEntityType.POST,
                        entityId = cp.postId,
                        sentiment = "NEUTRAL",
                        score = 0.5f,
                        method = "fallback_error",
                    )
                }
                if ((index + 1) % BATCH_SIZE == 0 || index == currentPosts.size - 1) {
                    progressReporter.update(ProgressEvent(
                        "Сентимент постов: ${index + 1}/${currentPosts.size}",
                        index + 1, currentPosts.size,
                    ))
                }
            }
        }

        // ── Шаг 4: тональность комментариев ──
        val comments = commentDao.findBySession(sessionId)
        // Берём только комментарии с непустым текстом
        val commentsWithText = comments.filter { (it[Comments.text] ?: "").isNotBlank() }
        logger.info("Sentiment analysis for ${commentsWithText.size} comments")

        if (usePython && commentsWithText.isNotEmpty()) {
            // Основной режим: батчевый sentiment комментариев через Python sidecar
            val texts = commentsWithText.map { it[Comments.text] ?: "" }
            for ((chunkIdx, chunk) in texts.chunked(BATCH_SIZE).withIndex()) {
                try {
                    val scores = nlpService.batchSentiment(chunk)
                    val offset = chunkIdx * BATCH_SIZE
                    for ((j, score) in scores.withIndex()) {
                        sentimentResultDao.insert(
                            entityType = SentimentEntityType.COMMENT,
                            entityId = commentsWithText[offset + j][Comments.id].value,
                            sentiment = score.label.uppercase(),
                            score = score.score,
                            method = "python_rubert",
                            probabilities = score.probabilities,
                        )
                    }
                } catch (e: Exception) {
                    // Сбой батча — нейтральная метка-индикатор ошибки
                    logger.warn("Batch comment sentiment failed: ${e.message}")
                    val offset = chunkIdx * BATCH_SIZE
                    for (j in chunk.indices) {
                        // Запасная запись сама по себе может упасть (например, строка уже
                        // существует). Гасим её отдельно: иначе исключение вышло бы из
                        // execute() и обрушило всю сессию на стадии препроцессинга.
                        try {
                            sentimentResultDao.insert(
                                entityType = SentimentEntityType.COMMENT,
                                entityId = commentsWithText[offset + j][Comments.id].value,
                                sentiment = "NEUTRAL", score = 0.5f, method = "fallback_error",
                            )
                        } catch (inner: Exception) {
                            logger.warn("Fallback comment sentiment insert failed: ${inner.message}")
                        }
                    }
                }
                val done = ((chunkIdx + 1) * BATCH_SIZE).coerceAtMost(commentsWithText.size)
                progressReporter.update(ProgressEvent(
                    "Сентимент комментариев: $done/${commentsWithText.size}", done, commentsWithText.size,
                ))
            }
        } else {
            // Fallback-режим: поштучный словарный sentiment комментариев.
            // Прогресс отчитывается чанками — иначе экран стоит до конца этапа.
            for ((index, comment) in commentsWithText.withIndex()) {
                val cid = comment[Comments.id].value
                val text = comment[Comments.text] ?: ""
                try {
                    val s = nlpService.scoreSentiment(text)
                    sentimentResultDao.insert(
                        entityType = SentimentEntityType.COMMENT,
                        entityId = cid,
                        sentiment = s.label.uppercase(),
                        score = s.score,
                        method = "kotlin_rusentilex",
                        probabilities = s.probabilities,
                    )
                } catch (_: Exception) {
                    // Запасная запись гасится отдельно — см. комментарий в батчевой ветке.
                    try {
                        sentimentResultDao.insert(
                            entityType = SentimentEntityType.COMMENT,
                            entityId = cid,
                            sentiment = "NEUTRAL",
                            score = 0.5f,
                            method = "fallback_error",
                        )
                    } catch (inner: Exception) {
                        logger.warn("Fallback comment sentiment insert failed: ${inner.message}")
                    }
                }
            }
        }

        logger.event(AppEvent.PREPROCESSING_COMPLETED, mapOf(
            "session_id" to sessionId,
            "posts_processed" to total,
            "current_sentiment" to currentPosts.size,
            "comments_sentiment" to commentsWithText.size,
        ))
    }
}
