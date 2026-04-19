package com.example.lomanalyzer.analysis.lom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

/**
 * Two-level bootstrap configuration per v6 §16.1.
 * Full: 300 outer × 100 inner. Gamma and orthogonalization remain fixed.
 * Leave-one-out prior is recomputed inside each inner iteration scope.
 */
data class BootstrapConfig(
    val outerIterations: Int = 300,
    val innerIterations: Int = 100,
    val ciLowerPct: Double = 0.05,
    val ciUpperPct: Double = 0.95,
)

data class BootstrapResult(
    val pointEstimate: Double,
    val ciLo: Double,
    val ciHi: Double,
)

data class AuthorBaselineData(
    val authorId: Int,
    val followers: Int,
    val postReactions: List<PostReactions>,
    val imputed: Boolean,
)

/**
 * Full two-level bootstrap (300×100) per v6 §16.1.
 * Outer: resample authors with replacement, recompute median/IQR normalization.
 * Inner: resample posts per author, recompute r_bar.
 * Parallelized across CPU cores via Dispatchers.Default.
 * Gamma and orthogonalization results remain fixed (session point estimates).
 */
class BootstrapEstimator(
    private val config: BootstrapConfig = BootstrapConfig(),
    private val normalizer: RobustNormalizer = RobustNormalizer(),
) {
    @Suppress("LongParameterList")
    suspend fun estimateIBase(
        authors: List<AuthorBaselineData>,
        gamma: Double,
        aWeight: Double = 0.55,
        eWeight: Double = 0.45,
    ): Map<Int, BootstrapResult> = coroutineScope {
        if (authors.isEmpty()) return@coroutineScope emptyMap()

        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val chunkSize = (config.outerIterations + coreCount - 1) / coreCount
        val chunks = (0 until config.outerIterations).chunked(chunkSize)

        val allIterResults = chunks.map { iterRange ->
            async(Dispatchers.Default) {
                val rng = Random(iterRange.first())
                iterRange.map { runOuterIteration(authors, gamma, aWeight, eWeight, rng) }
            }
        }.awaitAll().flatten()

        // Aggregate per author
        val authorScores = mutableMapOf<Int, MutableList<Double>>()
        for (iterScores in allIterResults) {
            for ((authorId, score) in iterScores) {
                authorScores.getOrPut(authorId) { mutableListOf() }.add(score)
            }
        }

        authorScores.mapValues { (_, scores) ->
            scores.sort()
            val lo = percentile(scores, config.ciLowerPct)
            val hi = percentile(scores, config.ciUpperPct)
            val point = percentile(scores, 0.5)
            BootstrapResult(point, lo, hi)
        }
    }

    @Suppress("LongParameterList")
    private fun runOuterIteration(
        authors: List<AuthorBaselineData>,
        gamma: Double,
        aWeight: Double,
        eWeight: Double,
        rng: Random,
    ): Map<Int, Double> {
        val n = authors.size
        val resampled = (0 until n).map { authors[rng.nextInt(n)] }

        // Inner bootstrap: resample posts per author, recompute r_bar
        val authorRawScores = resampled.map { author ->
            val rBar = innerBootstrapMeanReaction(author.postReactions, rng)
            val aRaw = AudienceComponent.computeRaw(author.followers)
            val eRaw = EngagementDensityComponent.computeRaw(rBar, author.followers, gamma)
            Triple(author.authorId, aRaw, eRaw)
        }

        // Recompute normalization stats per outer iteration
        val aValues = authorRawScores.map { it.second }
        val eValues = authorRawScores.map { it.third }
        val aStats = normalizer.computeStats(aValues)
        val eStats = normalizer.computeStats(eValues)

        return authorRawScores.associate { (id, aRaw, eRaw) ->
            val aNorm = normalizer.normalize(aRaw, aStats)
            val eNorm = normalizer.normalize(eRaw, eStats)
            id to (aWeight * aNorm + eWeight * eNorm)
        }
    }

    private fun innerBootstrapMeanReaction(posts: List<PostReactions>, rng: Random): Double {
        if (posts.isEmpty()) return 0.0
        val n = posts.size
        var total = 0.0
        repeat(config.innerIterations) {
            total += EngagementDensityComponent.weightedReaction(posts[rng.nextInt(n)])
        }
        return total / config.innerIterations
    }
}
