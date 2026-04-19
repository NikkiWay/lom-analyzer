package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class PipelineOrchestrator(
    private val sessionManager: SessionManager,
    private val registry: ActiveSessionRegistry,
    private val checkpointManager: CheckpointManager,
    private val progressReporter: ProgressReporter,
    private val cancellationController: CancellationController,
    private val logger: Logger,
) {
    private val executors = mutableMapOf<String, StageExecutor>()

    fun registerExecutor(stageName: String, executor: StageExecutor) {
        executors[stageName] = executor
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun orchestrate(sessionId: Int) {
        if (!registry.tryStartAnalysis(sessionId)) {
            logger.warn("Analysis already in progress, cannot start session $sessionId")
            return
        }
        logger.event(AppEvent.ANALYSIS_STARTED, mapOf("session_id" to sessionId))
        val stages = PipelineStage.allStages
        val totalStages = stages.size

        try {
            for ((index, stage) in stages.withIndex()) {
                coroutineContext.ensureActive()
                cancellationController.checkCancelled()

                progressReporter.update(
                    ProgressEvent(
                        stage = stage.name,
                        completedItems = index,
                        totalItems = totalStages,
                    )
                )

                val executor = executors[stage.name]
                if (executor != null) {
                    executor.execute(sessionId, stage)
                }
            }

            sessionManager.updateStatus(sessionId, SessionStatus.COMPLETED)
            logger.event(AppEvent.ANALYSIS_COMPLETED, mapOf("session_id" to sessionId))
        } catch (e: CancellationException) {
            sessionManager.updateStatus(sessionId, SessionStatus.CANCELLED)
            logger.event(AppEvent.ANALYSIS_CANCELLED, mapOf("session_id" to sessionId))
            checkpointManager.saveCheckpoint(
                sessionId,
                stages.getOrNull(findCurrentStageIndex(stages))?.name ?: "UNKNOWN",
                null,
            )
            throw e
        } catch (e: Exception) {
            sessionManager.updateStatus(sessionId, SessionStatus.FAILED)
            val stageName = "UNKNOWN"
            checkpointManager.saveCheckpoint(sessionId, stageName, e.message)
            logger.error("Pipeline failed at stage $stageName for session $sessionId", e)
            throw e
        } finally {
            registry.endAnalysis(sessionId)
        }
    }

    private fun findCurrentStageIndex(stages: List<PipelineStage>): Int =
        stages.size - 1
}
