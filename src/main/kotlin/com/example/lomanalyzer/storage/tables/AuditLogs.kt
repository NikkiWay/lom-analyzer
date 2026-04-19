package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object AuditLogs : IntIdTable("audit_log") {
    val sessionId = optReference("session_id", AnalysisSessions)
    val action = text("action")
    val details = text("details").nullable()
    val createdAt = long("created_at")
}
