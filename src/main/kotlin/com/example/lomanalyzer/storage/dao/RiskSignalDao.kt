package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.RiskSignals
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class RiskSignalDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        riskScore: Float,
        category: String? = null,
        description: String? = null,
        recommendation: String? = null,
        isBorderline: Boolean = false,
    ): Int = transaction(db) {
        RiskSignals.insertAndGetId {
            it[RiskSignals.sessionId] = sessionId
            it[RiskSignals.riskScore] = riskScore
            it[RiskSignals.category] = category
            it[RiskSignals.description] = description
            it[RiskSignals.recommendation] = recommendation
            it[RiskSignals.isBorderline] = isBorderline
            it[createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        RiskSignals.selectAll().where { RiskSignals.sessionId eq sessionId }.toList()
    }
}
