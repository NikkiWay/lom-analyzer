package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.Authors
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class AuthorDao(private val db: Database) {
    fun insert(
        vkId: Int,
        firstName: String? = null,
        lastName: String? = null,
        screenName: String? = null,
        followersCount: Int? = null,
        isClosed: Boolean = false,
        discoverySource: String = "SEED",
    ): Int = transaction(db) {
        val now = Instant.now().toEpochMilli()
        Authors.insertAndGetId {
            it[Authors.vkId] = vkId
            it[Authors.firstName] = firstName
            it[Authors.lastName] = lastName
            it[Authors.screenName] = screenName
            it[Authors.followersCount] = followersCount
            it[Authors.isClosed] = isClosed
            it[Authors.discoverySource] = discoverySource
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    fun findById(id: Int): ResultRow? = transaction(db) {
        Authors.selectAll().where { Authors.id eq id }.singleOrNull()
    }

    fun findByVkId(vkId: Int): ResultRow? = transaction(db) {
        Authors.selectAll().where { Authors.vkId eq vkId }.singleOrNull()
    }

    fun findAll(): List<ResultRow> = transaction(db) {
        Authors.selectAll().where { Authors.deletedAt.isNull() }.toList()
    }

    fun update(id: Int, block: UpdateStatement.() -> Unit) = transaction(db) {
        Authors.update({ Authors.id eq id }) {
            block(it)
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }
}
