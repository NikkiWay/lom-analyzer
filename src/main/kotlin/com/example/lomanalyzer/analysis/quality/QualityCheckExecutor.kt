/*
 * НАЗНАЧЕНИЕ
 * Исполнитель этапа 10 пайплайна (см. docs/algorithm.md; диплом 2.1.5, 2.2.8):
 * проверка качества завершённой сессии анализа. Делает две вещи:
 *  1) рассчитывает индикатор достаточности данных по каждому автору и пишет его
 *     в author_role.sufficiency;
 *  2) агрегирует индикаторы качества сессии (4 основных + технические) и сохраняет
 *     их как события сессии.
 *
 * ЧТО ВНУТРИ
 *  QualityCheckExecutor — реализация StageExecutor с одним методом execute().
 *
 * МЕТОД
 *  Шаг 1: для каждого автора по числу тематических постов/комментариев и средней
 *  ширине CI определяется уровень достаточности (RELIABLE/PRELIMINARY/UNRELIABLE)
 *  через DataSufficiencyIndicator (диплом 2.1.5).
 *  Шаг 2: из БД собираются агрегаты (полнота, качество фильтрации, покрытие
 *  комментариями, дедуп, ширина CI, закрытые аккаунты, ошибки API) и передаются в
 *  SessionQualityEvaluator.
 *
 * БИБЛИОТЕКИ / СВЯЗИ
 *  Exposed DAO (storage.dao/tables) — доступ к данным сессии (изоляция модулей
 *  через SQLite, см. architecture.md). SessionEventService/Logger — запись событий
 *  и логирование. ProgressReporter — обновление прогресса в UI. Корутины (suspend).
 */
package com.example.lomanalyzer.analysis.quality

import com.example.lomanalyzer.analysis.sufficiency.DataSufficiencyIndicator
import com.example.lomanalyzer.core.DataSufficiency
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.*
import com.example.lomanalyzer.storage.tables.*
import com.example.lomanalyzer.vk.SessionEventService

/**
 * Этап 10 алгоритма (диплом 2.1.5, 2.2.8):
 * 1. Индикатор достаточности данных по каждому автору
 * 2. Индикаторы качества сессии (4 основных + технические)
 */
