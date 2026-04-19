package com.example.lomanalyzer.persona

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Placeholder for longitudinal persona tracking (v2.0).
 * For v1.0: links forked session personas for traceability.
 */
object PersonaHistoryLinks : Table("persona_history_link") {
    val id = integer("id").autoIncrement()
    val sourceSessionId = integer("source_session_id")
    val targetSessionId = integer("target_session_id")
    val authorId = integer("author_id")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

class PersonaHistoryManager(private val db: Database) {
    fun linkPersonas(sourceSessionId: Int, targetSessionId: Int, authorId: Int) {
        transaction(db) {
            PersonaHistoryLinks.insert {
                it[PersonaHistoryLinks.sourceSessionId] = sourceSessionId
                it[PersonaHistoryLinks.targetSessionId] = targetSessionId
                it[PersonaHistoryLinks.authorId] = authorId
                it[createdAt] = Instant.now().toEpochMilli()
            }
        }
    }

    fun findLinks(sessionId: Int): List<ResultRow> = transaction(db) {
        PersonaHistoryLinks.selectAll().where {
            (PersonaHistoryLinks.sourceSessionId eq sessionId) or
                (PersonaHistoryLinks.targetSessionId eq sessionId)
        }.toList()
    }
}
