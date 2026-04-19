package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import java.time.Instant
import java.time.temporal.ChronoUnit

class RetentionManager(
    private val sessionDao: SessionDao,
    private val logger: Logger,
) {
    fun runRetention() {
        val cutoff = Instant.now().minus(365, ChronoUnit.DAYS).toEpochMilli()
        val sessions = sessionDao.findAll()
        for (session in sessions) {
            val createdAt = session[AnalysisSessions.createdAt]
            if (createdAt < cutoff) {
                val id = session[AnalysisSessions.id].value
                sessionDao.softDelete(id)
                logger.event(AppEvent.RETENTION_HARD_DELETE, mapOf("session_id" to id))
            }
        }
    }
}
