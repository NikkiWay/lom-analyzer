/*
 * НАЗНАЧЕНИЕ
 * Управление контрольными точками пайплайна (модуль сбора данных). Сохраняет прогресс
 * по фазам в таблицу pipeline_checkpoint, чтобы прерванную сессию можно было возобновить
 * с последней успешной точки (см. docs/algorithm.md, раздел «Контрольные точки»).
 *
 * ЧТО ВНУТРИ
 * Класс CheckpointService: saveCheckpoint (запись точки), getLastCheckpoint (последняя точка
 * сессии), getCheckpoints (все точки в хронологии) и data class CheckpointData.
 *
 * ФРЕЙМВОРКИ / СВЯЗИ
 * Exposed ORM: операции insert/selectAll в транзакции transaction(db) над таблицей
 * PipelineCheckpoints. Logger фиксирует событие CHECKPOINT_SAVED. Используется коллекторами
 * (например, AuthorWallCollector — каждые 10 авторов) и оркестратором пайплайна.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.tables.PipelineCheckpoints
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Manages 6 pipeline checkpoints (diploma 2.2.5):
 *  1. After Phase 1 (task setup)
 *  2. After Phase 2 (data collection) — plus intermediate every 10 authors in stage 4
 *  3. After Phase 3 (data processing)
 *  4. After Phase 4 (scores & inference)
 *  5. After Phase 5 (classification & result)
 *  6. Intermediate: after every 10 authors in stage 4 (long sessions)
 *
 * On startup, the app checks for interrupted sessions and offers to resume
 * from the last successful checkpoint.
 */
class CheckpointService(
    private val db: Database,
    private val logger: Logger,
) {
    /**
     * Сохраняет контрольную точку со статусом COMPLETED.
     * @param phase фаза пайплайна (например, PHASE_2), stage — конкретный шаг внутри фазы.
     * @param payload опциональные данные состояния (JSON) для возобновления.
     */
    fun saveCheckpoint(sessionId: Int, phase: String, stage: String, payload: String? = null) {
        // Запись точки выполняется в транзакции Exposed
        transaction(db) {
            PipelineCheckpoints.insert {
                it[PipelineCheckpoints.sessionId] = sessionId
                it[PipelineCheckpoints.phase] = phase
                it[PipelineCheckpoints.stage] = stage
                it[PipelineCheckpoints.status] = "COMPLETED"
                it[PipelineCheckpoints.payloadJson] = payload
                it[PipelineCheckpoints.createdAt] = Instant.now().toEpochMilli()
            }
        }
        logger.event(AppEvent.CHECKPOINT_SAVED, mapOf(
            "session_id" to sessionId,
            "phase" to phase,
            "stage" to stage,
        ))
    }

    /**
     * Возвращает последнюю по времени контрольную точку сессии или null, если их нет.
     * Используется при возобновлении прерванной сессии.
     */
    fun getLastCheckpoint(sessionId: Int): CheckpointData? = transaction(db) {
        // Берём самую свежую точку: сортировка по времени убыванием, лимит 1
        PipelineCheckpoints.selectAll()
            .where { PipelineCheckpoints.sessionId eq sessionId }
            .orderBy(PipelineCheckpoints.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let {
                CheckpointData(
                    phase = it[PipelineCheckpoints.phase],
                    stage = it[PipelineCheckpoints.stage],
                    status = it[PipelineCheckpoints.status],
                    payload = it[PipelineCheckpoints.payloadJson],
                    timestamp = it[PipelineCheckpoints.createdAt],
                )
            }
    }

    /** Возвращает все контрольные точки сессии в хронологическом порядке. */
    fun getCheckpoints(sessionId: Int): List<CheckpointData> = transaction(db) {
        PipelineCheckpoints.selectAll()
            .where { PipelineCheckpoints.sessionId eq sessionId }
            .orderBy(PipelineCheckpoints.createdAt)
            .map {
                CheckpointData(
                    phase = it[PipelineCheckpoints.phase],
                    stage = it[PipelineCheckpoints.stage],
                    status = it[PipelineCheckpoints.status],
                    payload = it[PipelineCheckpoints.payloadJson],
                    timestamp = it[PipelineCheckpoints.createdAt],
                )
            }
    }

    /** Снимок одной контрольной точки: фаза, шаг, статус, опциональные данные и метка времени (мс). */
    data class CheckpointData(
        val phase: String,
        val stage: String,
        val status: String,
        val payload: String?,
        val timestamp: Long,
    )
}
