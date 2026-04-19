package com.example.lomanalyzer.analysis.lom

import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

data class NormStats(
    val median: Double,
    val iqr: Double,
    val spread: Double,
    val nEff: Int,
    val cvIqr: Double?,
    val flag: String?, // null, NORM_UNSTABLE, NORM_UNSTABLE_SEVERE
    val denominator: Double,
    val fallbackUsed: String?, // null, "Q90_Q10", "FIXED_0.1"
)

/**
 * Robust Sigmoid normalization with cascade fallback per v6 §15.2.
 * z = (x_raw - median) / (denominator + epsilon)
 * x_norm = sigmoid(z) = 1 / (1 + e^-z)
 */
class RobustNormalizer(
    private val bootstrapIterations: Int = 1000,
    private val epsilon: Double = 1e-9,
) {
    companion object {
        private const val CV_IQR_UNSTABLE = 0.25
        private const val CV_IQR_SEVERE = 0.35
        private const val IQR_MIN = 0.001
        private const val FIXED_FALLBACK = 0.1
    }

    @Suppress("ReturnCount")
    fun computeStats(values: List<Double>): NormStats {
        val n = values.size
        if (n == 0) {
            return NormStats(0.0, 0.0, 0.0, 0, null, null, FIXED_FALLBACK, "FIXED_0.1")
        }
        if (n == 1) {
            return NormStats(values[0], 0.0, 0.0, 1, null, null, FIXED_FALLBACK, "FIXED_0.1")
        }

        val sorted = values.sorted()
        val median = percentile(sorted, 0.5)
        val q25 = percentile(sorted, 0.25)
        val q75 = percentile(sorted, 0.75)
        val iqr = q75 - q25

        val q10 = percentile(sorted, 0.10)
        val q90 = percentile(sorted, 0.90)
        val spread = q90 - q10

        val (denom, fallback) = cascadeDenominator(iqr, spread)

        val (cvIqr, flag) = if (n < 30) {
            val cv = bootstrapCvIqr(values)
            val f = when {
                cv > CV_IQR_SEVERE -> "NORM_UNSTABLE_SEVERE"
                cv > CV_IQR_UNSTABLE -> "NORM_UNSTABLE"
                else -> null
            }
            cv to f
        } else {
            null to null
        }

        return NormStats(median, iqr, spread, n, cvIqr, flag, denom, fallback)
    }

    fun normalize(raw: Double, stats: NormStats): Double {
        if (stats.nEff <= 1) return 0.5
        val z = (raw - stats.median) / (stats.denominator + epsilon)
        return sigmoid(z)
    }

    private fun cascadeDenominator(iqr: Double, spread: Double): Pair<Double, String?> =
        when {
            iqr >= IQR_MIN -> iqr to null
            spread >= IQR_MIN -> spread to "Q90_Q10"
            else -> FIXED_FALLBACK to "FIXED_0.1"
        }

    private fun bootstrapCvIqr(values: List<Double>): Double {
        val n = values.size
        val rng = Random(0)
        val iqrs = mutableListOf<Double>()

        repeat(bootstrapIterations) {
            val sample = (0 until n).map { values[rng.nextInt(n)] }.sorted()
            val q25 = percentile(sample, 0.25)
            val q75 = percentile(sample, 0.75)
            iqrs.add(q75 - q25)
        }

        val mean = iqrs.average()
        if (mean < epsilon) return 0.0
        val variance = iqrs.sumOf { (it - mean) * (it - mean) } / iqrs.size
        return sqrt(variance) / mean
    }

    private fun sigmoid(z: Double): Double = 1.0 / (1.0 + exp(-z))
}

internal fun percentile(sorted: List<Double>, p: Double): Double {
    if (sorted.isEmpty()) return 0.0
    val idx = (sorted.size * p).toInt().coerceIn(0, sorted.size - 1)
    return sorted[idx]
}
