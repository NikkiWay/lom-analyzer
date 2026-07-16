/*
 * НАЗНАЧЕНИЕ
 * Публичный контракт (интерфейс) UI-модуля — модуля 1 архитектуры
 * (диплом 2.2.3). UI читает все данные из SQLite; эта «шина» (bus) служит
 * каналом уведомлений, чтобы UI знал, когда обновить отображение.
 *
 * ЧТО ВНУТРИ
 * Интерфейс UiBus с тремя методами уведомлений: об обновлении данных сессии,
 * о готовности результатов анализа и об ошибке пайплайна.
 *
 * СВЯЗИ
 * Данные UI получает из SQLite (изоляция модулей, docs/architecture.md), а через
 * UiBus приходит лишь сигнал «пора обновиться». Это снимает прямую связь между
 * аналитическим ядром/сбором и UI.
 */
package com.example.lomanalyzer.core

/**
 * Public contract for the UI module (diploma 2.2.3, module 1).
 * UI reads all data from SQLite. This bus provides event notifications
 * so the UI knows when to refresh.
 *
 * Публичный контракт UI-модуля (модуль 1). UI читает все данные из SQLite, а эта
 * шина уведомлений сообщает UI, когда нужно обновиться.
 */
interface UiBus {
    /**
     * Уведомить UI, что данные сессии обновлены.
     *
     * Notify UI that session data has been updated
     *
     * @param sessionId идентификатор обновлённой сессии.
     */
    fun notifySessionUpdated(sessionId: Int)

    /**
     * Уведомить UI, что результаты анализа готовы к показу.
     *
     * Notify UI that analysis results are ready for display
     *
     * @param sessionId идентификатор сессии с готовыми результатами.
     */
    fun notifyResultsReady(sessionId: Int)

    /**
     * Уведомить UI об ошибке пайплайна.
     *
     * Notify UI of a pipeline error
     *
     * @param sessionId идентификатор сессии, в которой произошла ошибка.
     * @param stage название этапа/стадии, на котором возникла ошибка.
     * @param message текст сообщения об ошибке для показа пользователю.
     */
    fun notifyError(sessionId: Int, stage: String, message: String)
}
