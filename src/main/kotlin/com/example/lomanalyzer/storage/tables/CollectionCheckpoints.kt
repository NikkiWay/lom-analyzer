package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object CollectionCheckpoints : IntIdTable("collection_checkpoint") {
    val sessionId = reference("session_id", AnalysisSessions)
    val endpoint = text("endpoint")
    val ownerId = integer("owner_id")
    val offsetValue = text("offset_value").nullable()
    val itemsCollected = integer("items_collected").default(0)
    val status = text("status").default("IN_PROGRESS")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}
