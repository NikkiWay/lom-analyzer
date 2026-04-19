package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.RecoveryChoices
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class RecoveryChoiceDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        pythonFailureCount: Int,
        choice: String,
        pipelineStage: String? = null,
        payloadJson: String? = null,
    ): Int = transaction(db) {
        RecoveryChoices.insertAndGetId {
            it[RecoveryChoices.sessionId] = sessionId
            it[RecoveryChoices.pythonFailureCount] = pythonFailureCount
            it[RecoveryChoices.choice] = choice
            it[RecoveryChoices.pipelineStage] = pipelineStage
            it[RecoveryChoices.payloadJson] = payloadJson
            it[timestamp] = Instant.now().toEpochMilli()
        }.value
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        RecoveryChoices.selectAll().where { RecoveryChoices.sessionId eq sessionId }
            .orderBy(RecoveryChoices.timestamp)
            .toList()
    }
}
