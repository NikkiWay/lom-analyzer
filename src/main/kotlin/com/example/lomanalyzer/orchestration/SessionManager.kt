package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import org.jetbrains.exposed.sql.ResultRow

class SessionManager(
    private val sessionDao: SessionDao,
    private val logger: Logger,
) {
    fun createSession(params: SessionParams): Int {
        val id = sessionDao.insert(
            name = params.name,
            topicQuery = params.topicQuery,
            region = params.region,
            nlpMode = params.nlpMode,
            baselineWindowDays = params.baselineWindowDays,
            currentWindowDays = params.currentWindowDays,
        )
        logger.event(AppEvent.SESSION_CREATED, mapOf("session_id" to id))
        return id
    }

    fun getSession(id: Int): ResultRow? = sessionDao.findById(id)

    fun updateStatus(id: Int, status: SessionStatus) {
        sessionDao.updateStatus(id, status.name)
    }

    fun listSessions(): List<ResultRow> = sessionDao.findAll()

    fun getStatus(row: ResultRow): SessionStatus =
        SessionStatus.valueOf(row[AnalysisSessions.status])

    fun forkSession(sourceSessionId: Int, newParams: SessionParams): Int {
        val newId = sessionDao.insert(
            name = newParams.name,
            topicQuery = newParams.topicQuery,
            region = newParams.region,
            nlpMode = newParams.nlpMode,
            baselineWindowDays = newParams.baselineWindowDays,
            currentWindowDays = newParams.currentWindowDays,
        )
        sessionDao.setSessionFamily(newId, sourceSessionId)
        logger.event(AppEvent.SESSION_FORKED, mapOf(
            "source_id" to sourceSessionId, "new_id" to newId,
        ))
        return newId
    }
}
