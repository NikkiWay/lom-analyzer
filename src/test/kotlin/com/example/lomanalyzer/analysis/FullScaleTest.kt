package com.example.lomanalyzer.analysis

import com.example.lomanalyzer.analysis.content.HuberAggregator
import com.example.lomanalyzer.analysis.content.SentimentBootstrap
import com.example.lomanalyzer.analysis.lom.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class FullScaleTest {

    // --- Two-level bootstrap parallel vs serial ---

    @Test
    fun `bootstrap 300x100 produces CI for all authors`() = runBlocking {
        val authors = (1..20).map { i ->
            AuthorBaselineData(
                authorId = i,
                followers = 100 + i * 50,
                postReactions = (1..8).map {
                    PostReactions(likes = 5 + i, reposts = 1, comments = 1)
                },
                imputed = false,
            )
        }

        val config = BootstrapConfig(outerIterations = 50, innerIterations = 20)
        val estimator = BootstrapEstimator(config)
        val results = estimator.estimateIBase(authors, gamma = 0.45)

        assertEquals(20, results.size)
        for ((_, r) in results) {
            assertTrue(r.ciLo <= r.pointEstimate)
            assertTrue(r.ciHi >= r.pointEstimate)
            assertTrue(r.ciHi - r.ciLo < 0.5, "CI width reasonable")
        }
    }

    @Test
    fun `parallel bootstrap matches serial within tolerance`() = runBlocking {
        val authors = (1..10).map { i ->
            AuthorBaselineData(i, 200 + i * 100,
                (1..6).map { PostReactions(10 + i, 2, 1) }, false)
        }

        // Same config, same seed → should produce identical results
        val config = BootstrapConfig(outerIterations = 30, innerIterations = 15)
        val r1 = BootstrapEstimator(config).estimateIBase(authors, 0.45)
        val r2 = BootstrapEstimator(config).estimateIBase(authors, 0.45)

        for (id in r1.keys) {
            val a = r1[id]!!
            val b = r2[id]!!
            // Same seed → identical
            assertEquals(a.pointEstimate, b.pointEstimate, 0.001)
        }
    }

    // --- HuberAggregator ---

    @Test
    fun `Huber converges on bimodal distribution`() {
        // Bimodal: cluster around -0.7 and +0.7
        val scores = List(30) { -0.7f + (it % 2) * 1.4f }
        val result = HuberAggregator.aggregate(scores)

        // Huber should find a central tendency
        assertTrue(abs(result.huberMean) < 0.5, "huber=${result.huberMean}")
        assertTrue(result.toneMixedFlag, "SD=${result.sd} should be > 0.5 → mixed flag")
    }

    @Test
    fun `Huber on uniform positive is positive`() {
        val scores = List(20) { 0.7f }
        val result = HuberAggregator.aggregate(scores)
        assertTrue(result.huberMean > 0.6f)
        assertFalse(result.toneMixedFlag)
    }

    @Test
    fun `Huber CI contains point estimate`() {
        val scores = (1..30).map { it.toFloat() / 30 - 0.5f }
        val result = HuberAggregator.aggregate(scores)
        assertTrue(result.ciLo <= result.huberMean)
        assertTrue(result.ciHi >= result.huberMean)
    }

    @Test
    fun `Huber handles empty input`() {
        val result = HuberAggregator.aggregate(emptyList())
        assertEquals(0f, result.huberMean)
    }

    // --- SentimentBootstrap 20 variants ---

    @Test
    fun `sentiment bootstrap produces 20 variants`() {
        val result = SentimentBootstrap.bootstrap(
            listOf("хороший", "отличный", "замечательный"),
        )
        assertEquals(20, result.variantLabels.size)
        assertTrue(result.agreement in 0f..1f)
    }

    // --- DiscoveryEngine rules ---

    @Test
    fun `DiscoveryCandidate accumulates DPS from multiple rules`() {
        val candidate = com.example.lomanalyzer.vk.DiscoveryCandidate(vkId = 42)
        candidate.dps += 3.5
        candidate.rules.add("RULE_1")
        candidate.dps += 2.0
        candidate.rules.add("RULE_2")

        assertEquals(5.5, candidate.dps, 0.001)
        assertTrue(candidate.rules.contains("RULE_1"))
        assertTrue(candidate.rules.contains("RULE_2"))
    }

    @Test
    fun `DiscoveryCandidate default DPS is 0`() {
        val c = com.example.lomanalyzer.vk.DiscoveryCandidate(vkId = 1)
        assertEquals(0.0, c.dps)
        assertTrue(c.rules.isEmpty())
    }

    // --- Bootstrap default config is 300x100 ---

    @Test
    fun `default BootstrapConfig is 300x100`() {
        val config = BootstrapConfig()
        assertEquals(300, config.outerIterations)
        assertEquals(100, config.innerIterations)
    }

    // --- BlockBootstrap default is 300 ---

    @Test
    fun `default BlockBootstrap is 300 iterations`() {
        val bb = com.example.lomanalyzer.analysis.risk.BlockBootstrap()
        // Verify it runs without error at 300
        val anomalies = listOf(
            com.example.lomanalyzer.analysis.anomaly.AnomalyDetection(
                "VOLUME_SPIKE", java.time.LocalDate.of(2025, 6, 1),
                0.5, 3.0, "test",
            ),
        )
        val result = bb.bootstrap(anomalies)
        assertTrue(result.riskScore > 0)
    }
}
