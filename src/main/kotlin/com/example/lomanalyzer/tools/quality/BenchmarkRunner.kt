package com.example.lomanalyzer.tools.quality

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StageTiming(val stage: String, val durationMs: Long)

@Serializable
data class BenchmarkResult(
    val timestamp: Long = System.currentTimeMillis(),
    val totalMs: Long,
    val stages: List<StageTiming>,
    val postCount: Int,
    val authorCount: Int,
)

object BenchmarkRunner {
    private val json = Json { prettyPrint = true }

    fun compare(current: BenchmarkResult, baseline: BenchmarkResult): List<String> {
        val regressions = mutableListOf<String>()
        val baselineMap = baseline.stages.associateBy { it.stage }

        for (stage in current.stages) {
            val base = baselineMap[stage.stage] ?: continue
            if (base.durationMs > 0) {
                val ratio = stage.durationMs.toDouble() / base.durationMs
                if (ratio > 1.20) {
                    val pct = ((ratio - 1.0) * 100).toInt()
                    regressions.add("${stage.stage}: +$pct% regression " +
                        "(${base.durationMs}ms → ${stage.durationMs}ms)")
                }
            }
        }
        return regressions
    }

    fun toJson(result: BenchmarkResult): String = json.encodeToString(result)

    fun fromJson(jsonStr: String): BenchmarkResult = json.decodeFromString(jsonStr)

    fun generateReport(
        result: BenchmarkResult,
        regressions: List<String>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Benchmark Report")
        sb.appendLine()
        sb.appendLine("- Posts: ${result.postCount}")
        sb.appendLine("- Authors: ${result.authorCount}")
        sb.appendLine("- Total: ${result.totalMs}ms")
        sb.appendLine()
        sb.appendLine("## Stage Timings")
        sb.appendLine("| Stage | Duration (ms) |")
        sb.appendLine("|-------|--------------|")
        for (s in result.stages) {
            sb.appendLine("| ${s.stage} | ${s.durationMs} |")
        }
        if (regressions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Regressions (> 20%)")
            for (r in regressions) sb.appendLine("- $r")
        }
        return sb.toString()
    }
}
