/*
 * НАЗНАЧЕНИЕ
 * Исполнитель этапа 8 пайплайна (см. docs/algorithm.md): оценка неопределённости
 * через бутстрап. Для каждого автора сессии строит доверительные интервалы тех
 * оценок, что вычисляются по выборке постов/комментариев, и сохраняет их в БД.
 *
 * ЧТО ВНУТРИ
 * Класс InferenceExecutor (реализует StageExecutor.execute). Внутри execute() —
 * последовательная обработка авторов и по каждому шесть веток бутстрапа:
 *   - ER_bg   (one-level) — engagement rate по фоновым постам;
 *   - ER_top  (one-level) — engagement rate по тематическим постам;
 *   - Reach   (one-level) — суммарный охват (просмотры) тематических постов;
 *   - Pos_a   (one-level, distribution) — тональность постов автора;
 *   - Resp_a  (TWO-LEVEL) — тональность комментариев (кластеры по постам).
 * Точечные оценки (Aud, Age, TopVol, TopFocus) бутстрапом НЕ покрываются —
 * у них ci_lo/ci_hi = NULL.
 *
 * МЕТОД
 * One-level бутстрап B=1000 (OneLevelBootstrap), two-level 300×100
 * (TwoLevelBootstrap) только для Resp_a из-за кластерной структуры комментариев.
 * Соответствует Приложению Е.3 диплома.
 *
 * БИБЛИОТЕКИ / ФРЕЙМВОРКИ
 * Exposed ORM — доступ к данным через DAO; suspend/корутины — асинхронное
 * выполнение и распараллеливание бутстрапа; собственная система Logger/AppEvent
 * и ProgressReporter — журналирование и прогресс UI.
 *
 * СВЯЗИ
 * Читает посты/комментарии/тональность/оценки через DAO, пишет интервалы в
 * bootstrap_intervals (BootstrapIntervalDao.upsert). Вызывает OneLevelBootstrap
 * и TwoLevelBootstrap. Регистрируется в оркестраторе (PipelineWiring) как стадия 8.
 */
package com.example.lomanalyzer.analysis.inference

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.*
import com.example.lomanalyzer.storage.tables.*

/**
 * Этап 8 алгоритма (диплом 2.1.1): доверительные интервалы (CI) бутстрапа для
 * оценок, вычисляемых по выборке.
 *
 * One-level (B=1000): ER_bg, ER_top, Reach, Pos_a.
 * Two-level (300×100): только Resp_a (кластеризация по постам).
 * Точечные оценки (Aud, Age, TopVol, TopFocus): бутстрапа НЕТ — ci_lo/ci_hi = NULL.
 *
 * @see Подраздел_2_1_5_бутстрап.md
 * @see Приложение_Е.3
 */
