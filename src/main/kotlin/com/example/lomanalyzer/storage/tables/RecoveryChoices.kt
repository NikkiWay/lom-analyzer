package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object RecoveryChoices : IntIdTable("recovery_choice") {
    val sessionId = reference("session_id", AnalysisSessions)
    val pythonFailureCount = integer("python_failure_count")
    val choice = text("choice")
    val pipelineStage = text("pipeline_stage").nullable()
    val payloadJson = text("payload_json").nullable()
    val timestamp = long("timestamp")
}
