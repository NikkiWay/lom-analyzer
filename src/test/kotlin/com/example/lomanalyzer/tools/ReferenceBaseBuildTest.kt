package com.example.lomanalyzer.tools

import com.example.lomanalyzer.tools.benchmark.BenchmarkResult
import com.example.lomanalyzer.tools.benchmark.NlpModelBenchmark
import com.example.lomanalyzer.tools.refbase.ReferenceAuthor
import com.example.lomanalyzer.tools.refbase.ReferenceBaseComputer
import com.example.lomanalyzer.tools.refbase.StratificationConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.ln

class ReferenceBaseBuildTest {

    // --- Stratification math ---

    @Test
    fun `stratification counts sum to total sample`() {
        val config = StratificationConfig(totalSample = 15000)
        val counts = ReferenceBaseComputer.computeStratificationCounts(config)
        val total = counts.values.sum()
        // Allow ±1 for rounding
        assertTrue(abs(total - 15000) <= 4, "total=$total")
    }

    @Test
    fun `stratification percentages match config`() {
        val config = StratificationConfig(totalSample = 10000)
        val counts = ReferenceBaseComputer.computeStratificationCounts(config)
        assertEquals(3500, counts["lt1k"])
        assertEquals(4000, counts["1k_10k"])
        assertEquals(2000, counts["10k_100k"])
        assertEquals(500, counts["gt100k"])
    }

    // --- Raw metrics computation ---

    @Test
    fun `raw metrics computed correctly for known values`() {
        val author = ReferenceAuthor(
            vkId = 1,
            followersCount = 1000,
            totalPosts = 100,
            totalWeightedReactions = 5000.0,
            region = "msk",
            accountType = "personal",
        )

        val (lnF, lnRBar, eRaw) = ReferenceBaseComputer.computeRawMetrics(author)

        // ln(1 + 1000) = ln(1001) ≈ 6.909
        assertEquals(ln(1001.0), lnF, 0.001)
        // r_bar = 5000/100 = 50, ln(1+50) ≈ 3.932
        assertEquals(ln(51.0), lnRBar, 0.001)
        // E_raw = ln(51) - 0.45 * ln(1001) ≈ 3.932 - 3.109 ≈ 0.823
        val expected = ln(51.0) - 0.45 * ln(1001.0)
        assertEquals(expected, eRaw, 0.001)
    }

    // --- I_base^ref computation ---

    @Test
    fun `I_base_ref on hand-computed example`() {
        val lnFQ = ReferenceBaseComputer.computeQuantiles(
            listOf(4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0),
        )
        val eRawQ = ReferenceBaseComputer.computeQuantiles(
            listOf(-2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 4.0),
        )

        // Author at median → z_A = 0, z_E = 0 → sigmoid(0) = 0.5
        // I_base = 0.55 * 0.5 + 0.45 * 0.5 = 0.5
        val iBase = ReferenceBaseComputer.computeIBaseRef(
            lnF = lnFQ.q50, eRaw = eRawQ.q50, lnFQ, eRawQ,
        )
        assertEquals(0.5, iBase, 0.01)
    }

    @Test
    fun `I_base_ref high values above 0_5`() {
        val lnFQ = ReferenceBaseComputer.computeQuantiles((1..100).map { it.toDouble() })
        val eRawQ = ReferenceBaseComputer.computeQuantiles((1..100).map { it.toDouble() })

        val iBase = ReferenceBaseComputer.computeIBaseRef(
            lnF = 90.0, eRaw = 90.0, lnFQ, eRawQ,
        )
        assertTrue(iBase > 0.5, "High values should give iBase > 0.5: $iBase")
    }

    // --- Full build output ---

    @Test
    fun `buildOutput produces valid reference on synthetic data`() {
        val authors = (1..100).map { i ->
            ReferenceAuthor(
                vkId = i,
                followersCount = 100 + i * 50,
                totalPosts = 20 + i,
                totalWeightedReactions = (100 + i * 30).toDouble(),
                region = "msk",
                accountType = "personal",
            )
        }

        val output = ReferenceBaseComputer.buildOutput(authors)
        assertEquals(100, output.sampleSize)
        assertTrue(output.tauBaseP75 > 0, "tau=${output.tauBaseP75}")
        assertTrue(output.tauBaseP75 <= 1.0)
        assertTrue(output.lnFQuantiles.q50 > 0)
        assertTrue(output.lnFQuantiles.iqr > 0)
    }

    // --- Quantile computation ---

    @Test
    fun `quantiles computed correctly`() {
        val values = (1..100).map { it.toDouble() }
        val q = ReferenceBaseComputer.computeQuantiles(values)
        assertEquals(10.0, q.q10, 1.0)
        assertEquals(25.0, q.q25, 1.0)
        assertEquals(50.0, q.q50, 1.0)
        assertEquals(75.0, q.q75, 1.0)
        assertEquals(90.0, q.q90, 1.0)
        assertTrue(q.iqr > 40 && q.iqr < 60)
    }

    // --- NLP Benchmark ---

    @Test
    fun `benchmark correlation computation`() {
        val sims = listOf(0.9, 0.8, 0.2, 0.1, 0.85, 0.15)
        val labels = listOf(true, true, false, false, true, false)
        val corr = NlpModelBenchmark.evaluateCorrelation(sims, labels)
        assertTrue(corr > 0.8, "corr=$corr for well-separated data")
    }

    @Test
    fun `benchmark recommendation keeps tiny2 when no improvement`() {
        val results = listOf(
            BenchmarkResult("rubert-tiny2", 0.72, 15.0, 29, ""),
            BenchmarkResult("ruBERT-base", 0.74, 85.0, 680, ""),
        )
        val rec = NlpModelBenchmark.generateRecommendation(results)
        assertTrue(rec.contains("Keep rubert-tiny2"), rec)
    }
}