class InferenceExecutor(
    private val postDao: PostDao,
    private val commentDao: CommentDao,
    private val sentimentResultDao: SentimentResultDao,
    private val lomScoreDao: LomScoreDao,
    private val authorDao: AuthorDao,
    private val bootstrapIntervalDao: BootstrapIntervalDao,
    private val linkDao: LinkDao,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) : StageExecutor {

    /**
     * Запускает бутстрап для всех авторов сессии и пишет CI в bootstrap_intervals.
     * @param sessionId идентификатор сессии анализа.
     * @param stage текущая стадия пайплайна (передаётся оркестратором).
     */
    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        // Без посчитанных оценок (этап 7) бутстрапить нечего — выходим
        val scores = lomScoreDao.findBySession(sessionId)
        if (scores.isEmpty()) {
            logger.info("No scores for session #$sessionId, skipping bootstrap")
            return
        }

        // Загружаем посты сессии и делим по окнам: текущее (тема) и фоновое (baseline)
        val allPosts = postDao.findBySession(sessionId)
        val currentPosts = allPosts.filter { it[Posts.window] == "CURRENT" }
        val baselinePosts = allPosts.filter { it[Posts.window] == "BASELINE" }
        // Из текущего окна оставляем только тематически релевантные посты (после фильтра этапа 6)
        val topicPosts = currentPosts.filter { it[Posts.isTopicRelevant] == true }
        val sentimentMap = sentimentResultDao.findAllAsMap()  // карта id-сущности -> метка тональности
        val allComments = commentDao.findBySession(sessionId)

        val total = scores.size
        var processed = 0

        for (scoreRow in scores) {
            // Сопоставляем оценку автору сессии; если связи нет — пропускаем
            val authorId = scoreRow[LomScores.authorId].value
            val author = linkDao.getAuthorsForSession(sessionId)
                .firstOrNull { it[SessionAuthors.authorId].value == authorId }
            if (author == null) { processed++; continue }

            val authorRow = authorDao.findById(authorId) ?: continue
            val vkId = authorRow[Authors.vkId]  // VK-идентификатор автора для фильтрации его постов

            val followers = scoreRow[LomScores.followersCount] ?: 0  // F — число подписчиков (знаменатель ER)
            // Разбиваем посты автора на тематические и фоновые по его vkId
            val authorTopicPosts = topicPosts.filter { it[Posts.fromId] == vkId }
            val authorBaselinePosts = baselinePosts.filter { it[Posts.fromId] == vkId }

            // ── ER_bg bootstrap (one-level): engagement rate по фоновым постам ──
            // Нужно >=2 постов (иначе бутстрап невозможен) и F>0 (иначе деление на ноль)
            if (authorBaselinePosts.size >= 2 && followers > 0) {
                // Значение на пост: (лайки+комментарии+репосты)/F — доля вовлечённости
                val bgValues = authorBaselinePosts.map {
                    (it[Posts.likes] + it[Posts.comments] + it[Posts.reposts]).toDouble() / followers
                }
                // Статистика ER — среднее по постам; бутстрапим её CI
                val ci = OneLevelBootstrap.bootstrap(bgValues, { sample -> sample.average() })
                // Сохраняем CI в БД (upsert по ключу сессия+автор+метрика)
                if (ci != null) {
                    bootstrapIntervalDao.upsert(sessionId, authorId, "er_bg",
                        ci.ciLo.toFloat(), ci.ciHi.toFloat(), ci.procedureType, ci.iterations)
                }
            }

            // ── ER_top bootstrap (one-level): engagement rate по тематическим постам ──
            if (authorTopicPosts.size >= 2 && followers > 0) {
                // Та же формула ER, но по выборке тематических постов
                val topValues = authorTopicPosts.map {
                    (it[Posts.likes] + it[Posts.comments] + it[Posts.reposts]).toDouble() / followers
                }
                val ci = OneLevelBootstrap.bootstrap(topValues, { sample -> sample.average() })
                if (ci != null) {
                    bootstrapIntervalDao.upsert(sessionId, authorId, "er_top",
                        ci.ciLo.toFloat(), ci.ciHi.toFloat(), ci.procedureType, ci.iterations)
                }
            }

            // ── Reach bootstrap (one-level): суммарный охват тематических постов ──
            if (authorTopicPosts.size >= 2) {
                val reachValues = authorTopicPosts.map {
                    val views = it[Posts.views]
                    // Если просмотры неизвестны (0/NULL) — подставляем F как консервативную оценку охвата
                    if (views != null && views > 0) views.toDouble() else followers.toDouble()
                }
                // Статистика Reach — СУММА просмотров (а не среднее), отсюда sample.sum()
                val ci = OneLevelBootstrap.bootstrap(reachValues, { sample -> sample.sum() })
                if (ci != null) {
                    bootstrapIntervalDao.upsert(sessionId, authorId, "reach",
                        ci.ciLo.toFloat(), ci.ciHi.toFloat(), ci.procedureType, ci.iterations)
                }
            }

            // ── Pos_a bootstrap (one-level, distribution): тональность постов автора ──
            if (authorTopicPosts.size >= 2) {
                // Берём метки тональности тех постов, для которых она вычислена
                val sentLabels = authorTopicPosts.mapNotNull {
                    sentimentMap[it[Posts.id].value]
                }
                if (sentLabels.size >= 2) {
                    // Бутстрап распределения долей p+/p- по постам
                    val distCi = OneLevelBootstrap.bootstrapDistribution(sentLabels)
                    if (distCi != null) {
                        bootstrapIntervalDao.upsert(sessionId, authorId, "pos_positive",
                            distCi.positiveCi.ciLo.toFloat(), distCi.positiveCi.ciHi.toFloat(),
                            "one_level", distCi.positiveCi.iterations)
                        bootstrapIntervalDao.upsert(sessionId, authorId, "pos_negative",
                            distCi.negativeCi.ciLo.toFloat(), distCi.negativeCi.ciHi.toFloat(),
                            "one_level", distCi.negativeCi.iterations)
                    }
                }
            }

            // ── Resp_a bootstrap (ДВУХУРОВНЕВЫЙ — кластеризация по постам) ──
            if (authorTopicPosts.size >= 2) {
                // Формируем кластеры: на каждый тематический пост — список меток тональности его комментариев
                val clusters = authorTopicPosts.map { post ->
                    val postDbId = post[Posts.id].value
                    val postComments = allComments.filter { it[Comments.postId].value == postDbId }
                    postComments.mapNotNull { sentimentMap[it[Comments.id].value] }
                }
                // Оставляем только посты, под которыми есть комментарии с тональностью
                val nonEmptyClusters = clusters.filter { it.isNotEmpty() }
                if (nonEmptyClusters.size >= 2) {
                    // Двухуровневый бутстрап: внешний ресэмпл постов + внутренний ресэмпл комментариев
                    val distCi = TwoLevelBootstrap.bootstrap(nonEmptyClusters)
                    if (distCi != null) {
                        bootstrapIntervalDao.upsert(sessionId, authorId, "resp_positive",
                            distCi.positiveCi.ciLo.toFloat(), distCi.positiveCi.ciHi.toFloat(),
                            "two_level", distCi.positiveCi.iterations)
                        bootstrapIntervalDao.upsert(sessionId, authorId, "resp_negative",
                            distCi.negativeCi.ciLo.toFloat(), distCi.negativeCi.ciHi.toFloat(),
                            "two_level", distCi.negativeCi.iterations)
                    }
                }
            }

            processed++
            // Обновляем прогресс в UI каждые 20 авторов и на последнем
            if (processed % 20 == 0 || processed == total) {
                progressReporter.update(ProgressEvent(
                    stage = "Бутстрап: $processed/$total авторов",
                    completedItems = processed,
                    totalItems = total,
                ))
            }
        }

        // Журналируем завершение этапа бутстрапа с числом обработанных авторов
        logger.event(AppEvent.BOOTSTRAP_COMPLETED, mapOf(
            "session_id" to sessionId,
            "authors_processed" to processed,
        ))
    }
}
