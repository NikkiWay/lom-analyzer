/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для метрик производительности сессии (таблица
 * session_metrics). Фиксирует по каждому этапу пайплайна (stage) длительность
 * выполнения (durationMs), число обработанных элементов и произвольные метаданные.
 * Используется для телеметрии/профилирования. Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс SessionMetricsDao: insert (записать метрику этапа и вернуть id),
 * findBySession (выборка метрик сессии).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов. java.time.Instant — created_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица SessionMetricsTable (storage/tables). Привязка к сессии через sessionId.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.SessionMetricsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO метрик сессии. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class SessionMetricsDao(private val db: Database) {
    /**
     * INSERT метрики выполнения этапа пайплайна.
     * @param stage имя этапа пайплайна.
     * @param durationMs длительность выполнения этапа в миллисекундах.
     * @param itemsProcessed число обработанных элементов или NULL.
     * @param metadataJson дополнительные метаданные в JSON или NULL.
     * @return сгенерированный id строки метрики.
     */
    fun insert(
        sessionId: Int,
        stage: String,
        durationMs: Int,
        itemsProcessed: Int? = null,
        metadataJson: String? = null,
    ): Int = transaction(db) {
        SessionMetricsTable.insertAndGetId {
            it[SessionMetricsTable.sessionId] = sessionId
            it[SessionMetricsTable.stage] = stage
            it[SessionMetricsTable.durationMs] = durationMs
            it[SessionMetricsTable.itemsProcessed] = itemsProcessed
            it[SessionMetricsTable.metadataJson] = metadataJson
            it[createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    /**
     * SELECT всех метрик сессии.
     * @return список ResultRow.
     */
    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        SessionMetricsTable.selectAll()
            .where { SessionMetricsTable.sessionId eq sessionId }
            .toList()
    }
}
