package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.RepostRelations
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class RepostRelationDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        originalPostId: Int,
        repostPostId: Int,
        reposterVkId: Int,
        repostedAt: Long,
    ): Int = transaction(db) {
        RepostRelations.insertAndGetId {
            it[RepostRelations.sessionId] = sessionId
            it[RepostRelations.originalPostId] = originalPostId
            it[RepostRelations.repostPostId] = repostPostId
            it[RepostRelations.reposterVkId] = reposterVkId
            it[RepostRelations.repostedAt] = repostedAt
        }.value
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        RepostRelations.selectAll().where { RepostRelations.sessionId eq sessionId }.toList()
    }
}
