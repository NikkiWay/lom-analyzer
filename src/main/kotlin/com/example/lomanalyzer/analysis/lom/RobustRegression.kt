package com.example.lomanalyzer.analysis.lom

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

data class RegressionResult(
    val slope: Double,
    val intercept: Double,
)

/**
 * Robust regression module per v6 §14.1.2.
 * Provides Theil-Sen estimator, Huber M-estimator, MAD-based R²,
 * bootstrap CI, and permutation test for slope significance.
 */
object RobustRegression {

    /**
     * Theil-Sen estimator: median slope over all (i,j) pairs where x_i != x_j.
     * For n > 500, subsample O(n * log(n)) pairs to cap computation.
     */
    @Suppress("NestedBlockDepth")
    fun theilSen(x: List<Double>, y: List<Double>): RegressionResult {
        require(x.size == y.size && x.size >= 2)
        val n = x.size
        val slopes = mutableListOf<Double>()

        if (n <= 500) {
            // Exact: all O(n²) pairs
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    if (x[i] != x[j]) {
                        slopes.add((y[j] - y[i]) / (x[j] - x[i]))
                    }
                }
            }
        } else {
            // Subsample: n * 20 random pairs
            val rng = Random(42)
            val sampleSize = n * 20
            repeat(sampleSize) {
                val i = rng.nextInt(n)
                var j = rng.nextInt(n)
                while (j == i) j = rng.nextInt(n)
                if (x[i] != x[j]) {
                    slopes.add((y[j] - y[i]) / (x[j] - x[i]))
                }
            }
        }

        if (slopes.isEmpty()) return RegressionResult(0.0, 0.0)

        val slope = median(slopes)
        // Intercept: median of (y_i - slope * x_i)
        val residuals = (0 until n).map { y[it] - slope * x[it] }
        val intercept = median(residuals)

        return RegressionResult(slope, intercept)
    }

    /**
     * Huber M-estimator per v6 §12.4.
     * Iterative reweighting; converges when |mu_new - mu_old| < 1e-4; max 20 iterations.
     */
    @Suppress("ReturnCount")
    fun huber(values: List<Double>, k: Double = 1.345): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values[0]

        var mu = median(values)
        repeat(20) {
            var sumW = 0.0
            var sumWx = 0.0
            for (v in values) {
                val r = abs(v - mu)
                val w = if (r > 0) minOf(1.0, k / r) else 1.0
                sumW += w
                sumWx += w * v
            }
            val muNew = if (sumW > 0) sumWx / sumW else mu
            if (abs(muNew - mu) < 1e-4) return muNew
            mu = muNew
        }
        return mu
    }

    /**
     * MAD-based R² per v6 §14.1.2:
     * R²_MAD = 1 - (MAD_residual / MAD_total)²
     */
    fun madR2(
        x: List<Double>,
        y: List<Double>,
        slope: Double,
        intercept: Double,
    ): Double {
        val residuals = x.indices.map { abs(y[it] - intercept - slope * x[it]) }
        val yMedian = median(y)
        val totalDevs = y.map { abs(it - yMedian) }

        val madRes = median(residuals)
        val madTot = median(totalDevs)

        if (madTot <= 0) return 0.0
        val ratio = madRes / madTot
        return (1.0 - ratio * ratio).coerceIn(0.0, 1.0)
    }

    /**
     * Bootstrap CI for Theil-Sen slope: resample pairs with replacement,
     * compute 5/95 percentile.
     */
    fun bootstrapTheilSenCI(
        x: List<Double>,
        y: List<Double>,
        iterations: Int = 1000,
    ): Pair<Double, Double> {
        val n = x.size
        val rng = Random(42)
        val slopes = mutableListOf<Double>()

        repeat(iterations) {
            val indices = (0 until n).map { rng.nextInt(n) }
            val bx = indices.map { x[it] }
            val by = indices.map { y[it] }
            val result = theilSen(bx, by)
            slopes.add(result.slope)
        }

        slopes.sort()
        val lo = slopes[(slopes.size * 0.05).toInt()]
        val hi = slopes[(slopes.size * 0.95).toInt().coerceAtMost(slopes.size - 1)]
        return lo to hi
    }

    /**
     * Permutation test for slope significance.
     * Permute y, recompute Theil-Sen slope; p-value = fraction of
     * permuted |slope| >= observed |slope|.
     */
    fun permutationTestSlope(
        x: List<Double>,
        y: List<Double>,
        iterations: Int = 500,
    ): Double {
        val observed = theilSen(x, y).slope
        val absObserved = abs(observed)
        val rng = Random(42)
        var exceedCount = 0

        repeat(iterations) {
            val permY = y.toMutableList()
            permY.shuffle(rng)
            val permSlope = abs(theilSen(x, permY).slope)
            if (permSlope >= absObserved) exceedCount++
        }

        return exceedCount.toDouble() / iterations
    }

    internal fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }
}
