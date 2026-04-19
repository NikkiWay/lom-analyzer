package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object SessionMetricsTable : IntIdTable("session_metrics") {
    val sessionId = reference("session_id", AnalysisSessions)
    val stage = text("stage")
    val durationMs = integer("duration_ms")
    val itemsProcessed = integer("items_processed").nullable()
    val metadataJson = text("metadata_json").nullable()
    val createdAt = long("created_at")
}
