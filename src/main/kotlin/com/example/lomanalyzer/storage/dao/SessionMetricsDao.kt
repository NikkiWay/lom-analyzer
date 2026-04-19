package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.SessionMetricsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class SessionMetricsDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        stage: String,
        durationMs: Int,
        itemsProcessed: Int? = null,
        metadataJson: String? = null,
    ): Int = transaction(db) {
        SessionMetricsTable.insertAndGetId {
            it[SessionMetricsTable.sessionId] = sessionId
            it[SessionMetricsTable.stage] = stage
            it[SessionMetricsTable.durationMs] = durationMs
            it[SessionMetricsTable.itemsProcessed] = itemsProcessed
            it[SessionMetricsTable.metadataJson] = metadataJson
            it[createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        SessionMetricsTable.selectAll()
            .where { SessionMetricsTable.sessionId eq sessionId }
            .toList()
    }
}
