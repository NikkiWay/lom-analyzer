package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.*

/**
 * 30-minute timeout for RECOVERY_AWAITING per v6 §6.5.
 * If user doesn't respond, auto-cancel and mark CANCELLED with reason RECOVERY_TIMEOUT.
 */
class RecoveryTimeoutWatcher(
    private val registry: ActiveSessionRegistry,
    private val sessionManager: SessionManager,
    private val logger: Logger,
    private val timeoutMs: Long = 30 * 60 * 1000L,
) {
    private var watchJob: Job? = null

    fun startWatching(sessionId: Int, scope: CoroutineScope) {
        watchJob?.cancel()
        watchJob = scope.launch {
            delay(timeoutMs)
            if (registry.getCurrentState() == RegistryState.RECOVERY_AWAITING) {
                sessionManager.updateStatus(sessionId, SessionStatus.CANCELLED)
                registry.forceReset()
                logger.event(AppEvent.RECOVERY_TIMEOUT, mapOf("session_id" to sessionId))
            }
        }
    }

    fun cancelWatch() {
        watchJob?.cancel()
        watchJob = null
    }
}
