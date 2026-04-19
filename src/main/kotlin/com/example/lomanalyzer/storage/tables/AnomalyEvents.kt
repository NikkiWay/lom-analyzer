package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object AnomalyEvents : IntIdTable("anomaly_event") {
    val sessionId = reference("session_id", AnalysisSessions)
    val type = text("type")
    val dayDate = long("day_date")
    val severity = float("severity")
    val severityCi90Lo = float("severity_ci90_lo").nullable()
    val severityCi90Hi = float("severity_ci90_hi").nullable()
    val description = text("description").nullable()
    val isHolidayDay = bool("is_holiday_day").default(false)
    val routineProtectionApplied = bool("routine_protection_applied").default(false)
    val metadataJson = text("metadata_json").nullable()
    val createdAt = long("created_at")
}
