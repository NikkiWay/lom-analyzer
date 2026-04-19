package com.example.lomanalyzer.orchestration

import java.util.concurrent.atomic.AtomicInteger

class ActiveSessionRegistry {
    private val activeSessionId = AtomicInteger(-1)

    val analysisInProgress: Boolean
        get() = activeSessionId.get() >= 0

    fun tryStartAnalysis(sessionId: Int): Boolean =
        activeSessionId.compareAndSet(-1, sessionId)

    fun endAnalysis(sessionId: Int) {
        activeSessionId.compareAndSet(sessionId, -1)
    }

    fun currentSessionId(): Int? {
        val id = activeSessionId.get()
        return if (id >= 0) id else null
    }
}
