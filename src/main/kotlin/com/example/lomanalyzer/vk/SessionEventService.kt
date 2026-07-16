/*
 * НАЗНАЧЕНИЕ
 * Запись событий сессии сбора в таблицу session_event (модуль сбора данных). Фиксирует
 * запросы к VK API, ошибки, вехи прогресса, закрытые аккаунты и информационные сообщения.
 * UI читает эту таблицу для отображения ленты событий в реальном времени.
 *
 * ЧТО ВНУТРИ
 * Класс SessionEventService с методами logApiRequest, logApiError, logProgress, logInfo,
 * logClosedAccount, logError — каждый пишет одну строку в session_event.
 *
 * ФРЕЙМВОРКИ / СВЯЗИ
 * kotlinx.serialization (Json) — сериализация параметров запроса в details. SessionEventDao —
 * доступ к таблице; Logger дополнительно дублирует предупреждения/ошибки в общий лог.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.SessionEventDao
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Records session events to session_event table (diploma 2.2.5).
 * Every VK API request, every error, every 10% progress milestone.
 * UI reads this table to display real-time event feed.
 */
class SessionEventService(
    private val sessionEventDao: SessionEventDao,
    private val logger: Logger,
) {
    /** JSON-сериализатор для поля details; не выводит значения по умолчанию. */
    private val json = Json { encodeDefaults = false }

    /** Фиксирует факт запроса к VK API (тип API_REQUEST); параметры сериализуются в details. */
    fun logApiRequest(sessionId: Int, method: String, params: Map<String, Any?> = emptyMap()) {
        sessionEventDao.insert(
            sessionId = sessionId,
            eventType = "API_REQUEST",
            message = method,
            details = if (params.isNotEmpty()) json.encodeToString(params.mapValues { it.value?.toString() }) else null,
        )
    }

    /** Фиксирует ошибку VK API (тип API_ERROR) и дублирует предупреждение в общий лог. */
    fun logApiError(sessionId: Int, method: String, errorCode: Int, errorMsg: String) {
        sessionEventDao.insert(
            sessionId = sessionId,
            eventType = "API_ERROR",
            message = "$method: [$errorCode] $errorMsg",
        )
        logger.warn("VK API error in $method: [$errorCode] $errorMsg")
    }

    /** Фиксирует веху прогресса (тип PROGRESS) с вычисленным процентом выполнения. */
    fun logProgress(sessionId: Int, phase: String, completed: Int, total: Int) {
        val pct = if (total > 0) (completed * 100 / total) else 0
        sessionEventDao.insert(
            sessionId = sessionId,
            eventType = "PROGRESS",
            message = "$phase: $completed/$total ($pct%)",
        )
    }

    /** Пишет информационное событие (тип INFO) с опциональными деталями. */
    fun logInfo(sessionId: Int, message: String, details: String? = null) {
        sessionEventDao.insert(
            sessionId = sessionId,
            eventType = "INFO",
            message = message,
            details = details,
        )
    }

    /** Фиксирует обнаружение закрытого аккаунта (тип CLOSED_ACCOUNT); такой автор исключается из обработки. */
    fun logClosedAccount(sessionId: Int, vkId: Int) {
        sessionEventDao.insert(
            sessionId = sessionId,
            eventType = "CLOSED_ACCOUNT",
            message = "Author VK ID $vkId has a closed profile, excluded from processing",
        )
    }

    /** Пишет событие об ошибке (тип ERROR) и дублирует его в общий лог. */
    fun logError(sessionId: Int, message: String, details: String? = null) {
        sessionEventDao.insert(
            sessionId = sessionId,
            eventType = "ERROR",
            message = message,
            details = details,
        )
        logger.error(message)
    }
}
