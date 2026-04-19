package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.AnomalyEvents
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class AnomalyDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        type: String,
        dayDate: Long,
        severity: Float,
        description: String? = null,
        isHolidayDay: Boolean = false,
        routineProtectionApplied: Boolean = false,
    ): Int = transaction(db) {
        AnomalyEvents.insertAndGetId {
            it[AnomalyEvents.sessionId] = sessionId
            it[AnomalyEvents.type] = type
            it[AnomalyEvents.dayDate] = dayDate
            it[AnomalyEvents.severity] = severity
            it[AnomalyEvents.description] = description
            it[AnomalyEvents.isHolidayDay] = isHolidayDay
            it[AnomalyEvents.routineProtectionApplied] = routineProtectionApplied
            it[createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        AnomalyEvents.selectAll().where { AnomalyEvents.sessionId eq sessionId }
            .orderBy(AnomalyEvents.dayDate)
            .toList()
    }
}
