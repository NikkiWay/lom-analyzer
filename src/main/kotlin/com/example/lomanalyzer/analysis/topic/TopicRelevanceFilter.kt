package com.example.lomanalyzer.analysis.topic

import kotlin.math.min

data class TopicScoreResult(
    val l1: Float,
    val l2: Float?,
    val combined: Float,
    val relevant: Boolean,
    val method: String,
)

class TopicRelevanceFilter(
    private val semanticScorer: SemanticScorer? = null,
    private val nlpMode: String = "FALLBACK_ONLY",
    private val threshold: Float = if (nlpMode == "FULL") 0.30f else 0.33f,
) {
    companion object {
        private const val L1_WEIGHT = 0.4f
        private const val L2_WEIGHT = 0.6f
        private const val L1_SCALE = 3f
        private const val SECONDARY_FACTOR = 0.3f
    }

    fun computeL1(matchResult: NgramMatchResult): Float {
        if (matchResult.excludedHit) return 0f
        val raw = matchResult.primaryHits + SECONDARY_FACTOR * matchResult.secondaryHits
        return min(raw, L1_SCALE) / L1_SCALE
    }

    suspend fun score(
        matchResult: NgramMatchResult,
        postText: String,
    ): TopicScoreResult {
        val l1 = computeL1(matchResult)

        return if (nlpMode == "FULL" && semanticScorer != null && semanticScorer.isInitialized()) {
            val sem = semanticScorer.score(postText)
            val combined = L1_WEIGHT * l1 + L2_WEIGHT * sem
            TopicScoreResult(
                l1 = l1,
                l2 = sem,
                combined = combined,
                relevant = combined >= threshold,
                method = "L1_L2",
            )
        } else {
            TopicScoreResult(
                l1 = l1,
                l2 = null,
                combined = l1,
                relevant = l1 >= threshold,
                method = "L1_ONLY",
            )
        }
    }
}
