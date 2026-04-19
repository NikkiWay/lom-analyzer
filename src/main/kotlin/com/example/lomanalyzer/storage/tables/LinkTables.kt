package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.sql.Table

object SessionCommunities : Table("session_community") {
    val sessionId = reference("session_id", AnalysisSessions)
    val communityId = reference("community_id", Communities)
    override val primaryKey = PrimaryKey(sessionId, communityId)
}

object SessionAuthors : Table("session_author") {
    val sessionId = reference("session_id", AnalysisSessions)
    val authorId = reference("author_id", Authors)
    val role = text("role").nullable()
    override val primaryKey = PrimaryKey(sessionId, authorId)
}

object AnomalyAuthorLinks : Table("anomaly_author_link") {
    val anomalyId = reference("anomaly_id", AnomalyEvents)
    val authorId = reference("author_id", Authors)
    override val primaryKey = PrimaryKey(anomalyId, authorId)
}

object AnomalyPostLinks : Table("anomaly_post_link") {
    val anomalyId = reference("anomaly_id", AnomalyEvents)
    val postId = reference("post_id", Posts)
    override val primaryKey = PrimaryKey(anomalyId, postId)
}

object RiskAnomalyLinks : Table("risk_anomaly_link") {
    val riskId = reference("risk_id", RiskSignals)
    val anomalyId = reference("anomaly_id", AnomalyEvents)
    override val primaryKey = PrimaryKey(riskId, anomalyId)
}
