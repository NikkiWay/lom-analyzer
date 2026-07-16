/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для постов (таблица posts) — центральная таблица
 * собранного контента. Хранит посты из разных окон сбора (window: фоновое/текущее),
 * метрики вовлечённости (likes/reposts/comments/views), флаги медиа и репоста, а также
 * результаты препроцессинга (этап 3) и тематической фильтрации (этапы 5–6) и тип
 * оригинальности/голос аналитика. Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс PostDao: insert (идемпотентная вставка по ключу сессия+vkId+owner+window),
 * findById/findBySession/findBySessionAndFromId/findBySessionAndWindow (выборки),
 * updateOriginalityType, updateAnalystVote, updateTopicRelevance,
 * updatePostPreprocessing (точечные обновления полей по этапам пайплайна).
 *
 * МЕТОД
 * Тематическая фильтрация двухпроходная: L1 (ключевые слова) и L2 (RuBERT cosine),
 * их оценки и итог хранятся в topicScoreL1/L2/Combined и isTopicRelevant.
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов. java.time.Instant — created_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица Posts (storage/tables). fromId/ownerId — внешние идентификаторы VK.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.Posts
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO постов. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class PostDao(private val db: Database) {
    /**
     * Идемпотентный INSERT поста. Естественный ключ дубликата —
     * (sessionId, vkId, ownerId, window): один и тот же пост в одном окне сбора не
     * дублируется.
     * @param vkId внешний идентификатор поста VK.
     * @param ownerId владелец стены, где размещён пост.
     * @param fromId фактический автор поста.
     * @param publishedAt время публикации (epoch millis).
     * @param window окно сбора (фоновое/текущее).
     * @param ownTextLength длина собственного текста (без репостнутого).
     * @param hasCopyHistory признак того, что пост является репостом.
     * @return id существующей или вновь созданной строки.
     */
    @Suppress("LongParameterList")
    fun insert(
        sessionId: Int,
        vkId: Int,
        ownerId: Int,
        fromId: Int,
        publishedAt: Long,
        text: String? = null,
        window: String,
        ownTextLength: Int = 0,
        likes: Int = 0,
        reposts: Int = 0,
        comments: Int = 0,
        views: Int? = null,
        containsMedia: Boolean = false,
        hasCopyHistory: Boolean = false,
    ): Int = transaction(db) {
        // Пропускаем, если такой пост уже сохранён для этой сессии и окна сбора
        val existing = Posts.selectAll().where {
            (Posts.sessionId eq sessionId) and
                (Posts.vkId eq vkId) and
                (Posts.ownerId eq ownerId) and
                (Posts.window eq window)
        }.singleOrNull()
        // Уже есть — возвращаем id существующей строки
        if (existing != null) return@transaction existing[Posts.id].value

        Posts.insertAndGetId {
            it[Posts.sessionId] = sessionId
            it[Posts.vkId] = vkId
            it[Posts.ownerId] = ownerId
            it[Posts.fromId] = fromId
            it[Posts.publishedAt] = publishedAt
            it[Posts.text] = text
            it[Posts.window] = window
            it[Posts.ownTextLength] = ownTextLength
            it[Posts.likes] = likes
            it[Posts.reposts] = reposts
            it[Posts.comments] = comments
            it[Posts.views] = views
            it[Posts.containsMedia] = containsMedia
            it[Posts.hasCopyHistory] = hasCopyHistory
            it[createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    /**
     * SELECT поста по внутреннему первичному ключу id.
     * @return ResultRow или null.
     */
    fun findById(id: Int): ResultRow? = transaction(db) {
        Posts.selectAll().where { Posts.id eq id }.singleOrNull()
    }

    /**
     * SELECT всех постов сессии, упорядоченных по времени публикации (ASC).
     * @return список ResultRow.
     */
    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        Posts.selectAll().where { Posts.sessionId eq sessionId }
            .orderBy(Posts.publishedAt)
            .toList()
    }

    /**
     * SELECT постов конкретного автора (fromId) в сессии, новые сверху (DESC).
     * @return список ResultRow.
     */
    fun findBySessionAndFromId(sessionId: Int, fromId: Int): List<ResultRow> = transaction(db) {
        Posts.selectAll().where {
            (Posts.sessionId eq sessionId) and (Posts.fromId eq fromId)
        }.orderBy(Posts.publishedAt, SortOrder.DESC).toList()
    }

    /**
     * SELECT постов сессии из конкретного окна сбора (window).
     * @return список ResultRow.
     */
    fun findBySessionAndWindow(sessionId: Int, window: String): List<ResultRow> = transaction(db) {
        Posts.selectAll().where {
            (Posts.sessionId eq sessionId) and (Posts.window eq window)
        }.toList()
    }

    /**
     * UPDATE типа оригинальности поста (результат этапа дедупликации/оригинальности).
     * @param type метка типа (например, ORIGINAL/REPOST/DUPLICATE).
     */
    fun updateOriginalityType(id: Int, type: String) = transaction(db) {
        Posts.update({ Posts.id eq id }) {
            it[originalityType] = type
        }
    }

    /**
     * UPDATE ручного голоса аналитика по посту (подтверждение/отклонение).
     * @param vote true/false или null (голос снят).
     */
    fun updateAnalystVote(id: Int, vote: Boolean?) = transaction(db) {
        Posts.update({ Posts.id eq id }) {
            it[analystVote] = vote
        }
    }

    /**
     * UPDATE результатов тематической фильтрации поста (этапы 5–6).
     * @param relevant признак тематической релевантности.
     * @param l1 оценка прохода L1 (ключевые слова) или NULL.
     * @param l2 оценка прохода L2 (RuBERT cosine) или NULL.
     * @param combined итоговая комбинированная оценка или NULL.
     */
    fun updateTopicRelevance(id: Int, relevant: Boolean, l1: Float?, l2: Float?, combined: Float?) = transaction(db) {
        Posts.update({ Posts.id eq id }) {
            it[isTopicRelevant] = relevant
            it[topicScoreL1] = l1
            it[topicScoreL2] = l2
            it[topicScoreCombined] = combined
        }
    }

    /**
     * UPDATE результатов препроцессинга поста (этап 3): очищенный текст, определение
     * языка и счётчики хэштегов/упоминаний/ссылок.
     * @param textClean очищенный текст.
     * @param truncated признак усечения текста.
     * @param truncationReason причина усечения или NULL.
     * @param detectedLanguage определённый язык.
     * @param languageConfidence уверенность определения языка.
     * @param languageFlag флаг языка (например, RU/NON_RU/MIXED).
     */
    @Suppress("LongParameterList")
    fun updatePostPreprocessing(
        id: Int,
        textClean: String,
        ownTextLength: Int,
        truncated: Boolean,
        truncationReason: String?,
        detectedLanguage: String,
        languageConfidence: Float,
        languageFlag: String,
        hashtagsCount: Int,
        mentionsCount: Int,
        urlsCount: Int,
        containsMedia: Boolean,
    ) = transaction(db) {
        // UPDATE одной строки поста: переписываем поля препроцессинга
        Posts.update({ Posts.id eq id }) {
            it[Posts.textClean] = textClean
            it[Posts.ownTextLength] = ownTextLength
            it[Posts.truncated] = truncated
            it[Posts.truncationReason] = truncationReason
            it[Posts.detectedLanguage] = detectedLanguage
            it[Posts.languageConfidence] = languageConfidence
            it[Posts.languageFlag] = languageFlag
            it[Posts.hashtagsCount] = hashtagsCount
            it[Posts.mentionsCount] = mentionsCount
            it[Posts.urlsCount] = urlsCount
            it[Posts.containsMedia] = containsMedia
        }
    }
}
