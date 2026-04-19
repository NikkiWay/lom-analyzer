package com.example.lomanalyzer.orchestration

import java.util.concurrent.atomic.AtomicBoolean

class CancellationException(message: String = "Analysis cancelled") : RuntimeException(message)

class CancellationController {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun reset() {
        cancelled.set(false)
    }

    fun checkCancelled() {
        if (cancelled.get()) {
            throw CancellationException()
        }
    }

    fun isCancelled(): Boolean = cancelled.get()
}
