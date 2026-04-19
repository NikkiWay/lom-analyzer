package com.example.lomanalyzer.analysis.lom

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import kotlin.math.ln

/**
 * Orthogonalization of T (topic focus) per v6 §14.2.1.
 *
 * Theil-Sen on (ln(1 + N_topic_eff), T_raw) across all authors.
 * Apply if: R²_MAD >= 0.05 AND permutation p-value < 0.05 AND slope > 0.
 * Result: T_perp = T_raw - intercept - slope * ln(1 + N_topic_eff).
 * Weight set: A if applied, B otherwise.
 */
data class OrthogonalizationResult(
    val applied: Boolean,
    val madR2: Double,
    val pValue: Double,
    val slope: Double,
    val intercept: Double,
    val weights: EventWeights,
    val residuals: Map<Int, Double>,
)

class OrthogonalizerT(
    private val logger: Logger,
    private val permutationIterations: Int = 500,
) {
    companion object {
        private const val MIN_R2 = 0.05
        private const val MAX_P_VALUE = 0.05
    }

    fun orthogonalize(
        authorData: List<AuthorOrthogonalizationInput>,
    ): OrthogonalizationResult {
        if (authorData.size < 5) {
            return skipResult(authorData)
        }

        val xs = authorData.map { ln(1.0 + it.topicEffCount) }
        val ys = authorData.map { it.tRaw }

        val reg = RobustRegression.theilSen(xs, ys)
        val r2 = RobustRegression.madR2(xs, ys, reg.slope, reg.intercept)
        val pValue = RobustRegression.permutationTestSlope(
            xs, ys, permutationIterations,
        )

        val shouldApply = r2 >= MIN_R2 && pValue < MAX_P_VALUE && reg.slope > 0

        logger.event(AppEvent.T_ORTHOGONALIZATION_DECISION, mapOf(
            "r2_mad" to r2,
            "p_value" to pValue,
            "slope" to reg.slope,
            "applied" to shouldApply,
        ))

        val residuals = if (shouldApply) {
            authorData.associate { author ->
                val x = ln(1.0 + author.topicEffCount)
                author.authorId to (author.tRaw - reg.intercept - reg.slope * x)
            }
        } else {
            authorData.associate { it.authorId to it.tRaw }
        }

        return OrthogonalizationResult(
            applied = shouldApply,
            madR2 = r2,
            pValue = pValue,
            slope = reg.slope,
            intercept = reg.intercept,
            weights = if (shouldApply) EventWeights.SET_A else EventWeights.SET_B,
            residuals = residuals,
        )
    }

    private fun skipResult(
        authorData: List<AuthorOrthogonalizationInput>,
    ) = OrthogonalizationResult(
        applied = false, madR2 = 0.0, pValue = 1.0,
        slope = 0.0, intercept = 0.0,
        weights = EventWeights.SET_B,
        residuals = authorData.associate { it.authorId to it.tRaw },
    )
}

data class AuthorOrthogonalizationInput(
    val authorId: Int,
    val topicEffCount: Int,
    val tRaw: Double,
)
