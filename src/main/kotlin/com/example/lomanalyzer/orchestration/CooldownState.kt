/*
 * НАЗНАЧЕНИЕ
 * Наблюдаемое состояние «паузы» (cooldown) между обращениями к VK API. VK
 * ограничивает интенсивные серии запросов (flood control), поэтому пайплайн
 * вынужден выжидать. Это состояние показывает в UI обратный отсчёт и сообщение
 * о пропуске этапа сбора. Используется на этапе DataCollection (см. PipelineWiring).
 *
 * ЧТО ВНУТРИ
 * Класс CooldownState с реактивными полями: оставшиеся/всего секунды, подпись и
 * подзаголовок карточки отсчёта, сообщение о пропущенной фазе. Методы:
 * startCountdown, tick, clear, notifyPhaseSkipped, dismissPhaseSkipped.
 *
 * МЕТОД
 * Поддерживает два режима пауз: 1) общий cooldown перед Phase C (5 минут);
 * 2) задержка между авторами во время Phase C (2 минуты на автора). Паттерн
 * «приватный MutableStateFlow + публичный StateFlow» — наружу только чтение.
 *
 * БИБЛИОТЕКИ
 * kotlinx.coroutines.flow — реактивная передача состояния отсчёта в Compose-UI.
 */
package com.example.lomanalyzer.orchestration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable cooldown state shared between the pipeline (coroutine)
 * and the UI (Compose). Supports two modes:
 * 1. Pre-Phase-C cooldown (5 min)
 * 2. Per-author delay during Phase C (2 min between each author)
 * Наблюдаемое состояние паузы VK API, общее для пайплайна и UI. Два режима:
 * 1) пауза перед Phase C; 2) задержка между авторами в Phase C.
 */
class CooldownState {
    /** Внутреннее состояние: сколько секунд осталось до конца отсчёта. */
    private val _remainingSeconds = MutableStateFlow(0)
    /** Публичный read-only поток оставшихся секунд для UI. */
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    /** Total seconds for the current countdown (for progress bar calculation). */
    /** Всего секунд в текущем отсчёте (для расчёта заполнения прогресс-бара). */
    private val _totalSeconds = MutableStateFlow(0)
    /** Публичный read-only поток общего числа секунд отсчёта. */
    val totalSeconds: StateFlow<Int> = _totalSeconds.asStateFlow()

    /** Label shown on the countdown card. */
    /** Заголовок, отображаемый на карточке обратного отсчёта. */
    private val _label = MutableStateFlow("")
    /** Публичный read-only поток заголовка отсчёта. */
    val label: StateFlow<String> = _label.asStateFlow()

    /** Subtitle shown below the label. */
    /** Подзаголовок под заголовком карточки отсчёта. */
    private val _subtitle = MutableStateFlow("")
    /** Публичный read-only поток подзаголовка отсчёта. */
    val subtitle: StateFlow<String> = _subtitle.asStateFlow()

    /** Non-null when Phase C was skipped due to flood control. */
    /** Не-null, если Phase C был пропущен из-за flood control VK. */
    private val _phaseSkippedMessage = MutableStateFlow<String?>(null)
    /** Публичный read-only поток сообщения о пропуске фазы. */
    val phaseSkippedMessage: StateFlow<String?> = _phaseSkippedMessage.asStateFlow()

    /**
     * Запускает обратный отсчёт: задаёт общее число секунд, тексты заголовка и
     * подзаголовка, выставляет оставшееся время равным общему.
     */
    fun startCountdown(total: Int, label: String, subtitle: String) {
        _totalSeconds.value = total
        _remainingSeconds.value = total
        _label.value = label
        _subtitle.value = subtitle
    }

    /** Обновляет оставшееся время отсчёта (вызывается раз в секунду из пайплайна). */
    fun tick(remaining: Int) {
        _remainingSeconds.value = remaining
    }

    /** Сбрасывает все поля отсчёта в исходное состояние (пауза завершена). */
    fun clear() {
        _remainingSeconds.value = 0
        _totalSeconds.value = 0
        _label.value = ""
        _subtitle.value = ""
    }

    /** Публикует сообщение о пропуске фазы сбора (показывается в UI). */
    fun notifyPhaseSkipped(message: String) {
        _phaseSkippedMessage.value = message
    }

    /** Скрывает сообщение о пропуске фазы (пользователь закрыл уведомление). */
    fun dismissPhaseSkipped() {
        _phaseSkippedMessage.value = null
    }
}
