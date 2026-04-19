package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object RiskSignals : IntIdTable("risk_signal") {
    val sessionId = reference("session_id", AnalysisSessions)
    val riskScore = float("risk_score")
    val riskCi90Lo = float("risk_ci90_lo").nullable()
    val riskCi90Hi = float("risk_ci90_hi").nullable()
    val isBorderline = bool("is_borderline").default(false)
    val category = text("category").nullable()
    val description = text("description").nullable()
    val recommendation = text("recommendation").nullable()
    val decompositionJson = text("decomposition_json").nullable()
    val createdAt = long("created_at")
}
