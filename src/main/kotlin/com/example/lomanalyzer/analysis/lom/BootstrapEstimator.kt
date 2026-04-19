package com.example.lomanalyzer.analysis.lom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

data class BootstrapConfig(
    val outerIterations: Int = 100,
    val innerIterations: Int = 30,
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
 * Simplified two-level bootstrap (100x30 for MVP).
 * Outer: resample authors with replacement, recompute normalization.
 * Inner: resample posts per author, recompute r_bar.
 * Expose BootstrapConfig so Prompt 19 can upgrade to 300x100.
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

        val chunkSize = (config.outerIterations + 3) / 4
        val chunks = (0 until config.outerIterations).chunked(chunkSize)

        // Each chunk runs in parallel on Dispatchers.Default
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

        // Inner bootstrap: resample posts per author
        val authorRawScores = resampled.map { author ->
            val rBar = innerBootstrapMeanReaction(author.postReactions, rng)
            val aRaw = AudienceComponent.computeRaw(author.followers)
            val eRaw = EngagementDensityComponent.computeRaw(rBar, author.followers, gamma)
            Triple(author.authorId, aRaw, eRaw)
        }

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
            val idx = rng.nextInt(n)
            total += EngagementDensityComponent.weightedReaction(posts[idx])
        }
        return total / config.innerIterations
    }
}
