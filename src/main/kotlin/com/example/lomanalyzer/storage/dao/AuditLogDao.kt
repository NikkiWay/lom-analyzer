package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.security.AuditDao
import com.example.lomanalyzer.security.AuditEntry
import com.example.lomanalyzer.storage.tables.AuditLogs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class AuditLogDao(private val db: Database) : AuditDao {
    override fun insert(entry: AuditEntry) {
        transaction(db) {
            AuditLogs.insert {
                it[action] = entry.action
                it[details] = if (entry.details.isNotEmpty()) Json.encodeToString(entry.details) else null
                it[createdAt] = entry.timestamp.toEpochMilli()
            }
        }
    }

    fun insertWithSession(sessionId: Int, action: String, details: String? = null) = transaction(db) {
        AuditLogs.insert {
            it[AuditLogs.sessionId] = sessionId
            it[AuditLogs.action] = action
            it[AuditLogs.details] = details
            it[createdAt] = java.time.Instant.now().toEpochMilli()
        }
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        AuditLogs.selectAll().where { AuditLogs.sessionId eq sessionId }
            .orderBy(AuditLogs.createdAt, SortOrder.DESC)
            .toList()
    }
}
