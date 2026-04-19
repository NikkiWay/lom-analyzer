package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.AnalysisSessions
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class SessionDao(private val db: Database) {
    fun insert(
        name: String,
        topicQuery: String,
        region: String? = null,
        nlpMode: String = "FULL",
        baselineWindowDays: Int = 60,
        currentWindowDays: Int = 30,
    ): Int = transaction(db) {
        val now = Instant.now().toEpochMilli()
        AnalysisSessions.insertAndGetId {
            it[AnalysisSessions.name] = name
            it[AnalysisSessions.topicQuery] = topicQuery
            it[AnalysisSessions.region] = region
            it[AnalysisSessions.nlpMode] = nlpMode
            it[AnalysisSessions.baselineWindowDays] = baselineWindowDays
            it[AnalysisSessions.currentWindowDays] = currentWindowDays
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    fun findById(id: Int): ResultRow? = transaction(db) {
        AnalysisSessions.selectAll().where { AnalysisSessions.id eq id }.singleOrNull()
    }

    fun updateStatus(id: Int, status: String) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[AnalysisSessions.status] = status
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    fun findAll(): List<ResultRow> = transaction(db) {
        AnalysisSessions.selectAll()
            .where { AnalysisSessions.deletedAt.isNull() }
            .orderBy(AnalysisSessions.createdAt, SortOrder.DESC)
            .toList()
    }

    fun softDelete(id: Int) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[deletedAt] = Instant.now().toEpochMilli()
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    fun findSoftDeleted(): List<ResultRow> = transaction(db) {
        AnalysisSessions.selectAll()
            .where { AnalysisSessions.deletedAt.isNotNull() }
            .orderBy(AnalysisSessions.deletedAt, SortOrder.DESC)
            .toList()
    }

    fun restore(id: Int) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[deletedAt] = null
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }

    fun hardDelete(id: Int) = transaction(db) {
        AnalysisSessions.deleteWhere { AnalysisSessions.id eq id }
    }

    fun setSessionFamily(id: Int, familySourceId: Int) = transaction(db) {
        AnalysisSessions.update({ AnalysisSessions.id eq id }) {
            it[sessionFamilyId] = familySourceId
            it[updatedAt] = Instant.now().toEpochMilli()
        }
    }
}
