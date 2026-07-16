/*
 * НАЗНАЧЕНИЕ
 * Запуск и отмена пайплайна в фоновой coroutine, живущей на уровне всего
 * приложения (а не экрана). Благодаря собственному CoroutineScope анализ не
 * прерывается при навигации между экранами Compose. Точка входа для UI: «Запустить
 * анализ» → launch, «Отмена» → cancel.
 *
 * ЧТО ВНУТРИ
 * Класс PipelineLauncher с приватным scope (SupervisorJob + Dispatchers.IO),
 * ссылкой на текущую задачу activeJob и методами launch / cancel.
 *
 * МЕТОД
 * launch сбрасывает флаг отмены, фиксирует активную сессию в ActiveSessionHolder
 * и запускает orchestrate в IO-диспетчере. cancel взводит кооперативный флаг
 * (CancellationController) и дополнительно отменяет coroutine. SupervisorJob —
 * чтобы сбой одной задачи не «ронял» весь scope.
 *
 * БИБЛИОТЕКИ
 * kotlinx.coroutines (CoroutineScope, SupervisorJob, Dispatchers.IO, Job, launch)
 * — управление фоновой корутиной пайплайна.
 */
package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scoped launcher for the pipeline.
 * The coroutine survives UI navigation changes.
 * Запускает пайплайн в фоновой coroutine уровня приложения; анализ переживает
 * смену экранов UI.
 */
class PipelineLauncher(
    private val orchestrator: PipelineOrchestrator,
    private val cancellationController: CancellationController,
    private val sessionHolder: ActiveSessionHolder,
    private val logger: Logger,
) {
    /** Долгоживущий scope пайплайна: SupervisorJob (изоляция сбоев) + IO-диспетчер. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Текущая фоновая задача анализа (или null, если ничего не выполняется). */
    private var activeJob: Job? = null

    /** Запускает анализ сессии: сбрасывает отмену, фиксирует сессию, стартует orchestrate. */
    fun launch(sessionId: Int) {
        // Сбрасываем флаг отмены от предыдущего запуска
        cancellationController.reset()
        // Запоминаем активную сессию, чтобы UI знал, чьи данные показывать
        sessionHolder.set(sessionId)
        // Запускаем оркестратор в фоновой coroutine (IO)
        activeJob = scope.launch {
            orchestrator.orchestrate(sessionId)
        }
    }

    /** Отменяет анализ: взводит кооперативный флаг и отменяет фоновую coroutine. */
    fun cancel() {
        cancellationController.cancel()
        activeJob?.cancel()
        activeJob = null
    }
}
