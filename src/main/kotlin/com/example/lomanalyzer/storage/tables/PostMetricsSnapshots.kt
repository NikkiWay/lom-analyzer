package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/** Reserved for SessionFamily (v2.0). */
object PostMetricsSnapshots : IntIdTable("post_metrics_snapshot") {
    val sessionId = reference("session_id", AnalysisSessions)
    val postId = reference("post_id", Posts)
    val snapshotJson = text("snapshot_json").nullable()
    val createdAt = long("created_at")
}
