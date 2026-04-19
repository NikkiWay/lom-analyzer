package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.CollectionCheckpoints
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class CheckpointDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        endpoint: String,
        ownerId: Int,
    ): Int = transaction(db) {
        val now = Instant.now().toEpochMilli()
        CollectionCheckpoints.insertAndGetId {
            it[CollectionCheckpoints.sessionId] = sessionId
            it[CollectionCheckpoints.endpoint] = endpoint
            it[CollectionCheckpoints.ownerId] = ownerId
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    fun updateProgress(id: Int, offset: String?, itemsCollected: Int, status: String) = transaction(db) {
        CollectionCheckpoints.update({ CollectionCheckpoints.id eq id }) {
            it[offsetValue] = offset
            it[CollectionCheckpoints.itemsCollected] = itemsCollected
            it[CollectionCheckpoints.status] = status
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        CollectionCheckpoints.selectAll()
            .where { CollectionCheckpoints.sessionId eq sessionId }
            .toList()
    }
}
