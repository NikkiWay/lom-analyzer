package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object Communities : IntIdTable("community") {
    val vkId = integer("vk_id").uniqueIndex()
    val name = text("name")
    val screenName = text("screen_name").nullable()
    val membersCount = integer("members_count").nullable()
    val isClosed = bool("is_closed").default(false)
    val communityType = text("community_type").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val deletedAt = long("deleted_at").nullable()
}
