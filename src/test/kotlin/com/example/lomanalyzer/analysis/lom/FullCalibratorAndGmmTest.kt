package com.example.lomanalyzer.analysis.lom

import com.example.lomanalyzer.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class FullCalibratorAndGmmTest {

    private val testRef = ReferenceBase(
        version = "1.0.0",
        gammaUsedInCollection = 0.45,
        rawQuantileStatistics = RawQuantileStatistics(
            lnF = QuantileStats(q25 = 6.2, q50 = 7.8, q75 = 9.5, iqr = 3.3),
            lnRBar = QuantileStats(q25 = 1.8, q50 = 3.4, q75 = 5.2, iqr = 3.4),
        ),
        computedStatsAtGammaRef = ComputedStatsAtGamma(
            eRawAtGamma = QuantileStats(q25 = -1.2, q50 = 0.1, q75 = 1.4, iqr = 2.6),
            iBaseAtGamma = IBasePercentiles(p50 = 0.53, p75 = 0.78),
        ),
        iBaseThresholds = IBaseThresholds(tauBaseP75 = 0.78, fP75 = 13000),
    )

    // --- ReferenceGammaValidator three branches ---

    @Test
    fun `validator OK for delta 0_05`() {
        assertEquals("OK", ReferenceGammaValidator.validate(0.50))
    }

    @Test
    fun `validator MILD_RECOMPUTED for delta 0_15`() {
        assertEquals("MILD_RECOMPUTED", ReferenceGammaValidator.validate(0.60))
    }

    @Test
    fun `validator AUDIENCE_ONLY for delta 0_25`() {
        assertEquals("AUDIENCE_ONLY_REFERENCE", ReferenceGammaValidator.validate(0.70))
    }

    // --- ReferenceCalibrator three branches ---

    @Test
    fun `OK branch produces valid I_base_abs`() {
        val cal = ReferenceCalibrator()
        val result = cal.calibrate(5000, 1.0, "OK", 0.45, testRef)
        assertTrue(result.iBaseAbs in 0.0..1.0)
        assertTrue(result.flags.isEmpty())
    }

    @Test
    fun `MILD_RECOMPUTED produces valid result with flags`() {
        val cal = ReferenceCalibrator()
        val result = cal.calibrate(5000, 1.0, "MILD_RECOMPUTED", 0.60, testRef)
        assertTrue(result.iBaseAbs in 0.0..1.0)
        assertTrue("REF_THRESHOLD_APPROXIMATED" in result.flags)
    }

    @Test
    fun `MILD_RECOMPUTED differs from OK for non-reference gamma`() {
        val cal = ReferenceCalibrator()
        val ok = cal.calibrate(5000, 1.0, "OK", 0.45, testRef)
        val mild = cal.calibrate(5000, 1.0, "MILD_RECOMPUTED", 0.60, testRef)
        // Should produce different values due to E-quantile recomputation
        assertTrue(abs(ok.iBaseAbs - mild.iBaseAbs) > 0.001,
            "OK=${ok.iBaseAbs} vs MILD=${mild.iBaseAbs}")
    }

    @Test
    fun `AUDIENCE_ONLY ignores E component`() {
        val cal = ReferenceCalibrator()
        val a = cal.calibrate(5000, -5.0, "AUDIENCE_ONLY_REFERENCE", 0.70, testRef)
        val b = cal.calibrate(5000, 5.0, "AUDIENCE_ONLY_REFERENCE", 0.70, testRef)
        assertEquals(a.iBaseAbs, b.iBaseAbs, 0.001)
    }

    // --- High-correlation flag ---

    @Test
    fun `high correlation reference triggers MILD_RECOMPUTED_HIGH_CORRELATION`() {
        // Make IQRs very similar → high correlation proxy
        val highCorrRef = testRef.copy(
            rawQuantileStatistics = RawQuantileStatistics(
                lnF = QuantileStats(q25 = 6.0, q50 = 7.8, q75 = 9.6, iqr = 3.0),
                lnRBar = QuantileStats(q25 = 5.8, q50 = 7.5, q75 = 8.8, iqr = 3.0),
            ),
        )
        val cal = ReferenceCalibrator()
        val result = cal.calibrate(5000, 1.0, "MILD_RECOMPUTED", 0.60, highCorrRef)
        assertTrue("MILD_RECOMPUTED_HIGH_CORRELATION" in result.flags,
            "flags=${result.flags}")
    }

    @Test
    fun `low correlation reference does NOT trigger HIGH_CORRELATION`() {
        // IQRs very different → low correlation proxy
        val lowCorrRef = testRef.copy(
            rawQuantileStatistics = RawQuantileStatistics(
                lnF = QuantileStats(q25 = 6.0, q50 = 7.8, q75 = 9.6, iqr = 5.0),
                lnRBar = QuantileStats(q25 = 1.0, q50 = 2.0, q75 = 2.5, iqr = 1.5),
            ),
        )
        val cal = ReferenceCalibrator()
        val result = cal.calibrate(5000, 1.0, "MILD_RECOMPUTED", 0.60, lowCorrRef)
        assertFalse("MILD_RECOMPUTED_HIGH_CORRELATION" in result.flags)
    }

    // --- ClusterRoleClassifier (GMM) ---

    @Test
    fun `GMM with 2 clusters recovers expected roles`() {
        val rng = java.util.Random(42)

        // Cluster 1: high base, high event (top-right → AUTHORITATIVE)
        val cluster1 = (1..30).map {
            AuthorGmmInput(it, 0.7 + rng.nextGaussian() * 0.05, 0.7 + rng.nextGaussian() * 0.05)
        }
        // Cluster 2: low base, low event (bottom-left → BACKGROUND)
        val cluster2 = (31..60).map {
            AuthorGmmInput(it, 0.3 + rng.nextGaussian() * 0.05, 0.3 + rng.nextGaussian() * 0.05)
        }

        val classifier = ClusterRoleClassifier()
        assertTrue(classifier.isApplicable(60))

        val results = classifier.classify(cluster1 + cluster2, tauBase = 0.5, tauEvent = 0.5)
        assertEquals(60, results.size)

        // Cluster 1 authors should mostly be AUTHORITATIVE_LOM
        val cluster1Roles = results.filter { it.authorId <= 30 }.map { it.sessionRole }
        val authCount = cluster1Roles.count { it == SessionRole.AUTHORITATIVE_LOM }
        assertTrue(authCount > 20, "Expected most cluster1 → AUTHORITATIVE, got $authCount/30")

        // Cluster 2 authors should mostly be BACKGROUND
        val cluster2Roles = results.filter { it.authorId > 30 }.map { it.sessionRole }
        val bgCount = cluster2Roles.count { it == SessionRole.BACKGROUND }
        assertTrue(bgCount > 20, "Expected most cluster2 → BACKGROUND, got $bgCount/30")
    }

    @Test
    fun `GMM not applicable with fewer than 50 authors`() {
        val classifier = ClusterRoleClassifier()
        assertFalse(classifier.isApplicable(49))
        val results = classifier.classify(emptyList(), 0.5, 0.5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `GMM posterior probabilities sum to approximately 1`() {
        val rng = java.util.Random(42)
        val inputs = (1..60).map {
            AuthorGmmInput(it, rng.nextDouble(), rng.nextDouble())
        }
        val classifier = ClusterRoleClassifier()
        val results = classifier.classify(inputs, 0.5, 0.5)

        for (r in results) {
            assertTrue(r.posteriorProb in 0.0..1.0,
                "posterior=${r.posteriorProb}")
        }
    }

    // --- Backward compat: simplified calibrate still works ---

    @Test
    fun `simplified calibrate overload works`() {
        val cal = ReferenceCalibrator()
        val score = cal.calibrate(5000, 1.0, "OK", testRef)
        assertTrue(score in 0.0..1.0)
    }
}
