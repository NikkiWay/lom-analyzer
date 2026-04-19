package com.example.lomanalyzer.analysis.anomaly

import com.example.lomanalyzer.analysis.lom.CombinedRole
import com.example.lomanalyzer.analysis.risk.BlockBootstrap
import com.example.lomanalyzer.analysis.risk.RiskScorer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AnomalyAndRiskTest {

    // --- RollingZScore ---

    @Test
    fun `rolling z-score detects spike in synthetic series`() {
        val values = listOf(10.0, 11.0, 9.0, 10.0, 12.0, 10.0, 11.0, 50.0, 10.0)
        val zScorer = RollingZScore(windowSize = 7, sigmaMin = 0.5)
        val results = zScorer.compute(values)

        assertEquals(values.size, results.size)
        // The spike at index 7 (value=50) should have high z-score
        val spike = results[7]
        assertTrue(spike.zScore > 2.0, "z=${spike.zScore} for spike value 50")
    }

    @Test
    fun `rolling z-score handles constant series`() {
        val values = List(10) { 5.0 }
        val zScorer = RollingZScore(windowSize = 7, sigmaMin = 1.0)
        val results = zScorer.compute(values)

        for (r in results) {
            assertTrue(r.zScore <= 0.01, "z=${r.zScore} should be ~0 for constant series")
        }
    }

    @Test
    fun `rolling z-score respects sigma_min`() {
        val values = listOf(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 6.0)
        val zScorer = RollingZScore(windowSize = 7, sigmaMin = 1.0)
        val results = zScorer.compute(values)

        // With sigma_min=1.0, a +1 deviation gives z ≈ 1.0
        val last = results.last()
        assertTrue(last.rollingSigma >= 1.0, "sigma should be >= sigmaMin")
    }

    @Test
    fun `rolling z-score handles short series`() {
        val values = listOf(1.0, 2.0)
        val zScorer = RollingZScore(windowSize = 7)
        val results = zScorer.compute(values)
        assertEquals(2, results.size)
    }

    // --- RiskScorer ---

    @Test
    fun `risk_multi with 3 equal components equals 1_8`() {
        // 3 anomalies with equal severity 0.5 → R_dom = 0.5, S_total = 1.5
        // R_multi = 1 + 1.2 * (1 - 0.5/1.501) * 1 = 1 + 1.2 * 0.667 ≈ 1.8
        val anomalies = listOf(
            makeAnomaly("VOLUME_SPIKE", 0.5),
            makeAnomaly("TONE_SHIFT_NEGATIVE", 0.5),
            makeAnomaly("GIANT_ACTIVATION", 0.5),
        )

        val result = RiskScorer.computeRisk(anomalies)
        assertEquals(1.8, result.rMulti, 0.05)
        assertEquals(3, result.activeTypes)
        // R = 0.5 * 1.8 = 0.9
        assertTrue(result.riskScore in 0.85..0.95, "risk=${result.riskScore}")
    }

    @Test
    fun `single anomaly type gives R_multi = 1`() {
        val anomalies = listOf(makeAnomaly("VOLUME_SPIKE", 0.6))
        val result = RiskScorer.computeRisk(anomalies)
        assertEquals(1.0, result.rMulti)
        assertEquals(0.6, result.riskScore, 0.001)
    }

    @Test
    fun `empty anomalies give zero risk`() {
        val result = RiskScorer.computeRisk(emptyList())
        assertEquals(0.0, result.riskScore)
    }

    @Test
    fun `risk is clamped to 1`() {
        val anomalies = listOf(
            makeAnomaly("VOLUME_SPIKE", 0.9),
            makeAnomaly("TONE_SHIFT_NEGATIVE", 0.8),
        )
        val result = RiskScorer.computeRisk(anomalies)
        assertTrue(result.riskScore <= 1.0, "risk=${result.riskScore}")
    }

    // --- AnomalyDeduplicator ---

    @Test
    fun `ABSORBED_BY_GIANT suppresses tone shift on same day`() {
        val date = LocalDate.of(2025, 6, 15)
        val anomalies = listOf(
            AnomalyDetection("GIANT_ACTIVATION", date, 0.8, 0.0, "giant"),
            AnomalyDetection("TONE_SHIFT_NEGATIVE", date, 0.5, -3.0, "tone"),
            AnomalyDetection("VOLUME_SPIKE", date, 0.6, 3.0, "volume"),
        )

        val deduped = AnomalyDeduplicator.deduplicate(anomalies)
        // TONE_SHIFT should be suppressed (giant severity 0.8 > tone severity 0.5)
        assertEquals(2, deduped.size)
        assertTrue(deduped.none { it.type == "TONE_SHIFT_NEGATIVE" })
        assertTrue(deduped.any { it.type == "GIANT_ACTIVATION" })
        assertTrue(deduped.any { it.type == "VOLUME_SPIKE" })
    }

    @Test
    fun `tone shift NOT suppressed if giant severity is lower`() {
        val date = LocalDate.of(2025, 6, 15)
        val anomalies = listOf(
            AnomalyDetection("GIANT_ACTIVATION", date, 0.3, 0.0, "giant"),
            AnomalyDetection("TONE_SHIFT_NEGATIVE", date, 0.5, -3.0, "tone"),
        )

        val deduped = AnomalyDeduplicator.deduplicate(anomalies)
        assertEquals(2, deduped.size) // Both kept
    }

    @Test
    fun `tone shift on different day is NOT suppressed`() {
        val anomalies = listOf(
            AnomalyDetection("GIANT_ACTIVATION", LocalDate.of(2025, 6, 15), 0.8, 0.0, "g"),
            AnomalyDetection("TONE_SHIFT_NEGATIVE", LocalDate.of(2025, 6, 16), 0.5, -3.0, "t"),
        )

        val deduped = AnomalyDeduplicator.deduplicate(anomalies)
        assertEquals(2, deduped.size)
    }

    // --- GiantActivationDetector ---

    @Test
    fun `giant activation fires for SLEEPING_GIANT_CONFIRMED`() {
        val candidates = listOf(
            GiantCandidate(1, CombinedRole.SLEEPING_GIANT_CONFIRMED, 0.8, 5000, 0.7, LocalDate.now()),
        )
        val results = GiantActivationDetector.detect(candidates)
        assertEquals(1, results.size)
        // severity = 0.8 * (0.3 + 0.7*0.7) * 1.0 = 0.8 * 0.79 = 0.632
        assertTrue(results[0].severity > 0.5, "severity=${results[0].severity}")
    }

    @Test
    fun `giant does NOT fire for BACKGROUND role`() {
        val candidates = listOf(
            GiantCandidate(1, CombinedRole.BACKGROUND, 0.3, 100, 0.5, LocalDate.now()),
        )
        val results = GiantActivationDetector.detect(candidates)
        assertTrue(results.isEmpty())
    }

    // --- BlockBootstrap ---

    @Test
    fun `block bootstrap produces valid CI`() {
        val anomalies = (1..14).map { day ->
            makeAnomaly("VOLUME_SPIKE", 0.3 + (day % 3) * 0.1, LocalDate.of(2025, 6, day))
        }

        val result = BlockBootstrap(blockDays = 7, iterations = 50).bootstrap(anomalies)
        assertTrue(result.ciLo <= result.riskScore)
        assertTrue(result.ciHi >= result.riskScore)
        assertTrue(result.ciLo >= 0)
        assertTrue(result.ciHi <= 1)
    }

    // --- HolidayCalendar ---

    @Test
    fun `holiday calendar recognizes Jan 1 2025`() {
        val cal = HolidayCalendar()
        val info = cal.check(LocalDate.of(2025, 1, 1))
        assertTrue(info.isHoliday)
        assertEquals("Новый год", info.name)
    }

    @Test
    fun `holiday calendar returns false for regular day`() {
        val cal = HolidayCalendar()
        val info = cal.check(LocalDate.of(2025, 3, 15))
        assertFalse(info.isHoliday)
        assertNull(info.name)
    }

    // --- Helpers ---

    private fun makeAnomaly(
        type: String,
        severity: Double,
        date: LocalDate = LocalDate.of(2025, 6, 15),
    ) = AnomalyDetection(type, date, severity, 0.0, "test")
}
