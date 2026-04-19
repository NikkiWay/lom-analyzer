package com.example.lomanalyzer.analysis.lom

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PipelineStage
import com.example.lomanalyzer.orchestration.StageExecutor
import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.LomScoreDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.tables.Authors
import com.example.lomanalyzer.storage.tables.LomScores
import com.example.lomanalyzer.storage.tables.Posts
import java.time.Instant

class BaseInfluenceScorer(
    private val postDao: PostDao,
    private val authorDao: AuthorDao,
    private val lomScoreDao: LomScoreDao,
    private val gammaCalibrator: GammaCalibrator,
    private val normalizer: RobustNormalizer,
    private val bootstrapEstimator: BootstrapEstimator,
    private val logger: Logger,
) : StageExecutor {

    companion object {
        private const val A_WEIGHT = 0.55
        private const val E_WEIGHT = 0.45
    }

    @Suppress("LongMethod")
    override suspend fun execute(sessionId: Int, stage: PipelineStage) {
        val baselinePosts = postDao.findBySessionAndWindow(sessionId, "BASELINE")
        val allAuthors = authorDao.findAll()
        if (allAuthors.isEmpty()) return

        // Impute closed profiles
        val openFollowers = allAuthors
            .filter { !it[Authors.isClosed] && it[Authors.followersCount] != null }
            .mapNotNull { it[Authors.followersCount] }
        val q25 = ClosedProfileImputer.computeQ25(openFollowers)

        // Build author data
        val authorData = buildAuthorData(allAuthors, baselinePosts, q25)

        // Gamma calibration (stage 17)
        val gammaInputs = authorData.map {
            AuthorGammaInput(it.followers, EngagementDensityComponent.meanReaction(it.postReactions))
        }
        val gammaResult = gammaCalibrator.calibrate(gammaInputs)
        logger.event(AppEvent.GAMMA_CALIBRATED, mapOf(
            "gamma" to gammaResult.gamma,
            "r2" to gammaResult.r2,
            "fallback" to gammaResult.fallback,
        ))

        // Compute raw scores
        val rawScores = authorData.map { author ->
            val aRaw = AudienceComponent.computeRaw(author.followers)
            val rBar = EngagementDensityComponent.meanReaction(author.postReactions)
            val eRaw = EngagementDensityComponent.computeRaw(rBar, author.followers, gammaResult.gamma)
            AuthorRawScore(author.authorId, author.followers, aRaw, eRaw, rBar, author.imputed)
        }

        // Normalize
        val nonImputed = rawScores.filter { !it.imputed }
        val aStats = normalizer.computeStats(nonImputed.map { it.aRaw })
        val eStats = normalizer.computeStats(nonImputed.map { it.eRaw })

        if (aStats.flag != null || eStats.flag != null) {
            logger.event(AppEvent.NORMALIZATION_FALLBACK, mapOf(
                "a_flag" to (aStats.flag ?: "none"),
                "e_flag" to (eStats.flag ?: "none"),
            ))
        }

        // Bootstrap CI
        val bootstrapResults = bootstrapEstimator.estimateIBase(authorData, gammaResult.gamma)
        logger.event(AppEvent.BOOTSTRAP_COMPLETED, mapOf("session_id" to sessionId))

        // Persist
        for (score in rawScores) {
            val aNorm = normalizer.normalize(score.aRaw, aStats)
            val eNorm = normalizer.normalize(score.eRaw, eStats)
            val iBase = A_WEIGHT * aNorm + E_WEIGHT * eNorm
            val ci = bootstrapResults[score.authorId]

            lomScoreDao.insert(sessionId, score.authorId) {
                it[LomScores.audienceNorm] = aNorm.toFloat()
                it[LomScores.engagementDensityNorm] = eNorm.toFloat()
                it[LomScores.baseInfluenceHist] = iBase.toFloat()
                it[LomScores.baseInfluenceHistCiLo] = ci?.ciLo?.toFloat()
                it[LomScores.baseInfluenceHistCiHi] = ci?.ciHi?.toFloat()
                it[LomScores.gammaUsed] = gammaResult.gamma.toFloat()
            }
        }
    }

    @Suppress("NestedBlockDepth")
    private fun buildAuthorData(
        allAuthors: List<org.jetbrains.exposed.sql.ResultRow>,
        baselinePosts: List<org.jetbrains.exposed.sql.ResultRow>,
        q25: Int,
    ): List<AuthorBaselineData> {
        val postsByAuthor = baselinePosts.groupBy { it[Posts.fromId] }

        return allAuthors.map { author ->
            val authorId = author[Authors.id].value
            val vkId = author[Authors.vkId]
            val impute = ClosedProfileImputer.impute(
                author[Authors.followersCount], author[Authors.isClosed], q25,
            )

            val posts = postsByAuthor[vkId]?.map { post ->
                PostReactions(post[Posts.likes], post[Posts.reposts], post[Posts.comments])
            } ?: emptyList()

            AuthorBaselineData(authorId, impute.followers, posts, impute.imputed)
        }
    }
}

private data class AuthorRawScore(
    val authorId: Int,
    val followers: Int,
    val aRaw: Double,
    val eRaw: Double,
    val rBar: Double,
    val imputed: Boolean,
)
