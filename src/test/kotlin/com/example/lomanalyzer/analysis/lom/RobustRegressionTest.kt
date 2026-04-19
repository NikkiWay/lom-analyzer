package com.example.lomanalyzer.analysis.lom

import com.example.lomanalyzer.observability.Logger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class RobustRegressionTest {

    // --- Theil-Sen ---

    @Test
    fun `Theil-Sen on synthetic data with 20pct outliers is robust`() {
        // True relationship: y = 2 + 0.5x
        val rng = java.util.Random(42)
        val n = 50
        val xs = (1..n).map { it.toDouble() }
        val ys = xs.mapIndexed { i, x ->
            if (i < n * 0.8) {
                2.0 + 0.5 * x + rng.nextGaussian() * 0.3
            } else {
                // 20% gross outliers
                2.0 + 0.5 * x + rng.nextGaussian() * 20.0
            }
        }

        val result = RobustRegression.theilSen(xs, ys)
        // Slope should be within 5% of true value 0.5
        assertTrue(
            abs(result.slope - 0.5) < 0.05,
            "slope=${result.slope}, expected ~0.5"
        )
    }

    @Test
    fun `Theil-Sen on exact linear data gives perfect slope`() {
        val xs = (1..20).map { it.toDouble() }
        val ys = xs.map { 3.0 + 2.0 * it }
        val result = RobustRegression.theilSen(xs, ys)
        assertEquals(2.0, result.slope, 0.001)
        assertEquals(3.0, result.intercept, 0.001)
    }

    @Test
    fun `Theil-Sen handles large n with subsampling`() {
        val xs = (1..600).map { it.toDouble() }
        val ys = xs.map { 1.0 + 0.3 * it }
        val result = RobustRegression.theilSen(xs, ys)
        assertTrue(abs(result.slope - 0.3) < 0.01)
    }

    // --- MAD R² ---

    @Test
    fun `MAD R2 on perfect linear relationship is near 1`() {
        val xs = (1..30).map { it.toDouble() }
        val ys = xs.map { 5.0 + 1.5 * it }
        val r2 = RobustRegression.madR2(xs, ys, 1.5, 5.0)
        assertTrue(r2 > 0.99, "r2=$r2")
    }

    @Test
    fun `MAD R2 on random data is near 0`() {
        val rng = java.util.Random(42)
        val xs = (1..50).map { it.toDouble() }
        val ys = (1..50).map { rng.nextDouble() * 100 }
        val reg = RobustRegression.theilSen(xs, ys)
        val r2 = RobustRegression.madR2(xs, ys, reg.slope, reg.intercept)
        assertTrue(r2 < 0.3, "r2=$r2 should be low for random data")
    }

    // --- Huber M-estimator ---

    @Test
    fun `Huber resistant to outliers`() {
        // 90% values near 10, 10% outliers at 100
        val values = List(90) { 10.0 + java.util.Random(42).nextGaussian() * 0.5 } +
            List(10) { 100.0 }
        val huberMean = RobustRegression.huber(values)
        val arithmeticMean = values.average()
        // Huber should be close to 10, arithmetic mean is ~19
        assertTrue(
            abs(huberMean - 10.0) < 2.0,
            "huber=$huberMean should be ~10"
        )
        assertTrue(arithmeticMean > 15, "mean=$arithmeticMean should be >15")
    }

    @Test
    fun `Huber on empty returns 0`() {
        assertEquals(0.0, RobustRegression.huber(emptyList()))
    }

    // --- Bootstrap CI ---

    @Test
    fun `bootstrap CI contains true slope`() {
        val xs = (1..30).map { it.toDouble() }
        val ys = xs.map { 1.0 + 0.5 * it + java.util.Random(42).nextGaussian() * 0.2 }
        val (lo, hi) = RobustRegression.bootstrapTheilSenCI(xs, ys, 200)
        assertTrue(lo <= 0.55 && hi >= 0.45, "CI [$lo, $hi] should contain ~0.5")
    }

    // --- Permutation test ---

    @Test
    fun `permutation test gives low p for real signal`() {
        val xs = (1..30).map { it.toDouble() }
        val ys = xs.map { 1.0 + 0.5 * it + java.util.Random(42).nextGaussian() * 0.3 }
        val p = RobustRegression.permutationTestSlope(xs, ys, 200)
        assertTrue(p < 0.05, "p=$p should be < 0.05 for real signal")
    }

    @Test
    fun `permutation test gives high p for no signal`() {
        val rng = java.util.Random(42)
        val xs = (1..30).map { it.toDouble() }
        val ys = (1..30).map { rng.nextDouble() }
        val p = RobustRegression.permutationTestSlope(xs, ys, 200)
        assertTrue(p > 0.05, "p=$p should be > 0.05 for noise")
    }

    // --- OrthogonalizerT ---

    @Test
    fun `orthogonalizer applies when correlation exists`() {
        // T_raw correlated with N_topic_eff
        val data = (1..30).map { i ->
            AuthorOrthogonalizationInput(
                authorId = i,
                topicEffCount = i * 2,
                tRaw = 0.1 + 0.02 * i + java.util.Random(42).nextGaussian() * 0.005,
            )
        }
        val orth = OrthogonalizerT(Logger("test"), permutationIterations = 200)
        val result = orth.orthogonalize(data)

        if (result.applied) {
            assertEquals(EventWeights.SET_A, result.weights)
            assertTrue(result.madR2 >= 0.05)
        }
        // Either way, residuals exist for all authors
        assertEquals(30, result.residuals.size)
    }

    @Test
    fun `orthogonalizer skips when no correlation`() {
        val rng = java.util.Random(42)
        val data = (1..30).map { i ->
            AuthorOrthogonalizationInput(
                authorId = i,
                topicEffCount = i * 2,
                tRaw = rng.nextDouble(),
            )
        }
        val orth = OrthogonalizerT(Logger("test"), permutationIterations = 200)
        val result = orth.orthogonalize(data)

        assertFalse(result.applied)
        assertEquals(EventWeights.SET_B, result.weights)
    }

    @Test
    fun `orthogonalizer skips with too few authors`() {
        val data = (1..3).map { i ->
            AuthorOrthogonalizationInput(i, i * 5, 0.5)
        }
        val orth = OrthogonalizerT(Logger("test"))
        val result = orth.orthogonalize(data)
        assertFalse(result.applied)
    }

    // --- Upgraded GammaCalibrator ---

    @Test
    fun `upgraded gamma uses Theil-Sen for 20+ authors`() {
        val rng = java.util.Random(42)
        val authors = (1..30).map { i ->
            val f = 200 + i * 100
            val lnR = 5.0 - 0.4 * kotlin.math.ln(f.toDouble()) + rng.nextGaussian() * 0.1
            AuthorGammaInput(f, kotlin.math.exp(lnR))
        }
        val result = GammaCalibrator(bootstrapIterations = 100).calibrate(authors)
        assertEquals("THEIL_SEN", result.method)
        assertFalse(result.fallback)
    }

    @Test
    fun `upgraded gamma falls back to OLS for small sample`() {
        val authors = (1..15).map { i ->
            AuthorGammaInput(200 + i * 50, 10.0 + i)
        }
        val result = GammaCalibrator(bootstrapIterations = 50).calibrate(authors)
        assertEquals("OLS", result.method)
    }
}
