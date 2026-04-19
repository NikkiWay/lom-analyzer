package com.example.lomanalyzer.analysis.anomaly

import kotlin.math.sqrt

data class ZScorePoint(
    val index: Int,
    val value: Double,
    val zScore: Double,
    val rollingMean: Double,
    val rollingSigma: Double,
)

/**
 * 7-day window rolling z-score.
 * Protect against sigma_rolling < sigma_min.
 */
class RollingZScore(
    private val windowSize: Int = 7,
    private val sigmaMin: Double = 1.0,
) {
    fun compute(values: List<Double>): List<ZScorePoint> {
        if (values.size < windowSize) {
            return values.mapIndexed { i, v -> ZScorePoint(i, v, 0.0, v, 0.0) }
        }

        return values.mapIndexed { i, value ->
            val windowStart = maxOf(0, i - windowSize)
            val window = values.subList(windowStart, i + 1)
            val mean = window.average()
            val sigma = maxOf(stddev(window, mean), sigmaMin)
            val z = if (sigma > 0) (value - mean) / sigma else 0.0
            ZScorePoint(i, value, z, mean, sigma)
        }
    }

    private fun stddev(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }
}
