package com.example.lomanalyzer.analysis.topic

import kotlin.math.sqrt

data class BetaPosterior(
    val mean: Double,
    val ci95Lo: Double,
    val ci95Hi: Double,
    val alpha: Double,
    val beta: Double,
)

data class ValidationMetrics(
    val precision: BetaPosterior,
    val recall: BetaPosterior,
    val totalVotes: Int,
)

object BayesBetaValidator {
    /**
     * Computes Bayes Beta posteriors for precision and recall.
     * Prior: Beta(1, 1) (uniform).
     *
     * @param votes list of (systemRelevant, analystVote) pairs
     *   where analystVote: true=relevant, false=not relevant, null=unsure (skipped)
     */
    fun computeMetrics(votes: List<Pair<Boolean, Boolean?>>): ValidationMetrics {
        val resolved = votes.filter { it.second != null }

        var tp = 0; var fp = 0; var fn = 0
        for ((systemRelevant, analystVote) in resolved) {
            val actual = analystVote!!
            when {
                systemRelevant && actual -> tp++
                systemRelevant && !actual -> fp++
                !systemRelevant && actual -> fn++
            }
        }

        val precision = betaPosterior(1.0 + tp, 1.0 + fp)
        val recall = betaPosterior(1.0 + tp, 1.0 + fn)

        return ValidationMetrics(
            precision = precision,
            recall = recall,
            totalVotes = resolved.size,
        )
    }

    private fun betaPosterior(alpha: Double, beta: Double): BetaPosterior {
        val mean = alpha / (alpha + beta)
        // Normal approximation for 95% CI
        val variance = (alpha * beta) / ((alpha + beta).let { it * it * (it + 1) })
        val sd = sqrt(variance)
        return BetaPosterior(
            mean = mean,
            ci95Lo = (mean - 1.96 * sd).coerceAtLeast(0.0),
            ci95Hi = (mean + 1.96 * sd).coerceAtMost(1.0),
            alpha = alpha,
            beta = beta,
        )
    }
}
