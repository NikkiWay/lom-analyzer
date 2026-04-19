package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.Posts
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class PostDao(private val db: Database) {
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
    ): Int = transaction(db) {
        Posts.insertAndGetId {
            it[Posts.sessionId] = sessionId
            it[Posts.vkId] = vkId
            it[Posts.ownerId] = ownerId
            it[Posts.fromId] = fromId
            it[Posts.publishedAt] = publishedAt
            it[Posts.text] = text
            it[Posts.window] = window
            it[Posts.ownTextLength] = ownTextLength
            it[createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    fun findById(id: Int): ResultRow? = transaction(db) {
        Posts.selectAll().where { Posts.id eq id }.singleOrNull()
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        Posts.selectAll().where { Posts.sessionId eq sessionId }
            .orderBy(Posts.publishedAt)
            .toList()
    }

    fun findBySessionAndWindow(sessionId: Int, window: String): List<ResultRow> = transaction(db) {
        Posts.selectAll().where {
            (Posts.sessionId eq sessionId) and (Posts.window eq window)
        }.toList()
    }

    fun updateTopicRelevance(id: Int, relevant: Boolean, l1: Float?, l2: Float?, combined: Float?) = transaction(db) {
        Posts.update({ Posts.id eq id }) {
            it[isTopicRelevant] = relevant
            it[topicScoreL1] = l1
            it[topicScoreL2] = l2
            it[topicScoreCombined] = combined
        }
    }

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