class QualityCheckExecutor(
    private val lomScoreDao: LomScoreDao,
    private val bootstrapIntervalDao: BootstrapIntervalDao,
    private val compositeDao: CompositeDao,
    private val postDao: PostDao,
    private val commentDao: CommentDao,
    private val linkDao: LinkDao,
    private val authorDao: AuthorDao,
    private val sessionEventDao: SessionEventDao,
    private val sessionQualityEvaluator: SessionQualityEvaluator,
    private val sessionEventService: SessionEventService,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) : StageExecutor {

    companion object {
        /**
         * Сколько комментариев должно быть у тематического поста, чтобы считать
         * его покрытым откликом аудитории. Ниже этого порога отклик (ось 4) по
         * посту статистически не интерпретируем.
         */
        private const val MIN_COMMENTS_FOR_COVERAGE = 5L
    }

    /**
     * Выполняет проверку качества для сессии sessionId.
     * @param stage текущая стадия пайплайна (для оркестрации).
     */
    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        // Без оценок проверять нечего — пропускаем этап
        val scores = lomScoreDao.findBySession(sessionId)
        if (scores.isEmpty()) {
            logger.info("No scores for session #$sessionId, skipping quality check")
            return
        }

        // ── Шаг 1: достаточность данных по каждому автору ──
        var reliableCount = 0
        var preliminaryCount = 0
        var unreliableCount = 0

        for (scoreRow in scores) {
            val authorId = scoreRow[LomScores.authorId].value
            val topicPosts = scoreRow[LomScores.topicPostCount]
            val comments = scoreRow[LomScores.commentCount]

            // Средняя ширина доверительных интервалов автора из таблицы bootstrap_interval
            val intervals = bootstrapIntervalDao.findBySessionAndAuthor(sessionId, authorId)
            val avgCiWidth = if (intervals.isNotEmpty()) {
                intervals.map {
                    (it[BootstrapIntervals.ciHi] - it[BootstrapIntervals.ciLo]).toDouble()
                }.average()
            } else null

            // Уровень достаточности данных по объёму материала и точности оценок (диплом 2.1.5)
            val sufficiency = DataSufficiencyIndicator.evaluate(topicPosts, comments, avgCiWidth)

            // Обновляем author_role.sufficiency, сохраняя уже вычисленные роль/атрибуты без изменений
            compositeDao.upsertRole(
                sessionId, authorId,
                baseRole = compositeDao.findRolesBySession(sessionId)
                    .firstOrNull { it[AuthorRoles.authorId].value == authorId }
                    ?.get(AuthorRoles.baseRole) ?: "BACKGROUND_AUTHOR",
                positionAttr = compositeDao.findRolesBySession(sessionId)
                    .firstOrNull { it[AuthorRoles.authorId].value == authorId }
                    ?.get(AuthorRoles.positionAttr) ?: "NEUTRAL",
                responseAttr = compositeDao.findRolesBySession(sessionId)
                    .firstOrNull { it[AuthorRoles.authorId].value == authorId }
                    ?.get(AuthorRoles.responseAttr) ?: "MIXED",
                sufficiency = sufficiency.name,
            )

            // Накапливаем счётчики по категориям достаточности
            when (sufficiency) {
                DataSufficiency.RELIABLE -> reliableCount++
                DataSufficiency.PRELIMINARY -> preliminaryCount++
                DataSufficiency.UNRELIABLE -> unreliableCount++
            }
        }

        // Доли надёжных/ненадёжных авторов — входят в основной индикатор качества
        val totalAuthors = scores.size
        val reliableRatio = reliableCount.toFloat() / totalAuthors
        val unreliableRatio = unreliableCount.toFloat() / totalAuthors

        sessionEventService.logInfo(sessionId,
            "Достаточность данных: надёжна=$reliableCount, предварительна=$preliminaryCount, " +
                "ненадёжна=$unreliableCount из $totalAuthors")

        // ── Шаг 2: индикаторы качества сессии ──
        val allPosts = postDao.findBySession(sessionId)
        // Тематические посты текущего окна (CURRENT) — для покрытия комментариями
        val topicPosts = allPosts.filter { it[Posts.isTopicRelevant] == true && it[Posts.window] == "CURRENT" }

        // Полнота сбора: пока принимается за 1.0 (весь запрошенный материал собран)
        val collectionCompleteness = 1.0f

        // Качество фильтрации: доля постов с уверенным комбинированным тематическим score (>=0.5)
        val confidentPosts = allPosts.count {
            val combined = it[Posts.topicScoreCombined]
            combined != null && combined >= 0.5f
        }
        val totalScoredPosts = allPosts.count { it[Posts.topicScoreCombined] != null }
        val topicFilteringQuality = if (totalScoredPosts > 0)
            confidentPosts.toFloat() / totalScoredPosts else 0f

        // Покрытие комментариями: доля тематических постов, имеющих >= 5 комментариев.
        // Счётчики берём одним группированным запросом: раньше здесь был вызов
        // countByPost на каждый пост, и каждый — полное сканирование comment.
        val commentCountsByPost = commentDao.countBySessionGroupedByPost(sessionId)
        val postsWithComments = topicPosts.count { post ->
            // Поста нет в карте — комментариев нет вовсе
            (commentCountsByPost[post[Posts.id].value] ?: 0L) >= MIN_COMMENTS_FOR_COVERAGE
        }
        val commentCoverage = if (topicPosts.isNotEmpty())
            postsWithComments.toFloat() / topicPosts.size else 0f

        // Эффективность дедупликации: доля постов, не помеченных как обнаруженная копия
        val totalPostsBeforeDedup = allPosts.size
        val uniquePosts = allPosts.count { it[Posts.originalityType] != "DETECTED_COPY" }
        val dedupEfficiency = if (totalPostsBeforeDedup > 0)
            uniquePosts.toFloat() / totalPostsBeforeDedup else 1.0f

        // Средняя ширина доверительных интервалов по всей сессии
        val allIntervals = bootstrapIntervalDao.findBySession(sessionId)
        val avgCiWidth = if (allIntervals.isNotEmpty()) {
            allIntervals.map { (it[BootstrapIntervals.ciHi] - it[BootstrapIntervals.ciLo]) }.average().toFloat()
        } else 0f

        // Доля закрытых аккаунтов среди авторов сессии. Профили читаем одной
        // выборкой: точечный findById на каждого автора давал отдельный запрос
        // к БД в цикле (N+1).
        val sessionAuthors = linkDao.getAuthorsForSession(sessionId)
        val authorsById = authorDao.findAll().associateBy { it[Authors.id].value }
        val closedCount = sessionAuthors.count { sa ->
            authorsById[sa[SessionAuthors.authorId].value]?.get(Authors.isClosed) == true
        }
        val closedRatio = if (sessionAuthors.isNotEmpty())
            closedCount.toFloat() / sessionAuthors.size else 0f

        // Частота ошибок API: доля API_ERROR от общего числа API_REQUEST (по событиям сессии)
        val events = sessionEventDao.findBySession(sessionId)
        val apiRequests = events.count { it[SessionEvents.eventType] == "API_REQUEST" }
        val apiErrors = events.count { it[SessionEvents.eventType] == "API_ERROR" }
        val apiRetryRate = if (apiRequests > 0) apiErrors.toFloat() / apiRequests else 0f

        val qualityResult = sessionQualityEvaluator.evaluate(QualityInput(
            collectionCompleteness = collectionCompleteness,
            topicFilteringQuality = topicFilteringQuality,
            commentCoverage = commentCoverage,
            reliableRatio = reliableRatio,
            unreliableRatio = unreliableRatio,
            dedupEfficiency = dedupEfficiency,
            avgCiWidth = avgCiWidth,
            closedAccountRatio = closedRatio,
            apiRetryRate = apiRetryRate,
        ))

        // Сохраняем каждый индикатор как событие сессии (тип QUALITY_INDICATOR)
        for (indicator in qualityResult.indicators) {
            sessionEventDao.insert(
                sessionId = sessionId,
                eventType = "QUALITY_INDICATOR",
                message = "${indicator.name}: ${indicator.status.name} (%.3f)".format(indicator.value),
                details = indicator.description,
            )
        }

        sessionEventService.logInfo(sessionId,
            "Качество сессии: ${qualityResult.overallStatus.name} " +
                "(${qualityResult.indicators.count { it.status == com.example.lomanalyzer.core.QualityStatus.PASSED }}/" +
                "${qualityResult.indicators.size} пройдено)")

        progressReporter.update(ProgressEvent(
            stage = "Качество сессии: ${qualityResult.overallStatus.name}",
            completedItems = 1, totalItems = 1, finished = true,
        ))

        logger.info("Quality check complete: ${qualityResult.overallStatus}")
    }
}
