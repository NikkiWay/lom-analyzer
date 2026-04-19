package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object RepostRelations : IntIdTable("repost_relation") {
    val sessionId = reference("session_id", AnalysisSessions)
    val originalPostId = reference("original_post_id", Posts)
    val repostPostId = reference("repost_post_id", Posts)
    val reposterVkId = integer("reposter_vk_id")
    val repostedAt = long("reposted_at")
}
