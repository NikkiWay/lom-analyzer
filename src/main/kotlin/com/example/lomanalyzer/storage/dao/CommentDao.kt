/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для комментариев (таблица comments). Хранит
 * комментарии к постам, собранные на этапе сбора (фаза C — стены авторов и
 * комментарии). Комментарии — основа для оценки отклика аудитории (Resp_*).
 * Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс CommentDao: insert (идемпотентная вставка по ключу сессия+vkId),
 * findBySession/findByPost/findBySessionAndPost (выборки, отсортированы по времени
 * публикации), countBySession/countByPost (агрегатные COUNT).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов (insertAndGetId, selectAll, count).
 * java.time.Instant — created_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица Comments (storage/tables). postId ссылается на post; fromId — автор VK.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.Comments
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO комментариев. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class CommentDao(private val db: Database) {
    /**
     * Идемпотентный INSERT комментария: если в этой сессии уже есть комментарий с
     * таким vkId, повторно не вставляет, а возвращает id существующего.
     * @param postId внутренний id поста, к которому относится комментарий.
     * @param vkId идентификатор комментария во ВКонтакте.
     * @param fromId идентификатор автора комментария во ВКонтакте.
     * @param publishedAt время публикации (epoch millis).
     * @param likes число лайков комментария.
     * @return id существующей или вновь созданной строки.
     */
    fun insert(
        sessionId: Int,
        postId: Int,
        vkId: Int,
        fromId: Int,
        text: String?,
        publishedAt: Long,
        likes: Int = 0,
    ): Int = transaction(db) {
        // Проверка дубликата в рамках сессии по внешнему vkId комментария
        val existing = Comments.selectAll().where {
            (Comments.sessionId eq sessionId) and (Comments.vkId eq vkId)
        }.singleOrNull()
        // Уже сохранён — возвращаем его id, новую строку не создаём
        if (existing != null) return@transaction existing[Comments.id].value

        Comments.insertAndGetId {
            it[Comments.sessionId] = sessionId
            it[Comments.postId] = postId
            it[Comments.vkId] = vkId
            it[Comments.fromId] = fromId
            it[Comments.text] = text
            it[Comments.publishedAt] = publishedAt
            it[Comments.likes] = likes
            it[Comments.createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    /**
     * SELECT всех комментариев сессии, упорядоченных по времени публикации (ASC).
     * @return список ResultRow.
     */
    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        Comments.selectAll().where { Comments.sessionId eq sessionId }
            .orderBy(Comments.publishedAt)
            .toList()
    }

    /**
     * SELECT всех комментариев к конкретному посту, по времени публикации (ASC).
     * @return список ResultRow.
     */
    fun findByPost(postId: Int): List<ResultRow> = transaction(db) {
        Comments.selectAll().where { Comments.postId eq postId }
            .orderBy(Comments.publishedAt)
            .toList()
    }

    /**
     * SELECT комментариев к посту в рамках конкретной сессии, по времени (ASC).
     * @return список ResultRow.
     */
    fun findBySessionAndPost(sessionId: Int, postId: Int): List<ResultRow> = transaction(db) {
        Comments.selectAll().where {
            (Comments.sessionId eq sessionId) and (Comments.postId eq postId)
        }.orderBy(Comments.publishedAt).toList()
    }

    /**
     * COUNT числа комментариев в сессии.
     * @return количество строк (Long).
     */
    fun countBySession(sessionId: Int): Long = transaction(db) {
        Comments.selectAll().where { Comments.sessionId eq sessionId }.count()
    }

    /**
     * COUNT числа комментариев к конкретному посту.
     * @return количество строк (Long).
     */
    fun countByPost(postId: Int): Long = transaction(db) {
        Comments.selectAll().where { Comments.postId eq postId }.count()
    }
}
