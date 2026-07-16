/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "session_metrics" — метрики производительности
 * выполнения этапов пайплайна в рамках сессии (длительность, число обработанных
 * элементов). Используется для мониторинга и диагностики.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица SessionMetricsTable (Exposed ORM). Каждая строка — метрика
 * по конкретному этапу (stage) сессии.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). foreign key на
 * analysis_session.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица метрик выполнения этапов сессии ("session_metrics").
 *
 * IntIdTable (суррогатный primary key "id"). Хранит тайминги и счётчики по
 * стадиям пайплайна.
 */
object SessionMetricsTable : IntIdTable("session_metrics") {
    /** foreign key на analysis_session.id — сессия, к которой относится метрика. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** Название этапа пайплайна, к которому относится измерение. */
    val stage = text("stage")
    /** Длительность выполнения этапа в миллисекундах. */
    val durationMs = integer("duration_ms")
    /** Число обработанных элементов на этапе; может отсутствовать. */
    val itemsProcessed = integer("items_processed").nullable()
    /** Произвольные метаданные измерения (JSON); может отсутствовать. */
    val metadataJson = text("metadata_json").nullable()
    /** Момент записи метрики (Unix-время, мс). */
    val createdAt = long("created_at")
}
