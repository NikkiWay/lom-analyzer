package com.example.lomanalyzer.persona

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.storage.dao.LomScoreDao
import com.example.lomanalyzer.storage.dao.PersonaAggregateDao
import com.example.lomanalyzer.storage.tables.LomScores
import com.example.lomanalyzer.storage.tables.PersonaAggregates
import java.time.Instant

// TODO(Prompt-19): Replace median aggregation with Huber M-estimator for
//   actor-level sentiment (§12.4). The column avgSentimentHuber stores
//   median for MVP; Huber will overwrite it.

data class AuthorSentimentData(
    val authorId: Int,
    val sentimentScores: List<Float>,
    val mediaPostCount: Int,
    val totalPostCount: Int,
    val topTerms: String?,
    val discoverySource: String,
    val baselineWindowDays: Int,
)

class PersonaAggregator(
    private val lomScoreDao: LomScoreDao,
    private val personaAggregateDao: PersonaAggregateDao,
    private val logger: Logger,
) {
    fun aggregate(sessionId: Int, authorData: List<AuthorSentimentData>) {
        val lomScores = lomScoreDao.findBySession(sessionId)
        val lomByAuthor = lomScores.associateBy { it[LomScores.authorId].value }
        val now = Instant.now().toEpochMilli()

        for (author in authorData) {
            val lom = lomByAuthor[author.authorId]

            // Median aggregation for MVP (Huber in Prompt 19)
            val medianSentiment = medianOrNull(author.sentimentScores)
            val visualRatio = com.example.lomanalyzer.analysis.content.VisualActivityEstimator
                .compute(author.mediaPostCount, author.totalPostCount)

            personaAggregateDao.insert(sessionId, author.authorId, now) {
                it[PersonaAggregates.iBaseHist] = lom?.get(LomScores.baseInfluenceHist)
                it[PersonaAggregates.iBaseHistCiLo] = lom?.get(LomScores.baseInfluenceHistCiLo)
                it[PersonaAggregates.iBaseHistCiHi] = lom?.get(LomScores.baseInfluenceHistCiHi)
                it[PersonaAggregates.audience] = lom?.get(LomScores.audienceNorm)
                it[PersonaAggregates.engagementDensity] = lom?.get(LomScores.engagementDensityNorm)
                it[PersonaAggregates.roleSession] = lom?.get(LomScores.roleSession)
                it[PersonaAggregates.roleReference] = lom?.get(LomScores.roleReference)
                it[PersonaAggregates.roleCombined] = lom?.get(LomScores.roleCombined)
                it[PersonaAggregates.roleConfidence] = lom?.get(LomScores.roleConfidence)
                it[PersonaAggregates.roleCombinationFlag] = lom?.get(LomScores.roleCombinationFlag)
                it[PersonaAggregates.topicFocusRaw] = lom?.get(LomScores.topicFocusRaw)
                it[PersonaAggregates.topicVolumeNorm] = lom?.get(LomScores.topicVolumeNorm)
                it[PersonaAggregates.disseminationReachNorm] = lom?.get(LomScores.disseminationReachNorm)
                it[PersonaAggregates.originalityNorm] = lom?.get(LomScores.contentOriginalityNorm)
                it[PersonaAggregates.gammaUsed] = lom?.get(LomScores.gammaUsed)
                it[PersonaAggregates.kWindow] = lom?.get(LomScores.kWindow)
                it[PersonaAggregates.tOrthogonalized] = lom?.get(LomScores.tOrthogonalized) ?: false
                it[PersonaAggregates.avgSentimentHuber] = medianSentiment
                it[PersonaAggregates.visualActivityRatio] = visualRatio
                it[PersonaAggregates.topTerms] = author.topTerms
                it[PersonaAggregates.discoverySource] = author.discoverySource
                it[PersonaAggregates.baselineWindowDays] = author.baselineWindowDays
                it[PersonaAggregates.totalPosts] = author.totalPostCount
            }
        }

        logger.event(AppEvent.PERSONA_BUILT, mapOf(
            "session_id" to sessionId,
            "authors" to authorData.size,
        ))
    }

    private fun medianOrNull(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }
}
