package com.example.lomanalyzer.analysis.dedup

import kotlin.math.abs

/**
 * Stage 2: Bounded Jaccard near-duplicate detection.
 * Applied ONLY to topical posts with ownTextLength >= 100 (performance optimization).
 * Uses word-level bigrams on lemmas; window ±72 hours; threshold 0.75.
 */
class BoundedJaccard(
    private val threshold: Float = 0.75f,
    private val windowHours: Long = 72,
) {
    companion object {
        private const val MIN_TEXT_LENGTH = 100
    }

    fun isEligible(ownTextLength: Int, isTopicRelevant: Boolean?): Boolean =
        ownTextLength >= MIN_TEXT_LENGTH && isTopicRelevant == true

    @Suppress("ReturnCount")
    fun computeSimilarity(lemmasA: List<String>, lemmasB: List<String>): Float {
        val bigramsA = toBigrams(lemmasA)
        val bigramsB = toBigrams(lemmasB)

        if (bigramsA.isEmpty() && bigramsB.isEmpty()) return 1f
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0f

        val intersection = bigramsA.intersect(bigramsB).size
        val union = bigramsA.union(bigramsB).size

        return if (union > 0) intersection.toFloat() / union else 0f
    }

    fun isWithinWindow(publishedAtA: Long, publishedAtB: Long): Boolean {
        val diffMs = abs(publishedAtA - publishedAtB)
        val windowMs = windowHours * 3600 * 1000
        return diffMs <= windowMs
    }

    fun isNearDuplicate(
        lemmasA: List<String>,
        lemmasB: List<String>,
        publishedAtA: Long,
        publishedAtB: Long,
    ): Pair<Boolean, Float> {
        if (!isWithinWindow(publishedAtA, publishedAtB)) return false to 0f
        val sim = computeSimilarity(lemmasA, lemmasB)
        return (sim >= threshold) to sim
    }

    private fun toBigrams(lemmas: List<String>): Set<String> {
        if (lemmas.size < 2) return lemmas.toSet()
        return lemmas.windowed(2).map { "${it[0]} ${it[1]}" }.toSet()
    }
}
