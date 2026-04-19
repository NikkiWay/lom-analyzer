package com.example.lomanalyzer.observability

import java.util.concurrent.ConcurrentHashMap

class MetricsCollector {
    private val timings = ConcurrentHashMap<String, MutableList<Long>>()

    fun <T> timed(stage: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            val elapsed = (System.nanoTime() - start) / 1_000_000 // ms
            timings.getOrPut(stage) { mutableListOf() }.add(elapsed)
        }
    }

    fun record(stage: String, durationMs: Long) {
        timings.getOrPut(stage) { mutableListOf() }.add(durationMs)
    }

    fun summary(): Map<String, StageSummary> =
        timings.mapValues { (_, durations) ->
            StageSummary(
                count = durations.size,
                totalMs = durations.sum(),
                avgMs = if (durations.isNotEmpty()) durations.average() else 0.0,
                maxMs = durations.maxOrNull() ?: 0,
            )
        }

    fun reset() {
        timings.clear()
    }

    data class StageSummary(
        val count: Int,
        val totalMs: Long,
        val avgMs: Double,
        val maxMs: Long,
    )
}
