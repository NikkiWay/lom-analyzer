package com.example.lomanalyzer.orchestration

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Full state machine per v6 §7.2: {IDLE, ANALYZING, RECOVERY_AWAITING}.
 */
enum class RegistryState { IDLE, ANALYZING, RECOVERY_AWAITING }

class AnotherSessionActiveException(
    val activeSessionId: Int,
    val activeSessionName: String,
) : RuntimeException("Another session active: #$activeSessionId ($activeSessionName)")

class ActiveSessionRegistry {
    private val lock = ReentrantLock()
    private var state = RegistryState.IDLE
    private var sessionId = -1
    private var sessionName = ""

    val analysisInProgress: Boolean
        get() = lock.withLock { state != RegistryState.IDLE }

    fun getCurrentState(): RegistryState = lock.withLock { state }

    fun tryStartAnalysis(sessionId: Int, sessionName: String = "Session #$sessionId"): Boolean =
        lock.withLock {
            if (state != RegistryState.IDLE) return@withLock false
            this.state = RegistryState.ANALYZING
            this.sessionId = sessionId
            this.sessionName = sessionName
            true
        }

    fun transitionToRecovery() = lock.withLock {
        if (state == RegistryState.ANALYZING) {
            state = RegistryState.RECOVERY_AWAITING
        }
    }

    fun endAnalysis(sessionId: Int) = lock.withLock {
        if (this.sessionId == sessionId) {
            state = RegistryState.IDLE
            this.sessionId = -1
            this.sessionName = ""
        }
    }

    fun currentSessionId(): Int? = lock.withLock {
        if (sessionId >= 0 && state != RegistryState.IDLE) sessionId else null
    }

    fun forceReset() = lock.withLock {
        state = RegistryState.IDLE
        sessionId = -1
        sessionName = ""
    }
}
