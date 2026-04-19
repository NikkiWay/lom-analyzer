package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.CheckpointDao
import com.example.lomanalyzer.storage.tables.CollectionCheckpoints
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder

class CheckpointManager(
    private val checkpointDao: CheckpointDao,
    private val logger: Logger,
) {
    fun saveCheckpoint(sessionId: Int, stage: String, payload: String?) {
        checkpointDao.insert(
            sessionId = sessionId,
            endpoint = stage,
            ownerId = 0,
        ).also { id ->
            if (payload != null) {
                checkpointDao.updateProgress(id, payload, 0, "CHECKPOINTED")
            } else {
                checkpointDao.updateProgress(id, null, 0, "CHECKPOINTED")
            }
        }
        logger.event(AppEvent.CHECKPOINT_SAVED, mapOf(
            "session_id" to sessionId,
            "stage" to stage,
        ))
    }

    fun loadLastCheckpoint(sessionId: Int): CheckpointData? {
        val rows = checkpointDao.findBySession(sessionId)
        val last = rows
            .filter { it[CollectionCheckpoints.status] == "CHECKPOINTED" }
            .maxByOrNull { it[CollectionCheckpoints.updatedAt] }
            ?: return null

        return CheckpointData(
            sessionId = sessionId,
            stage = last[CollectionCheckpoints.endpoint],
            payload = last[CollectionCheckpoints.offsetValue],
        )
    }
}

data class CheckpointData(
    val sessionId: Int,
    val stage: String,
    val payload: String?,
)
