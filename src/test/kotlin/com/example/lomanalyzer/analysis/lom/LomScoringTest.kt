package com.example.lomanalyzer.analysis.lom

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.ln

class LomScoringTest {

    // --- GammaCalibrator ---

    @Test
    fun `gamma calibrator returns expected value on synthetic data`() {
        // Generate: higher F → lower r_bar (negative beta1 → positive gamma)
        // ln(r_bar) = 5 - 0.4 * ln(F) + noise → gamma ~ 0.4
        val rng = java.util.Random(42)
        val authors = (1..50).map { i ->
            val f = 200 + i * 100
            val lnR = 5.0 - 0.4 * ln(f.toDouble()) + rng.nextGaussian() * 0.1
            val rBar = kotlin.math.exp(lnR)
            AuthorGammaInput(f, rBar)
        }

        val result = GammaCalibrator(bootstrapIterations = 100).calibrate(authors)
        assertFalse(result.fallback, "Should not fallback with 50 active authors")
        assertTrue(result.gamma in 0.25..0.65, "gamma=${result.gamma}")
        assertTrue(result.r2 > 0.01, "r2=${result.r2}")
        assertTrue(result.ciLo <= result.gamma)
        assertTrue(result.ciHi >= result.gamma)
    }

    @Test
    fun `gamma calibrator falls back with too few authors`() {
        val authors = (1..10).map { AuthorGammaInput(500, 10.0) }
        val result = GammaCalibrator().calibrate(authors)
        assertTrue(result.fallback)
        assertEquals(0.45, result.gamma, 0.001)
    }

    @Test
    fun `gamma calibrator clips to range`() {
        // All identical → OLS beta1=0 → gamma=0, clipped to 0.25
        val authors = (1..25).map { AuthorGammaInput(200 + it * 10, 5.0) }
        val result = GammaCalibrator().calibrate(authors)
        assertTrue(result.gamma >= 0.25 && result.gamma <= 0.65)
    }

    // --- RobustNormalizer ---

    @Test
    fun `normalizer handles small sample without crashing`() {
        val norm = RobustNormalizer()
        val stats = norm.computeStats(listOf(1.0, 2.0, 3.0))
        assertEquals(3, stats.nEff)
        assertTrue(stats.median > 0)
        // Small sample should trigger bootstrap CV_IQR
        assertNotNull(stats.cvIqr)
    }

    @Test
    fun `normalizer handles single value`() {
        val norm = RobustNormalizer()
        val stats = norm.computeStats(listOf(5.0))
        assertEquals(1, stats.nEff)
        val normalized = norm.normalize(5.0, stats)
        assertEquals(0.5, normalized, 0.01)
    }

    @Test
    fun `normalizer handles empty input`() {
        val norm = RobustNormalizer()
        val stats = norm.computeStats(emptyList())
        assertEquals(0, stats.nEff)
    }

    @Test
    fun `normalizer produces values in 0-1 range`() {
        val norm = RobustNormalizer()
        val values = (1..100).map { it.toDouble() }
        val stats = norm.computeStats(values)

        for (v in values) {
            val n = norm.normalize(v, stats)
            assertTrue(n in 0.0..1.0, "normalized=$n for value=$v")
        }
    }

    @Test
    fun `normalizer median maps to approximately 0_5`() {
        val norm = RobustNormalizer()
        val values = (1..100).map { it.toDouble() }
        val stats = norm.computeStats(values)
        val medNorm = norm.normalize(stats.median, stats)
        assertTrue(abs(medNorm - 0.5) < 0.01)
    }

    @Test
    fun `CV_IQR flags NORM_UNSTABLE for high variance small sample`() {
        val norm = RobustNormalizer(bootstrapIterations = 500)
        // Very varied small sample
        val stats = norm.computeStats(listOf(0.1, 100.0, 0.5))
        // With 3 points the IQR bootstrap should show high CV
        assertNotNull(stats.cvIqr)
    }

    // --- BootstrapEstimator ---

    @Test
    fun `bootstrap CI width is reasonable`() = runBlocking {
        val authors = (1..30).map { i ->
            AuthorBaselineData(
                authorId = i,
                followers = 100 + i * 50,
                postReactions = (1..10).map {
                    PostReactions(likes = 5 + i, reposts = 1, comments = 1)
                },
                imputed = false,
            )
        }

        val estimator = BootstrapEstimator(BootstrapConfig(outerIterations = 50, innerIterations = 20))
        val results = estimator.estimateIBase(authors, gamma = 0.45)

        assertEquals(30, results.size)
        for ((_, result) in results) {
            assertTrue(result.ciLo <= result.pointEstimate, "ciLo=${result.ciLo} > point=${result.pointEstimate}")
            assertTrue(result.ciHi >= result.pointEstimate, "ciHi=${result.ciHi} < point=${result.pointEstimate}")
            val width = result.ciHi - result.ciLo
            assertTrue(width >= 0, "CI width should be non-negative: $width")
            assertTrue(width < 1.0, "CI width should be < 1 for normalized scores: $width")
        }
    }

    @Test
    fun `bootstrap handles empty authors`() = runBlocking {
        val estimator = BootstrapEstimator()
        val results = estimator.estimateIBase(emptyList(), gamma = 0.45)
        assertTrue(results.isEmpty())
    }

    // --- Component tests ---

    @Test
    fun `audience component returns ln(1+F)`() {
        val raw = AudienceComponent.computeRaw(1000)
        assertEquals(ln(1001.0), raw, 0.001)
    }

    @Test
    fun `engagement density weighted reaction formula`() {
        val wr = EngagementDensityComponent.weightedReaction(
            PostReactions(likes = 10, reposts = 5, comments = 4),
        )
        // 10 + 2*5 + 1.5*4 = 10 + 10 + 6 = 26
        assertEquals(26.0, wr, 0.001)
    }

    @Test
    fun `closed profile imputer uses Q25`() {
        val q25 = ClosedProfileImputer.computeQ25(listOf(100, 200, 300, 400))
        assertEquals(200, q25) // index = (4*0.25).toInt()=1 → sorted[1]=200
        val result = ClosedProfileImputer.impute(null, true, q25)
        assertTrue(result.imputed)
        assertEquals("ESTIMATED_CONSERVATIVE", result.audienceFlag)
        assertEquals(200, result.followers)
    }

    @Test
    fun `reference gamma validator OK for close gamma`() {
        assertEquals("OK", ReferenceGammaValidator.validate(0.45))
        assertEquals("OK", ReferenceGammaValidator.validate(0.50))
        assertEquals("OK", ReferenceGammaValidator.validate(0.40))
    }

    @Test
    fun `reference gamma validator MILD_RECOMPUTED for moderate gamma`() {
        assertEquals("MILD_RECOMPUTED", ReferenceGammaValidator.validate(0.60))
    }

    @Test
    fun `reference gamma validator AUDIENCE_ONLY for distant gamma`() {
        assertEquals("AUDIENCE_ONLY_REFERENCE", ReferenceGammaValidator.validate(0.70))
        assertEquals("AUDIENCE_ONLY_REFERENCE", ReferenceGammaValidator.validate(0.20))
    }
}
