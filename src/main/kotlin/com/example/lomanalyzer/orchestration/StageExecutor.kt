/*
 * НАЗНАЧЕНИЕ
 * Единый контракт для исполнителя одного этапа пайплайна. Каждая из 10 стадий
 * (см. PipelineStage и docs/algorithm.md) выполняется через реализацию этого
 * интерфейса, зарегистрированную в PipelineOrchestrator при помощи PipelineWiring.
 *
 * ЧТО ВНУТРИ
 * Функциональный интерфейс (fun interface) StageExecutor с единственным
 * suspend-методом execute. Благодаря fun interface исполнитель можно задавать
 * лямбдой — именно так PipelineWiring регистрирует логику каждого этапа.
 *
 * СВЯЗИ
 * Реализации хранятся в PipelineOrchestrator и вызываются в цикле по стадиям.
 * Метод suspend — выполняется внутри coroutine пайплайна (см. PipelineLauncher).
 */
package com.example.lomanalyzer.orchestration

/**
 * Контракт исполнителя одного этапа пайплайна.
 *
 * Реализуется лямбдой (fun interface). Вызывается оркестратором по очереди для
 * каждой стадии.
 */
fun interface StageExecutor {
    /**
     * Выполнить логику этапа.
     *
     * @param sessionId идентификатор сессии анализа, над которой работает этап.
     * @param stage описание текущей стадии (порядок и имя), см. PipelineStage.
     */
    suspend fun execute(sessionId: Int, stage: PipelineStage)
}
