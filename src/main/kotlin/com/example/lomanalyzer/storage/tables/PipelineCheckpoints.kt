/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "pipeline_checkpoint" — контрольные точки
 * выполнения этапов пайплайна (10 этапов). Позволяют возобновлять прогон с
 * последней успешно завершённой фазы/стадии и хранить промежуточные данные.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица PipelineCheckpoints (Exposed ORM). Привязана к сессии;
 * фиксирует фазу, стадию, статус и сериализованную полезную нагрузку. Имеет
 * индекс по (session, phase).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). Индекс объявлен в
 * init { }. В отличие от collection_checkpoint (прогресс сбора из VK), эта
 * таблица отслеживает прогресс аналитических этапов.
 *
 * СВЯЗИ
 * session_id -> analysis_session.id.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица контрольных точек пайплайна ("pipeline_checkpoint").
 *
 * IntIdTable (суррогатный primary key "id"). Хранит прогресс по фазам и стадиям
 * аналитического пайплайна для возобновляемости.
 */
object PipelineCheckpoints : IntIdTable("pipeline_checkpoint") {
    /** foreign key на analysis_session.id — сессия, к которой относится чекпойнт. */
    val sessionId = reference("session_id", AnalysisSessions)
    /** Фаза пайплайна (например, сбор/анализ). */
    val phase = text("phase")
    /** Конкретная стадия внутри фазы. */
    val stage = text("stage")
    /** Статус стадии; по умолчанию "COMPLETED". */
    val status = text("status").default("COMPLETED")
    /** Сериализованная полезная нагрузка чекпойнта (JSON) для возобновления; может отсутствовать. */
    val payloadJson = text("payload_json").nullable()
    /** Момент создания чекпойнта (Unix-время, мс). */
    val createdAt = long("created_at")

    init {
        // Индекс для быстрого поиска чекпойнтов сессии по фазе
        index(false, sessionId, phase)
    }
}
