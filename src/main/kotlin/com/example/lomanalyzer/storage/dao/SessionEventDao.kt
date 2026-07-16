/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для событий сессии (таблица session_events) — журнал
 * хода выполнения пайплайна: типизированные события с сообщением и деталями, по
 * которым UI показывает прогресс/лог сессии. Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс SessionEventDao: insert (записать событие и вернуть id), findBySession
 * (хронологическая выборка событий сессии).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов. java.time.Instant — created_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица SessionEvents (storage/tables). Привязка к сессии через sessionId.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.SessionEvents
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO событий сессии. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class SessionEventDao(private val db: Database) {
    /**
     * INSERT события сессии.
     * @param eventType тип события (категория для фильтрации/отображения).
     * @param message человекочитаемое сообщение или NULL.
     * @param details дополнительные детали (например, JSON) или NULL.
     * @return сгенерированный id события.
     */
    fun insert(
        sessionId: Int,
        eventType: String,
        message: String? = null,
        details: String? = null,
    ): Int = transaction(db) {
        SessionEvents.insertAndGetId {
            it[SessionEvents.sessionId] = sessionId
            it[SessionEvents.eventType] = eventType
            it[SessionEvents.message] = message
            it[SessionEvents.details] = details
            it[SessionEvents.createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    /**
     * SELECT всех событий сессии в хронологическом порядке (createdAt ASC).
     * @return список ResultRow.
     */
    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        SessionEvents.selectAll().where { SessionEvents.sessionId eq sessionId }
            .orderBy(SessionEvents.createdAt)
            .toList()
    }
}
