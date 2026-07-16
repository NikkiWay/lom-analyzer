/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "session_event" — лента событий хода сессии
 * (информационные сообщения, предупреждения, ошибки этапов пайплайна).
 * Отображается в UI и используется для контроля качества/диагностики.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица SessionEvents (Exposed ORM). Привязана к сессии; имеет
 * индекс для хронологической выборки событий.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). Индекс объявлен в
 * init { }.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица событий сессии ("session_event").
 *
 * IntIdTable (суррогатный primary key "id"). Хронологический журнал событий
 * выполнения сессии.
 */
object SessionEvents : IntIdTable("session_event") {
    /** foreign key на analysis_session.id — сессия, к которой относится событие. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** Тип события (например, INFO/WARN/ERROR или код этапа). */
    val eventType = text("event_type")
    /** Краткое сообщение события; может отсутствовать. */
    val message = text("message").nullable()
    /** Подробности события (например, JSON/стек); может отсутствовать. */
    val details = text("details").nullable()
    /** Момент возникновения события (Unix-время, мс). */
    val createdAt = long("created_at")

    init {
        // Индекс для выборки событий сессии в хронологическом порядке (по времени)
        index(false, sessionId, createdAt)
    }
}
