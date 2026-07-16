/*
 * НАЗНАЧЕНИЕ
 * Оркестратор дедупликации постов (этап обработки, см. docs/algorithm.md).
 * Запускает два этапа: Stage 1 — точные дубликаты по SHA-256 (ExactHasher),
 * Stage 2 — near-дубликаты по нормализованному Левенштейну (NormalizedLevenshtein),
 * и записывает обнаруженные группы дубликатов в БД.
 *
 * ЧТО ВНУТРИ
 * class DedupPipeline (реализует StageExecutor):
 * - execute: подготовка входа (устранение дублей BASELINE/CURRENT) и запуск этапов.
 * - runStage1: точная дедупликация по хешу.
 * - runStage2: попарное сравнение кандидатов по Левенштейну.
 * - buildLemmaCache/comparePair/recordNearDup/reportProgress: вспомогательные шаги.
 *
 * МЕТОД
 * Stage 1: группировка по SHA-256 нормализованного текста, лидер группы — самый
 * ранний пост, остальные помечаются как EXACT-копии. Stage 2: среди пригодных и
 * не помеченных копиями постов выполняется O(n^2) попарное сравнение в пределах
 * временного окна; при сходстве >= 0.90 пара пишется как NEAR_DUPLICATE.
 *
 * ФРЕЙМВОРКИ / БИБЛИОТЕКИ
 * - Exposed ORM (через DAO) — чтение постов/лемм, запись групп дубликатов.
 * - kotlinx.serialization (Json) — декодирование сохранённых лемм.
 * - StageExecutor/ProgressReporter — интеграция в пайплайн и прогресс.
 * - Logger/AppEvent — структурированные события дедупликации.
 *
 * СВЯЗИ
 * Результат (DedupGroups) использует OriginalityExecutor для пометки DETECTED_COPY.
 */
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

/**
 * Исполнитель этапа дедупликации.
 * @param postDao доступ к постам сессии.
 * @param processedTextDao доступ к сохранённым леммам постов.
 * @param dedupGroupDao запись обнаруженных групп дубликатов.
 * @param levenshtein детектор near-дубликатов (порог 0.90, окно ±72 ч).
 * @param progressReporter репортёр прогресса для UI.
 * @param logger логгер событий.
 */
