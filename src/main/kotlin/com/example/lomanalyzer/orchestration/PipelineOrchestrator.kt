/*
 * НАЗНАЧЕНИЕ
 * Дирижёр пайплайна: последовательно выполняет все 10 стадий (PipelineStage) для
 * одной сессии, докладывает прогресс, обрабатывает отмену и ошибки, ставит
 * контрольные точки и обновляет статус сессии. Центральный узел оркестрации:
 * исполнители стадий регистрируются здесь через PipelineWiring, а запускается
 * orchestrate из PipelineLauncher.
 *
 * ЧТО ВНУТРИ
 * Класс PipelineOrchestrator: реестр исполнителей (executors), метод регистрации
 * registerExecutor и главный suspend-метод orchestrate с циклом по стадиям и
 * обработкой исключений.
 *
 * АЛГОРИТМ / ПОТОК ВЫПОЛНЕНИЯ
 * 1) Захват реестра активной сессии (tryStartAnalysis) — не запускать два анализа.
 * 2) Цикл по PipelineStage.allStages: на каждой итерации проверка отмены
 *    (ensureActive + checkCancelled), публикация прогресса, вызов исполнителя.
 * 3) Успех → статус COMPLETED. Отмена (CancellationException) → статус CANCELLED,
 *    checkpoint, проброс исключения. Иная ошибка → статус FAILED, checkpoint с
 *    текстом ошибки (без проброса). finally → освобождение реестра.
 *
 * БИБЛИОТЕКИ
 * kotlinx.coroutines (ensureActive, coroutineContext) — кооперативная отмена на
 * уровне coroutine; Logger — события ANALYSIS_STARTED/COMPLETED/CANCELLED.
 *
 * СВЯЗИ
 * SessionManager (статусы), ActiveSessionRegistry (один анализ за раз),
 * CheckpointManager (точки при отмене/ошибке), ProgressReporter (UI),
 * CancellationController (отмена).
 */
package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Оркестратор пайплайна: выполняет стадии по очереди и управляет состоянием анализа.
 */
class PipelineOrchestrator(
    private val sessionManager: SessionManager,
    private val registry: ActiveSessionRegistry,
    private val checkpointManager: CheckpointManager,
    private val progressReporter: ProgressReporter,
    private val cancellationController: CancellationController,
    private val logger: Logger,
) {
    /** Реестр исполнителей по имени стадии (заполняется PipelineWiring.wire). */
    private val executors = mutableMapOf<String, StageExecutor>()

    /** Регистрирует исполнителя для стадии с заданным именем. */
    fun registerExecutor(stageName: String, executor: StageExecutor) {
        executors[stageName] = executor
    }

    /**
     * Главный метод оркестрации: прогоняет все стадии для сессии sessionId.
     * Управляет прогрессом, отменой, ошибками, статусами и контрольными точками.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun orchestrate(sessionId: Int) {
        // «Швейцар»: пытаемся занять реестр. Если занят — другой анализ уже идёт, выходим
        if (!registry.tryStartAnalysis(sessionId)) {
            progressReporter.update(ProgressEvent(
                stage = "Ошибка",
                error = "Другой анализ уже запущен",
                finished = true,
            ))
            logger.warn("Analysis already in progress, cannot start session $sessionId")
            return
        }
        logger.event(AppEvent.ANALYSIS_STARTED, mapOf("session_id" to sessionId))
        // Полный список стадий по порядку и их количество для прогресса
        val stages = PipelineStage.allStages
        val totalStages = stages.size
        // Имя текущей стадии — нужно в catch для checkpoint и сообщения об ошибке
        var currentStageName = "UNKNOWN"

        try {
            // Главный цикл: выполняем стадии строго по порядку
            for ((index, stage) in stages.withIndex()) {
                // Две точки проверки отмены: на уровне coroutine и кооперативного флага
                coroutineContext.ensureActive()
                cancellationController.checkCancelled()
                currentStageName = stage.name

                // Сообщаем UI, какая стадия началась и сколько уже пройдено
                progressReporter.update(
                    ProgressEvent(
                        stage = stage.name,
                        completedItems = index,
                        totalItems = totalStages,
                    )
                )

                // Находим зарегистрированного исполнителя стадии; если его нет — пропускаем
                val executor = executors[stage.name]
                if (executor != null) {
                    executor.execute(sessionId, stage)
                }
            }

            // Все стадии прошли успешно — помечаем сессию завершённой
            sessionManager.updateStatus(sessionId, SessionStatus.COMPLETED)
            logger.event(AppEvent.ANALYSIS_COMPLETED, mapOf("session_id" to sessionId))

            // Финальное событие прогресса: 100% и флаг finished
            progressReporter.update(ProgressEvent(
                stage = "Завершено",
                completedItems = totalStages,
                totalItems = totalStages,
                finished = true,
            ))
        } catch (e: CancellationException) {
            // Отмена пользователем: статус CANCELLED, сохраняем точку и пробрасываем дальше
            sessionManager.updateStatus(sessionId, SessionStatus.CANCELLED)
            logger.event(AppEvent.ANALYSIS_CANCELLED, mapOf("session_id" to sessionId))
            checkpointManager.saveCheckpoint(sessionId, currentStageName, null)

            progressReporter.update(ProgressEvent(
                stage = "Отменено",
                error = "Анализ отменён пользователем",
                finished = true,
            ))
            // Пробрасываем, чтобы coroutine корректно завершилась как отменённая
            throw e
        } catch (e: Exception) {
            // Любая другая ошибка: статус FAILED, в точку пишем текст ошибки (не пробрасываем)
            sessionManager.updateStatus(sessionId, SessionStatus.FAILED)
            checkpointManager.saveCheckpoint(sessionId, currentStageName, e.message)
            logger.error("Pipeline failed at stage $currentStageName for session $sessionId", e)

            progressReporter.update(ProgressEvent(
                stage = currentStageName,
                error = e.message ?: "Неизвестная ошибка",
                finished = true,
            ))
        } finally {
            // В любом исходе освобождаем реестр, чтобы можно было запустить новый анализ
            registry.endAnalysis(sessionId)
        }
    }
}
