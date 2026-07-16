/*
 * НАЗНАЧЕНИЕ
 * Исполнитель ЭТАПА 7 пайплайна (docs/algorithm.md; диплом 2.1.1): вычисление
 * всех 11 количественных оценок (Приложение Е.4) для каждого автора, имеющего
 * хотя бы один подтверждённый тематический пост. Это оркестратор: он достаёт
 * данные из БД, раскладывает их по 4 осям и делегирует расчёт чистым функциям
 * StructuralScores / TopicScores / PositionScore / ResponseScores.
 *
 * ЧТО ВНУТРИ
 * class ScoringExecutor (реализует StageExecutor):
 *  - execute(sessionId, stage)        — основной проход по авторам сессии;
 *  - postReactions(row)               — (L + C + R) для поста;
 *  - authorAgeDays(row)               — возраст аккаунта в днях.
 *
 * АЛГОРИТМ (на каждого автора)
 *  Ось 1 (структура):  Aud_a, Age_a, ER_a^bg  — StructuralScores.
 *  Ось 2 (тема):       TopVol_a, TopFocus_a, Reach_a — TopicScores.
 *  Ось 3 (позиция):    Pos_a (p+,p0,p-)        — PositionScore.
 *  Ось 4 (отклик):     ER_a^top, Resp_a (q+,q0,q-) — ResponseScores.
 *  Итого 11 оценок (по 3 компоненты в Pos/Resp) сохраняются в LomScores.
 *  Закрытые аккаунты и авторы без тематических постов пропускаются (диплом 2.1.1).
 *
 * БИБЛИОТЕКИ / ФРЕЙМВОРКИ
 *  - Exposed ORM (ResultRow, таблицы Posts/Authors/Comments/...) — доступ к БД;
 *  - Koin — внедряет DAO и сервисы через конструктор;
 *  - корутины (suspend execute) — этап выполняется в пайплайне асинхронно;
 *  - ProgressReporter / Logger — прогресс и события (AppEvent.SCORING_COMPLETED).
 *
 * СВЯЗИ
 *  Читает Posts/Authors/Comments/SentimentResults через DAO, пишет в LomScores.
 *  Запускается оркестратором пайплайна; результат потребляет этап 8 (bootstrap)
 *  и этап 9 (CompositeRolesExecutor).
 */
package com.example.lomanalyzer.analysis.scoring

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.*
import com.example.lomanalyzer.storage.tables.SentimentEntityType
import com.example.lomanalyzer.storage.tables.*

/**
 * Этап 7 алгоритма (диплом 2.1.1): расчёт 11 количественных оценок для каждого
 * автора, имеющего хотя бы один подтверждённый тематический пост.
 *
 * Все формулы строго по Приложению Е.4. Никаких производных/самодельных индексов.
 *
 * @param postDao доступ к постам сессии (тематические/фоновые, окна CURRENT/BASELINE)
 * @param authorDao доступ к профилям авторов (подписчики, дата создания, закрытость)
 * @param commentDao доступ к комментариям под постами
 * @param sentimentResultDao готовые метки тональности постов и комментариев (NLP)
 * @param lomScoreDao запись итоговых 11 оценок (таблица LomScores)
 * @param linkDao связь «сессия ↔ авторы»
 * @param progressReporter отчёт о прогрессе для UI
 * @param logger журналирование событий
 * @see Подраздел_2_1_3_используемые_оценки.md
 */
