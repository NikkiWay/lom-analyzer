package com.example.lomanalyzer.orchestration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProgressEvent(
    val stage: String = "",
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val etaSeconds: Long? = null,
)

class ProgressReporter {
    private val _progress = MutableStateFlow(ProgressEvent())
    val progress: StateFlow<ProgressEvent> = _progress.asStateFlow()

    fun update(event: ProgressEvent) {
        _progress.value = event
    }

    fun reset() {
        _progress.value = ProgressEvent()
    }
}
