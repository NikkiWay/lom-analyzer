/*
 * НАЗНАЧЕНИЕ
 * Связующее звено между пайплайном (coroutine) и Compose-UI: хранит id текущей
 * (или последней завершённой) сессии и «версию данных». UI подписывается на эти
 * StateFlow, чтобы знать, какую сессию показывать и когда перечитать результаты
 * из БД.
 *
 * ЧТО ВНУТРИ
 * Класс ActiveSessionHolder с двумя реактивными состояниями: sessionId (Int?)
 * и dataVersion (Long). Методы set/clear меняют активную сессию,
 * notifyDataChanged инкрементирует версию данных.
 *
 * МЕТОД
 * Паттерн «приватный MutableStateFlow + публичный StateFlow (asStateFlow)»:
 * наружу отдаётся только read-only поток, менять состояние можно лишь методами
 * класса. Инкремент dataVersion заставляет Compose-блоки remember(sessionId,
 * dataVersion) пересчитаться и подхватить свежие результаты.
 *
 * БИБЛИОТЕКИ
 * kotlinx.coroutines.flow (MutableStateFlow / StateFlow) — реактивное состояние,
 * совместимое с Compose.
 */
package com.example.lomanalyzer.orchestration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the ID of the session currently being analyzed or last completed.
 * UI screens read from this to know which session's data to display.
 * Хранит id анализируемой (или последней завершённой) сессии для UI.
 *
 * [dataVersion] is incremented every time pipeline writes new data, so that
 * Compose `remember(sessionId, dataVersion)` blocks re-execute and pick up
 * fresh results from the database.
 * [dataVersion] увеличивается при каждой записи новых данных пайплайном, чтобы
 * Compose перечитал свежие результаты из БД.
 */
class ActiveSessionHolder {
    /** Внутреннее изменяемое состояние id активной сессии (null — нет активной). */
    private val _sessionId = MutableStateFlow<Int?>(null)
    /** Публичный read-only поток id активной сессии для подписки из UI. */
    val sessionId: StateFlow<Int?> = _sessionId.asStateFlow()

    /** Внутренний счётчик версии данных (увеличивается при появлении новых результатов). */
    private val _dataVersion = MutableStateFlow(0L)
    /** Публичный read-only поток версии данных для перерисовки UI. */
    val dataVersion: StateFlow<Long> = _dataVersion.asStateFlow()

    /** Устанавливает id активной сессии. */
    fun set(id: Int) {
        _sessionId.value = id
    }

    /** Сбрасывает активную сессию (нет выбранной). */
    fun clear() {
        _sessionId.value = null
    }

    /** Signal UI screens that fresh data is available. */
    /** Сигнал UI о появлении свежих данных — инкремент версии. */
    fun notifyDataChanged() {
        _dataVersion.value++
    }
}
