package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.PersonaAggregates
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction

class PersonaAggregateDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        authorId: Int,
        snapshotTimestamp: Long,
        block: PersonaAggregates.(InsertStatement<*>) -> Unit,
    ): Int = transaction(db) {
            PersonaAggregates.insertAndGetId {
                it[PersonaAggregates.sessionId] = sessionId
                it[PersonaAggregates.authorId] = authorId
                it[PersonaAggregates.snapshotTimestamp] = snapshotTimestamp
                block(it)
            }.value
        }

    fun findBySessionAndAuthor(sessionId: Int, authorId: Int): ResultRow? = transaction(db) {
        PersonaAggregates.selectAll().where {
            (PersonaAggregates.sessionId eq sessionId) and (PersonaAggregates.authorId eq authorId)
        }.singleOrNull()
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        PersonaAggregates.selectAll()
            .where { PersonaAggregates.sessionId eq sessionId }
            .toList()
    }
}
