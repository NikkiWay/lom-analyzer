package com.example.lomanalyzer.security

import com.example.lomanalyzer.observability.Logger
import java.time.Instant

data class AuditEntry(
    val timestamp: Instant,
    val action: String,
    val details: Map<String, String> = emptyMap(),
)

/**
 * Writes audit events. Currently logs to the structured logger;
 * will be wired to the database DAO once the Storage layer is implemented.
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
        // Persist to DB when DAO is available
        dao?.insert(entry)
        // Always log structurally
        logger.info("AUDIT: $action", details.mapValues { it.value as Any? })
    }
}

/** Stub DAO interface — will be implemented in the Storage layer. */
interface AuditDao {
    fun insert(entry: AuditEntry)
}