class ScoringExecutor(
    private val postDao: PostDao,
    private val authorDao: AuthorDao,
    private val commentDao: CommentDao,
    private val sentimentResultDao: SentimentResultDao,
    private val lomScoreDao: LomScoreDao,
    private val linkDao: LinkDao,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) : StageExecutor {

    /**
     * Основной проход этапа: для каждого открытого автора с ≥1 тематическим
     * постом считает 11 оценок по 4 осям и сохраняет их в LomScores.
     * @param sessionId идентификатор сессии анализа
     * @param stage текущий этап пайплайна (для совместимости с интерфейсом)
     */
    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        // Берём всех авторов, привязанных к данной сессии
        val sessionAuthors = linkDao.getAuthorsForSession(sessionId)
        if (sessionAuthors.isEmpty()) {
            logger.info("No authors for session #$sessionId, skipping scoring")
            return
        }

        // Загружаем все данные одним махом, чтобы избежать проблемы N+1 запросов в цикле
        val allPosts = postDao.findBySession(sessionId)
        // Окно CURRENT — посты периода темы; BASELINE — фоновый период (для ER_bg)
        val currentPosts = allPosts.filter { it[Posts.window] == "CURRENT" }
        val baselinePosts = allPosts.filter { it[Posts.window] == "BASELINE" }
        // Карты «id → метка тональности» — отдельно по постам и по комментариям.
        // Единой картой их держать нельзя: post.id и comment.id нумеруются
        // независимо и пересекаются (см. V11__sentiment_result_entity_type.sql).
        val postSentimentMap = sentimentResultDao.findAllAsMap(SentimentEntityType.POST)
        val commentSentimentMap = sentimentResultDao.findAllAsMap(SentimentEntityType.COMMENT)
        val allComments = commentDao.findBySession(sessionId)

        // Тематические посты = окно CURRENT И прошедшие тематический фильтр (is_topic_relevant)
        val topicPosts = currentPosts.filter { it[Posts.isTopicRelevant] == true }

        // Максимальный возраст аккаунта по всем авторам — знаменатель нормировки Age_a (Е.4.1)
        val allAuthors = sessionAuthors.mapNotNull { sa ->
            authorDao.findById(sa[SessionAuthors.authorId].value)
        }
        val maxAgeDays = allAuthors.maxOfOrNull { authorAgeDays(it) } ?: 1L

        val totalAuthors = allAuthors.size
        var processed = 0

        for (author in allAuthors) {
            val authorDbId = author[Authors.id].value
            val vkId = author[Authors.vkId]
            val isClosed = author[Authors.isClosed]
            // Число подписчиков F_a (знаменатель в ER_bg/ER_top); 0, если неизвестно
            val followers = author[Authors.followersCount] ?: 0

            // Закрытые аккаунты исключаются из обработки (диплом 2.1.1)
            if (isClosed) {
                processed++
                continue
            }

            // Разбиваем посты автора по окнам/типам (сопоставление по VK-id автора from_id)
            val authorTopicPosts = topicPosts.filter { it[Posts.fromId] == vkId }
            val authorBaselinePosts = baselinePosts.filter { it[Posts.fromId] == vkId }
            // Нетематические посты периода CURRENT — нужны для знаменателя TopFocus_a
            val authorCurrentNonTopic = currentPosts.filter {
                it[Posts.fromId] == vkId && it[Posts.isTopicRelevant] != true
            }

            val topicCount = authorTopicPosts.size
            if (topicCount == 0) {
                processed++
                continue // Условие включения: у автора должен быть хотя бы один тематический пост
            }

            // === Ось 1: структурное влияние (Е.4.1) ===
            val audScore = StructuralScores.audienceScore(followers)                       // Aud_a = ln(1+F_a)
            val ageScore = StructuralScores.normalizedAccountAge(authorAgeDays(author), maxAgeDays) // Age_a (норм. на максимум)
            // Реакции (L+C+R) по фоновым постам → средний фоновый engagement rate
            val bgReactions = authorBaselinePosts.map { postReactions(it) }
            val erBgScore = StructuralScores.backgroundEngagementRate(bgReactions, followers)        // ER_a^bg

            // === Ось 2: тематическая активность (Е.4.2) ===
            val topVolScore = TopicScores.topicalPostVolume(topicCount)                    // TopVol_a = |T_a|
            val topFocusScore = TopicScores.topicalFocusShare(topicCount, authorCurrentNonTopic.size) // TopFocus_a
            // Охват каждого тем. поста: просмотры V_i, либо размер аудитории F_a как fallback
            val reachValues = authorTopicPosts.map { post ->
                val views = post[Posts.views]
                if (views != null && views > 0) views.toLong()
                else followers.toLong() // fallback: размер аудитории, если просмотры недоступны
            }
            val reachScore = TopicScores.topicalReach(reachValues)                     // Reach_a = Σ V_i

            // === Ось 3: позиция автора (берём метки тональности из заранее загруженной карты) ===
            val postSentiments = authorTopicPosts.mapNotNull { post ->
                postSentimentMap[post[Posts.id].value]
            }
            val posDistribution = PositionScore.authorPositionDistribution(postSentiments)            // Pos_a = (p+,p0,p-)

            // === Ось 4: отклик аудитории (Е.4.4) ===
            // Реакции по тематическим постам → тематический engagement rate
            val topicReactions = authorTopicPosts.map { postReactions(it) }
            val erTopScore = ResponseScores.topicalEngagementRate(topicReactions, followers)   // ER_a^top

            // Resp_a: тональность всех комментариев под тематическими постами автора
            val topicPostIds = authorTopicPosts.map { it[Posts.id].value }.toSet()
            val authorComments = allComments.filter { it[Comments.postId].value in topicPostIds }
            val totalComments = authorComments.size
            // Метка комментария из карты тональностей; при отсутствии считаем NEUTRAL
            val commentSentiments = authorComments.map { comment ->
                commentSentimentMap[comment[Comments.id].value] ?: "NEUTRAL"
            }
            val respDistribution = ResponseScores.audienceResponseDistribution(commentSentiments)     // Resp_a = (q+,q0,q-)

            // === Сохранение всех 11 оценок в LomScores (upsert по (sessionId, authorId)) ===
            lomScoreDao.upsert(
                sessionId = sessionId,
                authorId = authorDbId,
                aud = audScore.toFloat(),
                age = ageScore.toFloat(),
                erBg = erBgScore.toFloat(),
                topVol = topVolScore,
                topFocus = topFocusScore.toFloat(),
                reach = reachScore.toFloat(),
                posPositive = posDistribution.positive.toFloat(),
                posNeutral = posDistribution.neutral.toFloat(),
                posNegative = posDistribution.negative.toFloat(),
                erTop = erTopScore.toFloat(),
                respPositive = respDistribution.positive.toFloat(),
                respNeutral = respDistribution.neutral.toFloat(),
                respNegative = respDistribution.negative.toFloat(),
                bgPostCount = authorBaselinePosts.size,
                topicPostCount = topicCount,
                commentCount = totalComments,
                followersCount = followers,
            )

            processed++
            // Обновляем прогресс батчами по 50 авторов (и обязательно на последнем)
            if (processed % 50 == 0 || processed == totalAuthors) {
                progressReporter.update(ProgressEvent(
                    stage = "Расчёт оценок: $processed/$totalAuthors авторов",
                    completedItems = processed,
                    totalItems = totalAuthors,
                ))
            }
        }

        // Фиксируем завершение этапа в журнале событий
        logger.event(AppEvent.SCORING_COMPLETED, mapOf(
            "session_id" to sessionId,
            "authors_scored" to processed,
        ))
    }

    /** Суммарные реакции поста: лайки + комментарии + репосты (числитель в ER_bg/ER_top). */
    private fun postReactions(post: org.jetbrains.exposed.sql.ResultRow): Int =
        post[Posts.likes] + post[Posts.comments] + post[Posts.reposts]

    /**
     * Возраст аккаунта автора в днях для Age_a.
     * Берёт разницу между «сейчас» и моментом первого наблюдения (firstSeenAt),
     * переводит миллисекунды в дни; отрицательные значения отсекаются до 0.
     */
    private fun authorAgeDays(author: org.jetbrains.exposed.sql.ResultRow): Long {
        // Нет данных о первом появлении → возраст 0
        val firstSeen = author[Authors.firstSeenAt] ?: return 0L
        val now = System.currentTimeMillis()
        // Перевод миллисекунд в дни: / (1000 * 60 * 60 * 24); не допускаем отрицательного возраста
        return ((now - firstSeen) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
    }
}
