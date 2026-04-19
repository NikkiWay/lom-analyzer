package com.example.lomanalyzer.orchestration

fun interface StageExecutor {
    suspend fun execute(sessionId: Int, stage: PipelineStage)
}
