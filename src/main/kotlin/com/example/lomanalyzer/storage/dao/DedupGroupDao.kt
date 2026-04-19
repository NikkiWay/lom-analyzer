package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.DedupGroups
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class DedupGroupDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        canonicalPostId: Int,
        duplicatePostId: Int,
        similarity: Float,
        method: String,
    ): Int = transaction(db) {
        DedupGroups.insertAndGetId {
            it[DedupGroups.sessionId] = sessionId
            it[DedupGroups.canonicalPostId] = canonicalPostId
            it[DedupGroups.duplicatePostId] = duplicatePostId
            it[DedupGroups.similarity] = similarity
            it[DedupGroups.method] = method
        }.value
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        DedupGroups.selectAll().where { DedupGroups.sessionId eq sessionId }.toList()
    }
}