class DedupPipeline(
    private val postDao: PostDao,
    private val processedTextDao: ProcessedTextDao,
    private val dedupGroupDao: DedupGroupDao,
    private val levenshtein: NormalizedLevenshtein,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) : StageExecutor {

    /**
     * Запускает дедупликацию для сессии: подготовка входа, Stage 1, Stage 2.
     * @param sessionId идентификатор сессии анализа.
     * @param stage текущая стадия пайплайна.
     */
    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        val allPosts = postDao.findBySession(sessionId)

        // When the same VK post exists in both BASELINE and CURRENT windows,
        // keep only one copy for dedup (prefer CURRENT) to avoid false matches.
        // Один и тот же VK-пост может попасть в оба окна (BASELINE и CURRENT) —
        // оставляем для дедупликации только первое вхождение по ключу (vkId, ownerId),
        // чтобы не получить ложные «дубликаты» одного и того же поста
        val seen = mutableSetOf<Long>()
        val dedupInput = allPosts.filter { row ->
            // Композитный ключ поста: vkId (со сдвигом) + ownerId; add() вернёт false для повторов
            val key = row[Posts.vkId].toLong() * 1_000_000_000 + row[Posts.ownerId]
            seen.add(key)
        }

        // Проецируем строки БД в облегчённые HashablePost для алгоритмов дедупликации
        val hashablePosts = dedupInput.map { row ->
            HashablePost(
                postId = row[Posts.id].value,
                fromId = row[Posts.fromId],
                publishedAt = row[Posts.publishedAt],
                cleanText = row[Posts.textClean] ?: row[Posts.text] ?: "",
                ownTextLength = row[Posts.ownTextLength],
                isTopicRelevant = row[Posts.isTopicRelevant],
            )
        }

        // Stage 1 возвращает id точных копий, которые исключаются из Stage 2
        val detectedCopyIds = runStage1(sessionId, hashablePosts)
        runStage2(sessionId, hashablePosts, detectedCopyIds)
    }

    /**
     * Stage 1: точная дедупликация по SHA-256. Для каждой группы одинаковых
     * хешей лидер (самый ранний пост) сохраняется, остальные записываются как
     * EXACT-дубликаты.
     * @return множество id постов, признанных точными копиями.
     */
    private fun runStage1(sessionId: Int, posts: List<HashablePost>): Set<Int> {
        val detectedCopyIds = mutableSetOf<Int>()
        // Группируем посты по хешу нормализованного текста
        val exactGroups = ExactHasher.findDuplicateGroups(posts)

        for ((_, group) in exactGroups) {
            // Лидер группы — первый (самый ранний) пост
            val leader = group.first()
            // Остальные посты группы — точные копии лидера
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

    /**
     * Stage 2: near-дедупликация по нормализованному Левенштейну. Среди пригодных
     * и не помеченных точными копиями постов выполняется попарное сравнение
     * (O(n^2)); пары со сходством >= порога в пределах окна записываются как
     * NEAR_DUPLICATE.
     * @param excludeIds id постов, уже признанных точными копиями (Stage 1).
     */
    @Suppress("LoopWithTooManyJumpStatements")
    private suspend fun runStage2(sessionId: Int, posts: List<HashablePost>, excludeIds: Set<Int>) {
        // Кандидаты: достаточно длинные, тематические и не точные копии
        val candidates = posts.filter {
            levenshtein.isEligible(it.ownTextLength, it.isTopicRelevant) && it.postId !in excludeIds
        }

        // Заранее подгружаем леммы всех кандидатов в кэш (чтобы не ходить в БД в цикле)
        val lemmaCache = buildLemmaCache(candidates)
        // Множество уже записанных пар near-дубликатов (для дедупликации самих пар)
        val nearDupPairs = mutableSetOf<Pair<Int, Int>>()

        // Попарное сравнение всех кандидатов (i < j)
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                // Сходство или null, если пара не near-дубликат (вне окна/ниже порога)
                val sim = comparePair(candidates[i], candidates[j], lemmaCache) ?: continue
                recordNearDup(sessionId, candidates[i], candidates[j], sim, nearDupPairs)
            }
            reportProgress(i, candidates.size)
        }

        logger.event(AppEvent.DEDUP_STAGE2_COMPLETED, mapOf(
            "session_id" to sessionId, "near_dup_pairs" to nearDupPairs.size,
        ))
    }

    /**
     * Загружает леммы всех кандидатов в память (кэш postId -> леммы).
     * Десериализует lemmasJson из БД; при отсутствии — пустой список.
     */
    private fun buildLemmaCache(candidates: List<HashablePost>): Map<Int, List<String>> {
        val cache = mutableMapOf<Int, List<String>>()
        for (post in candidates) {
            val processed = processedTextDao.findByPostId(post.postId)
            val json = processed?.get(ProcessedTexts.lemmasJson)
            cache[post.postId] = if (json != null) Json.decodeFromString(json) else emptyList()
        }
        return cache
    }

    /**
     * Сравнивает пару постов по Левенштейну с учётом временного окна.
     * @return значение сходства, если пара — near-дубликат; иначе null.
     */
    @Suppress("ReturnCount")
    private fun comparePair(a: HashablePost, b: HashablePost, cache: Map<Int, List<String>>): Float? {
        val lemmasA = cache[a.postId] ?: return null
        val lemmasB = cache[b.postId] ?: return null
        val (isDup, sim) = levenshtein.isNearDuplicate(lemmasA, lemmasB, a.publishedAt, b.publishedAt)
        return if (isDup) sim else null
    }

    /**
     * Записывает пару near-дубликатов в БД (если ещё не записана).
     * Лидером (оригиналом) выбирается более ранний по времени публикации пост.
     * @param seen множество уже учтённых пар (по упорядоченным id) для защиты от повторов.
     */
    private fun recordNearDup(
        sessionId: Int,
        a: HashablePost,
        b: HashablePost,
        sim: Float,
        seen: MutableSet<Pair<Int, Int>>,
    ) {
        // Лидер = более ранний пост, dup = более поздний
        val (leader, dup) = if (a.publishedAt <= b.publishedAt) a to b else b to a
        // Нормализованный ключ пары (min,max) для исключения повторной записи
        val key = minOf(leader.postId, dup.postId) to maxOf(leader.postId, dup.postId)
        if (key !in seen) {
            seen.add(key)
            dedupGroupDao.insert(sessionId, leader.postId, dup.postId, sim, "NEAR_DUPLICATE")
        }
    }

    /** Обновляет прогресс Stage 2 каждые 50 кандидатов и на последнем. */
    private suspend fun reportProgress(index: Int, total: Int) {
        if ((index + 1) % 50 == 0 || index == total - 1) {
            progressReporter.update(ProgressEvent("DEDUPLICATION", index + 1, total))
        }
    }
}
