package com.example.lomanalyzer.analysis.risk

import com.example.lomanalyzer.analysis.anomaly.AnomalyDetection
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

data class RiskCiResult(
    val riskScore: Double,
    val ciLo: Double,
    val ciHi: Double,
    val isBorderline: Boolean,
)

/**
 * Block bootstrap with 7-day blocks, 100 iterations in MVP (300 in Prompt 19).
 * Flag BORDERLINE if CI crosses risk level boundaries (0.15, 0.35, 0.55).
 */
class BlockBootstrap(
    private val blockDays: Int = 7,
    private val iterations: Int = 100,
) {
    companion object {
        private val RISK_BOUNDARIES = listOf(0.15, 0.35, 0.55)
    }

    @Suppress("ReturnCount")
    fun bootstrap(anomalies: List<AnomalyDetection>): RiskCiResult {
        if (anomalies.isEmpty()) {
            return RiskCiResult(0.0, 0.0, 0.0, false)
        }

        val pointEstimate = RiskScorer.computeRisk(anomalies).riskScore

        val dates = anomalies.map { it.dayDate }.distinct().sorted()
        if (dates.size < 2) {
            return RiskCiResult(pointEstimate, pointEstimate, pointEstimate, false)
        }

        val blocks = buildBlocks(anomalies, dates)
        val rng = Random(42)
        val bootstrapScores = mutableListOf<Double>()

        repeat(iterations) {
            val resampledBlocks = (0 until blocks.size).map { blocks[rng.nextInt(blocks.size)] }
            val resampledAnomalies = resampledBlocks.flatten()
            val score = RiskScorer.computeRisk(resampledAnomalies).riskScore
            bootstrapScores.add(score)
        }

        bootstrapScores.sort()
        val ciLo = bootstrapScores[(bootstrapScores.size * 0.05).toInt()]
        val ciHi = bootstrapScores[
            (bootstrapScores.size * 0.95).toInt().coerceAtMost(bootstrapScores.size - 1)
        ]

        val borderline = RISK_BOUNDARIES.any { b -> ciLo < b && ciHi >= b }

        return RiskCiResult(pointEstimate, ciLo, ciHi, borderline)
    }

    private fun buildBlocks(
        anomalies: List<AnomalyDetection>,
        dates: List<LocalDate>,
    ): List<List<AnomalyDetection>> {
        val blocks = mutableListOf<List<AnomalyDetection>>()
        var blockStart = dates.first()

        while (blockStart <= dates.last()) {
            val blockEnd = blockStart.plusDays(blockDays.toLong())
            val blockAnomalies = anomalies.filter {
                !it.dayDate.isBefore(blockStart) && it.dayDate.isBefore(blockEnd)
            }
            if (blockAnomalies.isNotEmpty()) {
                blocks.add(blockAnomalies)
            }
            blockStart = blockEnd
        }

        if (blocks.isEmpty()) blocks.add(anomalies)
        return blocks
    }
}
