/*
 * НАЗНАЧЕНИЕ
 * Сервис управления сессиями анализа поверх таблицы analysis_sessions: создание
 * сессии из параметров формы, чтение, смена статуса, перечисление и «форк»
 * (создание производной сессии на базе существующей). Используется UI и
 * PipelineOrchestrator/PipelineWiring (смена статусов по ходу пайплайна).
 *
 * ЧТО ВНУТРИ
 * Класс SessionManager с методами createSession, getSession, updateStatus,
 * listSessions, getStatus, forkSession.
 *
 * МЕТОД
 * Списки n-грамм и эталонных текстов сериализуются в одну строку через перевод
 * строки (joinToString("\n")); пустые списки сохраняются как null. forkSession
 * создаёт новую сессию и связывает её с исходной через «семью» сессий
 * (setSessionFamily) — для сравнения результатов.
 *
 * БИБЛИОТЕКИ
 * Exposed ORM (SessionDao, таблица AnalysisSessions, ResultRow) — доступ к БД.
 * Logger — события SESSION_CREATED / SESSION_FORKED.
 */
package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.SessionDao
import com.example.lomanalyzer.storage.tables.AnalysisSessions
import org.jetbrains.exposed.sql.ResultRow

/**
 * Управляет жизненным циклом сессий анализа в БД.
 *
 * @param sessionDao DAO таблицы сессий.
 * @param logger логгер событий сессий.
 */
class SessionManager(
    private val sessionDao: SessionDao,
    private val logger: Logger,
) {
    /**
     * Создаёт новую сессию из параметров формы и возвращает её id.
     * Списки n-грамм/эталонов «склеиваются» в строки; пустые → null.
     */
    fun createSession(params: SessionParams): Int {
        val id = sessionDao.insert(
            name = params.name,
            topicQuery = params.topicQuery,
            primaryNgrams = params.primaryNgrams.joinToString("\n").ifBlank { null },
            secondaryNgrams = params.secondaryNgrams.joinToString("\n").ifBlank { null },
            excludedNgrams = params.excludedNgrams.joinToString("\n").ifBlank { null },
            referenceTexts = params.referenceTexts.joinToString("\n").ifBlank { null },
            region = params.region,
            nlpMode = params.nlpMode,
            baselineWindowDays = params.baselineWindowDays,
            currentWindowDays = params.currentWindowDays,
            importJsonPath = params.importJsonPath,
        )
        logger.event(AppEvent.SESSION_CREATED, mapOf("session_id" to id))
        return id
    }

    /** Возвращает строку сессии по id или null, если не найдена. */
    fun getSession(id: Int): ResultRow? = sessionDao.findById(id)

    /** Обновляет статус сессии (имя enum сохраняется как строка). */
    fun updateStatus(id: Int, status: SessionStatus) {
        sessionDao.updateStatus(id, status.name)
    }

    /** Возвращает все сессии. */
    fun listSessions(): List<ResultRow> = sessionDao.findAll()

    /** Декодирует статус сессии из строки БД в enum SessionStatus. */
    fun getStatus(row: ResultRow): SessionStatus =
        SessionStatus.valueOf(row[AnalysisSessions.status])

    /**
     * Создаёт производную сессию («форк») на базе существующей с новыми параметрами
     * и связывает её с исходной в «семью» сессий. Возвращает id новой сессии.
     */
    fun forkSession(sourceSessionId: Int, newParams: SessionParams): Int {
        val newId = sessionDao.insert(
            name = newParams.name,
            topicQuery = newParams.topicQuery,
            primaryNgrams = newParams.primaryNgrams.joinToString("\n").ifBlank { null },
            secondaryNgrams = newParams.secondaryNgrams.joinToString("\n").ifBlank { null },
            excludedNgrams = newParams.excludedNgrams.joinToString("\n").ifBlank { null },
            referenceTexts = newParams.referenceTexts.joinToString("\n").ifBlank { null },
            region = newParams.region,
            nlpMode = newParams.nlpMode,
            baselineWindowDays = newParams.baselineWindowDays,
            currentWindowDays = newParams.currentWindowDays,
            importJsonPath = newParams.importJsonPath,
        )
        // Привязываем новую сессию к исходной (общая «семья» для сравнения результатов)
        sessionDao.setSessionFamily(newId, sourceSessionId)
        logger.event(AppEvent.SESSION_FORKED, mapOf(
            "source_id" to sourceSessionId, "new_id" to newId,
        ))
        return newId
    }
}
