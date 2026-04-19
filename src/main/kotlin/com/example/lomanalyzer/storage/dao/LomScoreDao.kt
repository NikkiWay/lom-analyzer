package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.LomScores
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class LomScoreDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        authorId: Int,
        block: LomScores.(InsertStatement<*>) -> Unit,
    ): Int = transaction(db) {
        LomScores.insertAndGetId {
            it[LomScores.sessionId] = sessionId
            it[LomScores.authorId] = authorId
            it[createdAt] = Instant.now().toEpochMilli()
            block(it)
        }.value
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        LomScores.selectAll().where { LomScores.sessionId eq sessionId }.toList()
    }

    fun findBySessionAndAuthor(sessionId: Int, authorId: Int): ResultRow? =
        transaction(db) {
            LomScores.selectAll().where {
                (LomScores.sessionId eq sessionId) and
                    (LomScores.authorId eq authorId)
            }.singleOrNull()
        }
}
