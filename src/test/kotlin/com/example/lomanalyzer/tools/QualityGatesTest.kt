package com.example.lomanalyzer.tools

import com.example.lomanalyzer.tools.quality.BenchmarkRunner
import com.example.lomanalyzer.tools.quality.BenchmarkResult
import com.example.lomanalyzer.tools.quality.CohenKappa
import com.example.lomanalyzer.tools.quality.QualityGates
import com.example.lomanalyzer.tools.quality.StageTiming
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class QualityGatesTest {

    // --- Benchmark regression detection ---

    @Test
    fun `no regression when timings are similar`() {
        val baseline = BenchmarkResult(
            totalMs = 300,
            stages = listOf(StageTiming("A", 100), StageTiming("B", 200)),
            postCount = 500, authorCount = 50,
        )
        val current = BenchmarkResult(
            totalMs = 310,
            stages = listOf(StageTiming("A", 105), StageTiming("B", 205)),
            postCount = 500, authorCount = 50,
        )
        val regressions = BenchmarkRunner.compare(current, baseline)
        assertTrue(regressions.isEmpty())
    }

    @Test
    fun `regression detected when stage exceeds 20pct`() {
        val baseline = BenchmarkResult(
            totalMs = 300,
            stages = listOf(StageTiming("A", 100), StageTiming("B", 200)),
            postCount = 500, authorCount = 50,
        )
        val current = BenchmarkResult(
            totalMs = 400,
            stages = listOf(StageTiming("A", 150), StageTiming("B", 250)),
            postCount = 500, authorCount = 50,
        )
        val regressions = BenchmarkRunner.compare(current, baseline)
        assertTrue(regressions.any { it.contains("A") })
        assertTrue(regressions.any { it.contains("B") })
    }

    @Test
    fun `benchmark JSON roundtrip`() {
        val result = BenchmarkResult(
            totalMs = 290,
            stages = listOf(StageTiming("X", 100)),
            postCount = 500, authorCount = 50,
        )
        val json = BenchmarkRunner.toJson(result)
        val parsed = BenchmarkRunner.fromJson(json)
        assertEquals(result.totalMs, parsed.totalMs)
        assertEquals(result.stages.size, parsed.stages.size)
    }

    // --- Cohen's kappa ---

    @Test
    fun `perfect agreement gives kappa 1`() {
        val labels = listOf("A", "B", "C", "A", "B")
        val kappa = CohenKappa.compute(labels, labels)
        assertEquals(1.0, kappa, 0.001)
    }

    @Test
    fun `substantial agreement gives kappa above 0_6`() {
        val r1 = listOf("A", "B", "A", "B", "A", "B", "A", "B", "A", "B")
        val r2 = listOf("A", "B", "A", "B", "A", "B", "A", "A", "A", "B")
        val kappa = CohenKappa.compute(r1, r2)
        assertTrue(kappa > 0.6, "kappa=$kappa")
    }

    @Test
    fun `random agreement gives kappa near 0`() {
        val r1 = listOf("A", "B", "C", "A", "B", "C")
        val r2 = listOf("C", "A", "B", "B", "C", "A")
        val kappa = CohenKappa.compute(r1, r2)
        assertTrue(kappa < 0.3, "kappa=$kappa for random")
    }

    // --- Quality gates ---

    @Test
    fun `gates summary contains all 6 gates`() {
        val results = QualityGates.evaluateAll()
        assertEquals(6, results.size)
        val ids = results.map { it.gateId }
        assertTrue("G-01" in ids)
        assertTrue("G-06" in ids)
    }

    @Test
    fun `gates summary markdown format is valid`() {
        val results = QualityGates.evaluateAll()
        val summary = QualityGates.generateSummary(results)
        assertTrue(summary.contains("Quality Gates Summary"))
        assertTrue(summary.contains("| Gate |"))
    }

    // --- NFR-03: pipeline time ---

    @Test
    fun `baseline benchmark under 90 minutes`() {
        val baseline = java.io.File("benchmarks/baseline.json")
        if (baseline.exists()) {
            val result = BenchmarkRunner.fromJson(baseline.readText())
            val minutes = result.totalMs / 60000.0
            assertTrue(
                minutes < 90,
                "Pipeline took ${minutes}min, limit is 90min",
            )
        }
    }
}
