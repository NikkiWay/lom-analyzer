package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object DedupGroups : IntIdTable("dedup_group") {
    val sessionId = reference("session_id", AnalysisSessions)
    val canonicalPostId = reference("canonical_post_id", Posts)
    val duplicatePostId = reference("duplicate_post_id", Posts)
    val similarity = float("similarity")
    val method = text("method")
}
