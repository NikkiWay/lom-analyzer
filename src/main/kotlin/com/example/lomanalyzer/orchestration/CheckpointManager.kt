/*
 * НАЗНАЧЕНИЕ
 * Сохранение и чтение контрольных точек (checkpoint) пайплайна. Контрольные точки
 * позволяют понять, на каком этапе остановился/упал анализ, и используются при
 * отмене и ошибках (PipelineOrchestrator сохраняет точку в catch-блоках). Это
 * низкоуровневый менеджер поверх таблицы collection_checkpoints; в самом пайплайне
 * (PipelineWiring) для фаз PHASE_1..PHASE_5 используется отдельный CheckpointService.
 *
 * ЧТО ВНУТРИ
 * Класс CheckpointManager с методами saveCheckpoint (записать точку) и
 * loadLastCheckpoint (прочитать последнюю), а также data class CheckpointData
 * (id сессии, имя стадии, полезная нагрузка).
 *
 * МЕТОД
 * Точка пишется как запись в collection_checkpoints со статусом CHECKPOINTED;
 * payload (произвольная строка, например имя стадии или текст ошибки) хранится
 * в поле offsetValue. Последняя точка — запись со статусом CHECKPOINTED с
 * максимальным updatedAt.
 *
 * БИБЛИОТЕКИ
 * Exposed ORM (CheckpointDao, таблица CollectionCheckpoints) — доступ к БД.
 * Logger — событие CHECKPOINT_SAVED для наблюдаемости.
 */
package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.CheckpointDao
import com.example.lomanalyzer.storage.tables.CollectionCheckpoints
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder

/**
 * Управляет контрольными точками пайплайна (запись/чтение) в БД.
 *
 * @param checkpointDao DAO таблицы контрольных точек.
 * @param logger логгер для события сохранения точки.
 */
class CheckpointManager(
    private val checkpointDao: CheckpointDao,
    private val logger: Logger,
) {
    /**
     * Сохраняет контрольную точку для сессии.
     * @param stage имя стадии, на которой ставится точка.
     * @param payload произвольные данные точки (имя стадии, текст ошибки) или null.
     */
    fun saveCheckpoint(sessionId: Int, stage: String, payload: String?) {
        // Создаём новую запись точки; ownerId=0 — не привязана к конкретному владельцу VK
        checkpointDao.insert(
            sessionId = sessionId,
            endpoint = stage,
            ownerId = 0,
        ).also { id ->
            // Записываем payload (если есть) и помечаем точку статусом CHECKPOINTED
            if (payload != null) {
                checkpointDao.updateProgress(id, payload, 0, "CHECKPOINTED")
            } else {
                checkpointDao.updateProgress(id, null, 0, "CHECKPOINTED")
            }
        }
        // Фиксируем событие сохранения точки для журнала наблюдаемости
        logger.event(AppEvent.CHECKPOINT_SAVED, mapOf(
            "session_id" to sessionId,
            "stage" to stage,
        ))
    }

    /**
     * Возвращает последнюю сохранённую контрольную точку сессии или null, если её нет.
     */
    fun loadLastCheckpoint(sessionId: Int): CheckpointData? {
        // Берём все точки сессии и оставляем только корректно сохранённые
        val rows = checkpointDao.findBySession(sessionId)
        val last = rows
            .filter { it[CollectionCheckpoints.status] == "CHECKPOINTED" }
            // Самая поздняя точка — по времени обновления
            .maxByOrNull { it[CollectionCheckpoints.updatedAt] }
            ?: return null

        // Переносим поля записи в удобную модель CheckpointData
        return CheckpointData(
            sessionId = sessionId,
            stage = last[CollectionCheckpoints.endpoint],
            payload = last[CollectionCheckpoints.offsetValue],
        )
    }
}

/**
 * Прочитанная контрольная точка.
 *
 * @param sessionId id сессии.
 * @param stage имя стадии, на которой была поставлена точка.
 * @param payload сохранённые данные точки (или null).
 */
data class CheckpointData(
    val sessionId: Int,
    val stage: String,
    val payload: String?,
)
