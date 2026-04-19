package com.example.lomanalyzer.analysis.content

import com.example.lomanalyzer.analysis.lom.RobustRegression
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Huber M-estimator aggregation for actor-level sentiment per v6 §12.4.
 * k = 1.345, convergence |Δμ| < 1e-4, max 20 iterations.
 * Bootstrap 200 for CI. Flag AUTHOR_TONE_MIXED when SD > 0.5.
 */
data class HuberAggregateResult(
    val huberMean: Float,
    val ciLo: Float,
    val ciHi: Float,
    val sd: Float,
    val toneMixedFlag: Boolean,
    val unstableRatio: Float,
)

object HuberAggregator {
    private const val BOOTSTRAP_ITERATIONS = 200
    private const val TONE_MIXED_THRESHOLD = 0.5

    fun aggregate(
        sentimentScores: List<Float>,
        unstableCount: Int = 0,
    ): HuberAggregateResult {
        if (sentimentScores.isEmpty()) {
            return HuberAggregateResult(0f, 0f, 0f, 0f, false, 0f)
        }

        val values = sentimentScores.map { it.toDouble() }
        val huberMean = RobustRegression.huber(values).toFloat()

        // SD for AUTHOR_TONE_MIXED flag
        val variance = values.sumOf { (it - huberMean) * (it - huberMean) } / values.size
        val sd = sqrt(variance).toFloat()

        // Bootstrap 200 for CI
        val rng = Random(42)
        val bootstrapMeans = mutableListOf<Float>()
        repeat(BOOTSTRAP_ITERATIONS) {
            val sample = (0 until values.size).map {
                values[rng.nextInt(values.size)]
            }
            bootstrapMeans.add(RobustRegression.huber(sample).toFloat())
        }
        bootstrapMeans.sort()
        val ciLo = bootstrapMeans[(bootstrapMeans.size * 0.05).toInt()]
        val ciHi = bootstrapMeans[
            (bootstrapMeans.size * 0.95).toInt().coerceAtMost(bootstrapMeans.size - 1)
        ]

        val unstableRatio = if (sentimentScores.isNotEmpty()) {
            unstableCount.toFloat() / sentimentScores.size
        } else 0f

        return HuberAggregateResult(
            huberMean = huberMean,
            ciLo = ciLo,
            ciHi = ciHi,
            sd = sd,
            toneMixedFlag = sd > TONE_MIXED_THRESHOLD,
            unstableRatio = unstableRatio,
        )
    }
}
