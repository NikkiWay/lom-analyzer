package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Full retention manager per v6 §31.4.
 * Soft-delete: sessions older than 12 months.
 * Hard-delete: 30 days after soft-delete.
 * Restore: clears deleted_at within the grace period.
 */
class RetentionManager(
    private val sessionDao: SessionDao,
    private val logger: Logger,
    private val softDeleteMonths: Long = 12,
    private val gracePeriodDays: Long = 30,
) {
    data class SoftDeletedSession(val id: Int, val name: String, val deletedAt: Long)

    fun runRetention() {
        softDeleteExpired()
        hardDeletePastGrace()
    }

    fun getSoftDeletedSessions(): List<SoftDeletedSession> {
        return sessionDao.findSoftDeleted().map {
            SoftDeletedSession(
                id = it[AnalysisSessions.id].value,
                name = it[AnalysisSessions.name],
                deletedAt = it[AnalysisSessions.deletedAt] ?: 0,
            )
        }
    }

    fun restoreSession(sessionId: Int) {
        sessionDao.restore(sessionId)
        logger.event(AppEvent.RETENTION_RESTORE, mapOf("session_id" to sessionId))
    }

    private fun softDeleteExpired() {
        val cutoff = Instant.now().minus(softDeleteMonths * 30, ChronoUnit.DAYS).toEpochMilli()
        val sessions = sessionDao.findAll()
        for (session in sessions) {
            val createdAt = session[AnalysisSessions.createdAt]
            if (createdAt < cutoff) {
                val id = session[AnalysisSessions.id].value
                sessionDao.softDelete(id)
                logger.event(AppEvent.RETENTION_SOFT_DELETE, mapOf("session_id" to id))
            }
        }
    }

    private fun hardDeletePastGrace() {
        val graceCutoff = Instant.now().minus(gracePeriodDays, ChronoUnit.DAYS).toEpochMilli()
        val softDeleted = sessionDao.findSoftDeleted()
        for (session in softDeleted) {
            val deletedAt = session[AnalysisSessions.deletedAt] ?: continue
            if (deletedAt < graceCutoff) {
                val id = session[AnalysisSessions.id].value
                sessionDao.hardDelete(id)
                logger.event(AppEvent.RETENTION_HARD_DELETE, mapOf("session_id" to id))
            }
        }
    }
}
