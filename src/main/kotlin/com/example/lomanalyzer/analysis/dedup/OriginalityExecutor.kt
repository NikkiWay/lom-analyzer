/*
 * НАЗНАЧЕНИЕ
 * Исполнитель классификации оригинальности постов (этап обработки после
 * дедупликации, см. docs/algorithm.md). Для каждого поста сессии вычисляет тип
 * оригинальности и сохраняет его в БД; типы DETECTED_COPY определяются по
 * результатам дедупликации (группы EXACT/NEAR_DUPLICATE).
 *
 * ЧТО ВНУТРИ
 * class OriginalityExecutor (реализует StageExecutor) с методом execute:
 * собирает id обнаруженных копий, классифицирует каждый пост через
 * OriginalityClassifier и пишет результат.
 *
 * ФРЕЙМВОРКИ / БИБЛИОТЕКИ
 * - Exposed ORM (через DAO) — чтение постов и групп дубликатов, запись типа.
 * - StageExecutor — интеграция в оркестрацию пайплайна.
 * - Logger/AppEvent — событие завершения классификации.
 *
 * СВЯЗИ
 * Использует выход DedupPipeline (DedupGroups) и правила OriginalityClassifier.
 */
package com.example.lomanalyzer.analysis.dedup

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.DedupGroupDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.tables.DedupGroups
import com.example.lomanalyzer.storage.tables.Posts

/**
 * Исполнитель этапа классификации оригинальности постов.
 * @param postDao доступ к постам и записи типа оригинальности.
 * @param dedupGroupDao доступ к группам дубликатов (для определения копий).
 * @param logger логгер событий.
 */
class OriginalityExecutor(
    private val postDao: PostDao,
    private val dedupGroupDao: DedupGroupDao,
    private val logger: Logger,
) : StageExecutor {

    /**
     * Классифицирует оригинальность всех постов сессии и сохраняет типы.
     * @param sessionId идентификатор сессии анализа.
     * @param stage текущая стадия пайплайна.
     */
    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        val posts = postDao.findBySession(sessionId)
        val dedupGroups = dedupGroupDao.findBySession(sessionId)

        // Множество id постов-копий: дубликаты из групп EXACT и NEAR_DUPLICATE
        val detectedCopyIds = dedupGroups
            .filter { it[DedupGroups.method] == "EXACT" || it[DedupGroups.method] == "NEAR_DUPLICATE" }
            .map { it[DedupGroups.duplicatePostId].value }
            .toSet()

        var classified = 0
        for (post in posts) {
            val postId = post[Posts.id].value
            // Признаки поста, влияющие на тип оригинальности
            val hasCopyHistory = post[Posts.hasCopyHistory]
            val ownTextLength = post[Posts.ownTextLength]
            val containsMedia = post[Posts.containsMedia]
            // Пост — копия, если он попал в детектированные дубликаты
            val isDetectedCopy = postId in detectedCopyIds

            // Применяем приоритетные правила классификации оригинальности
            val type = OriginalityClassifier.classify(
                hasCopyHistory = hasCopyHistory,
                ownTextLength = ownTextLength,
                containsMedia = containsMedia,
                isDetectedCopy = isDetectedCopy,
            )
            // Сохраняем имя типа в БД
            postDao.updateOriginalityType(postId, type.name)
            classified++
        }

        logger.event(AppEvent.ORIGINALITY_CLASSIFIED, mapOf(
            "session_id" to sessionId,
            "posts_classified" to classified,
        ))
    }
}
