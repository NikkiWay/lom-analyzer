/*
 * НАЗНАЧЕНИЕ
 * Глобальный реестр-«швейцар»: гарантирует, что в один момент времени выполняется
 * не более одного анализа. Реализует конечный автомат состояний активной сессии
 * (IDLE → ANALYZING → RECOVERY_AWAITING). Используется PipelineOrchestrator перед
 * стартом, чтобы не запустить два анализа параллельно, и App.kt — для сброса при
 * восстановлении после сбоя.
 *
 * ЧТО ВНУТРИ
 * enum RegistryState (три состояния), исключение AnotherSessionActiveException
 * и класс ActiveSessionRegistry с потокобезопасными методами перехода между
 * состояниями (tryStartAnalysis, transitionToRecovery, endAnalysis, forceReset)
 * и запросами текущего состояния/сессии.
 *
 * МЕТОД
 * Конечный автомат по спецификации v6 §7.2. Все операции с состоянием защищены
 * одним ReentrantLock, поэтому реестр безопасно использовать из разных корутин/потоков
 * (UI и coroutine пайплайна).
 *
 * БИБЛИОТЕКИ
 * java.util.concurrent.locks.ReentrantLock + kotlin.concurrent.withLock —
 * взаимное исключение при чтении/записи состояния.
 */
package com.example.lomanalyzer.orchestration

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Full state machine per v6 §7.2: {IDLE, ANALYZING, RECOVERY_AWAITING}.
 * Состояния реестра: IDLE — свободно; ANALYZING — идёт анализ;
 * RECOVERY_AWAITING — анализ прерван и ожидает восстановления.
 */
enum class RegistryState { IDLE, ANALYZING, RECOVERY_AWAITING }

/**
 * Бросается при попытке запустить анализ, когда другой уже выполняется.
 *
 * @property activeSessionId id уже выполняющейся сессии.
 * @property activeSessionName отображаемое имя уже выполняющейся сессии.
 */
class AnotherSessionActiveException(
    val activeSessionId: Int,
    val activeSessionName: String,
) : RuntimeException("Another session active: #$activeSessionId ($activeSessionName)")

/** Потокобезопасный реестр единственной активной сессии анализа. */
class ActiveSessionRegistry {
    /** Замок, сериализующий все операции над состоянием реестра. */
    private val lock = ReentrantLock()
    /** Текущее состояние конечного автомата. */
    private var state = RegistryState.IDLE
    /** Id активной сессии (-1, если активной нет). */
    private var sessionId = -1
    /** Отображаемое имя активной сессии. */
    private var sessionName = ""

    /** true, если сейчас идёт анализ или ожидается восстановление (не IDLE). */
    val analysisInProgress: Boolean
        get() = lock.withLock { state != RegistryState.IDLE }

    /** Возвращает текущее состояние автомата (под замком). */
    fun getCurrentState(): RegistryState = lock.withLock { state }

    /**
     * Пытается перевести реестр в состояние ANALYZING и «застолбить» сессию.
     * @return true, если переход удался; false, если анализ уже идёт (реестр занят).
     */
    fun tryStartAnalysis(sessionId: Int, sessionName: String = "Session #$sessionId"): Boolean =
        lock.withLock {
            // Если реестр не свободен — отказываем (другой анализ уже выполняется)
            if (state != RegistryState.IDLE) return@withLock false
            this.state = RegistryState.ANALYZING
            this.sessionId = sessionId
            this.sessionName = sessionName
            true
        }

    /** Переводит активный анализ в режим ожидания восстановления (после прерывания). */
    fun transitionToRecovery() = lock.withLock {
        if (state == RegistryState.ANALYZING) {
            state = RegistryState.RECOVERY_AWAITING
        }
    }

    /** Завершает анализ и освобождает реестр, если переданный id совпадает с активным. */
    fun endAnalysis(sessionId: Int) = lock.withLock {
        // Сбрасываем только «свою» сессию, чтобы не обнулить чужую активную
        if (this.sessionId == sessionId) {
            state = RegistryState.IDLE
            this.sessionId = -1
            this.sessionName = ""
        }
    }

    /** @return id активной сессии или null, если реестр свободен. */
    fun currentSessionId(): Int? = lock.withLock {
        if (sessionId >= 0 && state != RegistryState.IDLE) sessionId else null
    }

    /** Безусловный сброс реестра в IDLE — для восстановления при старте приложения. */
    fun forceReset() = lock.withLock {
        state = RegistryState.IDLE
        sessionId = -1
        sessionName = ""
    }
}
