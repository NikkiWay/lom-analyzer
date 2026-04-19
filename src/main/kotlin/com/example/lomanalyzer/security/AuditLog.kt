package com.example.lomanalyzer.security

import com.example.lomanalyzer.observability.Logger
import java.time.Instant

data class AuditEntry(
    val timestamp: Instant,
    val action: String,
    val details: Map<String, String> = emptyMap(),
)

/**
 * Writes audit events to both the structured logger and the database.
 */
class AuditLog(
    private val logger: Logger,
    private val dao: AuditDao? = null,
) {
    fun record(action: String, details: Map<String, String> = emptyMap()) {
        val entry = AuditEntry(
            timestamp = Instant.now(),
            action = action,
            details = details,
        )
        dao?.insert(entry)
        logger.info("AUDIT: $action", details.mapValues { it.value as Any? })
    }
}

/** DAO interface for persisting audit entries to the database. */
interface AuditDao {
    fun insert(entry: AuditEntry)
}
