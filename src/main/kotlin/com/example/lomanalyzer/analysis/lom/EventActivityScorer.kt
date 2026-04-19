package com.example.lomanalyzer.analysis.lom

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger

// NOTE: Orthogonalization of T (Prompt 18) will conditionally switch to Set A weights:
//   Set A (orthogonalized): T=0.20, V=0.25, S=0.35, O=0.20
//   Set B (non-orthogonalized): T=0.15, V=0.30, S=0.35, O=0.20

data class EventWeights(
    val wT: Double = 0.15,
    val wV: Double = 0.30,
    val wS: Double = 0.35,
    val wO: Double = 0.20,
) {
    companion object {
        val SET_B = EventWeights(0.15, 0.30, 0.35, 0.20)
        val SET_A = EventWeights(0.20, 0.25, 0.35, 0.20)
    }
}

data class AuthorEventData(
    val authorId: Int,
    val topicCount: Int,
    val allCount: Int,
    val topicEffCount: Int,
    val baselineDays: Int,
    val discoverySource: String,
    val postReaches: List<Long>,
    val originalityWeights: List<Float>,
)

data class EventScoreResult(
    val authorId: Int,
    val tRaw: Double,
    val vRaw: Double,
    val sRaw: Double,
    val oRaw: Double,
    val tNorm: Double,
    val vNorm: Double,
    val sNorm: Double,
    val oNorm: Double,
    val iEvent: Double,
    val kWindow: Int,
)

class EventActivityScorer(
    private val normalizer: RobustNormalizer,
    private val weights: EventWeights = EventWeights.SET_B,
    @Suppress("UnusedPrivateProperty") private val logger: Logger,
) {
    @Suppress("LongMethod")
    fun score(
        authors: List<AuthorEventData>,
        totalTopicAll: Int,
        totalPostsAll: Int,
    ): List<EventScoreResult> {
        if (authors.isEmpty()) return emptyList()

        // Compute raw scores
        val rawScores = authors.map { author ->
            val prior = TopicFocusComponent.computeLeaveOneOutPrior(
                totalTopicAll, totalPostsAll, author.topicCount, author.allCount,
            )
            val tRaw = TopicFocusComponent.computeRaw(author.topicCount, author.allCount, prior)
            val kWindow = TopicalVolumeComponent.computeKWindow(author.baselineDays)
            val vRaw = TopicalVolumeComponent.computeRaw(author.topicEffCount, kWindow)
            val reach = DisseminationReachComponent()
            val sRaw = reach.computeAuthorRaw(author.postReaches)
            val oRaw = ContentOriginalityComponent.computeRaw(author.originalityWeights, author.topicEffCount)

            RawEventScore(author.authorId, tRaw, vRaw, sRaw, oRaw, author.baselineDays)
        }

        // Normalize
        val tStats = normalizer.computeStats(rawScores.map { it.tRaw })
        val vStats = normalizer.computeStats(rawScores.map { it.vRaw })
        val sStats = normalizer.computeStats(rawScores.map { it.sRaw })
        val oStats = normalizer.computeStats(rawScores.map { it.oRaw })

        return rawScores.map { raw ->
            val tNorm = normalizer.normalize(raw.tRaw, tStats)
            val vNorm = normalizer.normalize(raw.vRaw, vStats)
            val sNorm = normalizer.normalize(raw.sRaw, sStats)
            val oNorm = normalizer.normalize(raw.oRaw, oStats)

            val iEvent = weights.wT * tNorm + weights.wV * vNorm + weights.wS * sNorm + weights.wO * oNorm
            val kWindowInt = TopicalVolumeComponent.computeKWindow(raw.baselineDays).let { (it * 100).toInt() }

            EventScoreResult(raw.authorId, raw.tRaw, raw.vRaw, raw.sRaw, raw.oRaw,
                tNorm, vNorm, sNorm, oNorm, iEvent, kWindowInt)
        }
    }
}

private data class RawEventScore(
    val authorId: Int,
    val tRaw: Double,
    val vRaw: Double,
    val sRaw: Double,
    val oRaw: Double,
    val baselineDays: Int,
)
