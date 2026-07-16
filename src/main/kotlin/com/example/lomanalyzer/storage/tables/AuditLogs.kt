/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "audit_log" — журнал аудита действий
 * (служебные/пользовательские операции над сессией и приложением). Нужен для
 * прослеживаемости (что и когда произошло), в т. ч. для контроля качества.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица AuditLogs (Exposed ORM). Запись может быть привязана к
 * сессии (optReference — допускается NULL для глобальных действий вне сессии).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). optReference задаёт
 * необязательный (nullable) foreign key на analysis_session.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id (может быть NULL).
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица журнала аудита ("audit_log").
 *
 * IntIdTable (суррогатный primary key "id"). Фиксирует действия с привязкой к
 * сессии (необязательной) и человекочитаемым описанием.
 */
object AuditLogs : IntIdTable("audit_log") {
    /** Необязательный foreign key на analysis_session.id; NULL — действие вне конкретной сессии. */
    val sessionId = optReference("session_id", AnalysisSessions)
    /** Код/название действия (что произошло). */
    val action = text("action")
    /** Дополнительные детали действия (например, JSON); может отсутствовать. */
    val details = text("details").nullable()
    /** Момент записи события (Unix-время, мс). */
    val createdAt = long("created_at")
}
