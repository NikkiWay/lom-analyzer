/*
 * НАЗНАЧЕНИЕ
 * Канал отчёта о прогрессе пайплайна для UI. Оркестратор и исполнители этапов
 * публикуют сюда текущую стадию, число выполненных/всего шагов, ETA, ошибку и
 * признак завершения; Compose-UI подписывается на поток и рисует индикатор
 * прогресса.
 *
 * ЧТО ВНУТРИ
 * data class ProgressEvent (снимок состояния прогресса) и класс ProgressReporter
 * с реактивным StateFlow и методами update (опубликовать событие) и reset
 * (вернуть в начальное состояние).
 *
 * МЕТОД
 * Паттерн «приватный MutableStateFlow + публичный StateFlow»: наружу — только
 * чтение. PipelineOrchestrator вызывает update перед каждой стадией и по итогам
 * (завершение/отмена/ошибка).
 *
 * БИБЛИОТЕКИ
 * kotlinx.coroutines.flow — реактивная передача прогресса в Compose-UI.
 */
package com.example.lomanalyzer.orchestration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Снимок прогресса выполнения пайплайна.
 *
 * @param stage имя текущей стадии (или человекочитаемый статус — «Завершено», «Ошибка»).
 * @param completedItems сколько единиц работы выполнено (например, номер стадии).
 * @param totalItems общее число единиц работы (например, число стадий).
 * @param etaSeconds оценка оставшегося времени в секундах (null, если неизвестна).
 * @param error текст ошибки, если стадия завершилась неуспешно (иначе null).
 * @param finished true, если пайплайн завершён (успех, отмена или ошибка).
 */
data class ProgressEvent(
    val stage: String = "",
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val etaSeconds: Long? = null,
    val error: String? = null,
    val finished: Boolean = false,
)

/** Публикует события прогресса пайплайна для подписчиков из UI. */
class ProgressReporter {
    /** Внутреннее изменяемое состояние прогресса. */
    private val _progress = MutableStateFlow(ProgressEvent())
    /** Публичный read-only поток прогресса для UI. */
    val progress: StateFlow<ProgressEvent> = _progress.asStateFlow()

    /** Публикует новое событие прогресса. */
    fun update(event: ProgressEvent) {
        _progress.value = event
    }

    /** Сбрасывает прогресс в начальное (пустое) состояние. */
    fun reset() {
        _progress.value = ProgressEvent()
    }
}
