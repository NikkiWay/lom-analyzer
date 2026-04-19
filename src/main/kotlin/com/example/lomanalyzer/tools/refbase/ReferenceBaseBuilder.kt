package com.example.lomanalyzer.tools.refbase

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Stand-alone CLI tool to build reference_base.json per v6 §17.2-17.6.
 *
 * Stratified sampling: 15,000 VK public accounts.
 * Audience: <1k (35%), 1k-10k (40%), 10k-100k (20%), >100k (5%).
 * Region: msk (30%), spb (8%), regions (62%).
 * Type: personal (60%), community (40%).
 *
 * Usage: Run with VK API token as environment variable VK_TOKEN.
 * Expected runtime: 4-8 hours with rate limiting.
 */

data class StratificationConfig(
    val totalSample: Int = 15000,
    val audienceBuckets: Map<String, Double> = mapOf(
        "lt1k" to 0.35, "1k_10k" to 0.40, "10k_100k" to 0.20, "gt100k" to 0.05,
    ),
    val regionBuckets: Map<String, Double> = mapOf(
        "msk" to 0.30, "spb" to 0.08, "regions" to 0.62,
    ),
    val typeBuckets: Map<String, Double> = mapOf(
        "personal" to 0.60, "community" to 0.40,
    ),
)

data class ReferenceAuthor(
    val vkId: Int,
    val followersCount: Int,
    val totalPosts: Int,
    val totalWeightedReactions: Double,
    val region: String,
    val accountType: String,
)

data class QuantileResult(
    val q10: Double, val q25: Double, val q50: Double,
    val q75: Double, val q90: Double, val iqr: Double,
)

data class ReferenceOutput(
    val version: String,
    val sampleSize: Int,
    val gammaRef: Double = 0.45,
    val lnFQuantiles: QuantileResult,
    val lnRBarQuantiles: QuantileResult,
    val eRawQuantiles: QuantileResult,
    val iBaseP50: Double,
    val iBaseP75: Double,
    val iBaseP90: Double,
    val tauBaseP75: Double,
    val fTildeReference: Int,
    val sha256: String = "",
)

object ReferenceBaseComputer {
    private const val GAMMA_REF = 0.45

    fun computeStratificationCounts(config: StratificationConfig): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for ((bucket, pct) in config.audienceBuckets) {
            counts[bucket] = (config.totalSample * pct).toInt()
        }
        return counts
    }

    fun computeRawMetrics(author: ReferenceAuthor): Triple<Double, Double, Double> {
        val lnF = ln(1.0 + author.followersCount)
        val rBar = if (author.totalPosts > 0) {
            author.totalWeightedReactions / author.totalPosts
        } else 0.0
        val lnRBar = ln(1.0 + rBar)
        val eRaw = lnRBar - GAMMA_REF * lnF
        return Triple(lnF, lnRBar, eRaw)
    }

    fun computeQuantiles(values: List<Double>): QuantileResult {
        val sorted = values.sorted()
        return QuantileResult(
            q10 = percentile(sorted, 0.10),
            q25 = percentile(sorted, 0.25),
            q50 = percentile(sorted, 0.50),
            q75 = percentile(sorted, 0.75),
            q90 = percentile(sorted, 0.90),
            iqr = percentile(sorted, 0.75) - percentile(sorted, 0.25),
        )
    }

    fun computeIBaseRef(
        lnF: Double,
        eRaw: Double,
        lnFQuantiles: QuantileResult,
        eRawQuantiles: QuantileResult,
    ): Double {
        val zA = (lnF - lnFQuantiles.q50) / (lnFQuantiles.iqr + 1e-9)
        val zE = (eRaw - eRawQuantiles.q50) / (eRawQuantiles.iqr + 1e-9)
        return 0.55 * sigmoid(zA) + 0.45 * sigmoid(zE)
    }

    fun buildOutput(authors: List<ReferenceAuthor>): ReferenceOutput {
        val metrics = authors.map { computeRawMetrics(it) }
        val lnFs = metrics.map { it.first }
        val lnRBars = metrics.map { it.second }
        val eRaws = metrics.map { it.third }

        val lnFQ = computeQuantiles(lnFs)
        val lnRBarQ = computeQuantiles(lnRBars)
        val eRawQ = computeQuantiles(eRaws)

        val iBaseValues = metrics.map { (lnF, _, eRaw) ->
            computeIBaseRef(lnF, eRaw, lnFQ, eRawQ)
        }
        val iBaseQ = computeQuantiles(iBaseValues)

        return ReferenceOutput(
            version = "1.0.0",
            sampleSize = authors.size,
            lnFQuantiles = lnFQ,
            lnRBarQuantiles = lnRBarQ,
            eRawQuantiles = eRawQ,
            iBaseP50 = iBaseQ.q50,
            iBaseP75 = iBaseQ.q75,
            iBaseP90 = iBaseQ.q90,
            tauBaseP75 = iBaseQ.q75,
            fTildeReference = computeFTilde(authors),
        )
    }

    private fun computeFTilde(authors: List<ReferenceAuthor>): Int {
        val sorted = authors.map { it.followersCount }.sorted()
        return if (sorted.isNotEmpty()) sorted[sorted.size / 2] else 500
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val idx = (sorted.size * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun sigmoid(z: Double): Double = 1.0 / (1.0 + kotlin.math.exp(-z))
}
