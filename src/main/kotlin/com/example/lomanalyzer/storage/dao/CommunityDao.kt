package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.Communities
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class CommunityDao(private val db: Database) {
    fun insert(
        vkId: Int,
        name: String,
        screenName: String? = null,
        membersCount: Int? = null,
        isClosed: Boolean = false,
        communityType: String? = null,
    ): Int = transaction(db) {
        val now = Instant.now().toEpochMilli()
        Communities.insertAndGetId {
            it[Communities.vkId] = vkId
            it[Communities.name] = name
            it[Communities.screenName] = screenName
            it[Communities.membersCount] = membersCount
            it[Communities.isClosed] = isClosed
            it[Communities.communityType] = communityType
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    fun findById(id: Int): ResultRow? = transaction(db) {
        Communities.selectAll().where { Communities.id eq id }.singleOrNull()
    }

    fun findByVkId(vkId: Int): ResultRow? = transaction(db) {
        Communities.selectAll().where { Communities.vkId eq vkId }.singleOrNull()
    }

    fun findAll(): List<ResultRow> = transaction(db) {
        Communities.selectAll().where { Communities.deletedAt.isNull() }.toList()
    }
}
