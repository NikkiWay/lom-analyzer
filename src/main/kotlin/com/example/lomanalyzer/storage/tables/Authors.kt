package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object Authors : IntIdTable("author") {
    val vkId = integer("vk_id").uniqueIndex()
    val firstName = text("first_name").nullable()
    val lastName = text("last_name").nullable()
    val screenName = text("screen_name").nullable()
    val followersCount = integer("followers_count").nullable()
    val isClosed = bool("is_closed").default(false)
    val audienceFlag = text("audience_flag").nullable()
    val firstSeenAt = long("first_seen_at").nullable()
    val discoverySource = text("discovery_source").default("SEED")
    val baselineWindowDays = integer("baseline_window_days").default(60)
    val accountFlags = text("account_flags").nullable()
    val possiblyNonStationary = bool("possibly_non_stationary").default(false)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val deletedAt = long("deleted_at").nullable()
}
