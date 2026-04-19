package com.example.lomanalyzer.analysis.anomaly

import com.example.lomanalyzer.analysis.lom.CombinedRole
import java.time.LocalDate

// TODO(Prompt-23): Add routine protection based on N_topic_baseline.

data class GiantCandidate(
    val authorId: Int,
    val combinedRole: CombinedRole,
    val iBaseHist: Double,
    val followers: Int,
    val riskTone: Double,
    val dayDate: LocalDate,
    val fP75Ref: Int = 13000,
)

/**
 * MVP: trigger when SLEEPING_GIANT_CONFIRMED (mult 1.0),
 * SLEEPING_GIANT_LOCAL (mult 0.7), or BASELINE_UNKNOWN with F > F_p75 (mult 0.5)
 * publishes a topical post in current window.
 * Severity = I_base * (0.3 + 0.7 * risk_tone) * role_multiplier.
 */
object GiantActivationDetector {

    fun detect(candidates: List<GiantCandidate>): List<AnomalyDetection> =
        candidates.mapNotNull { candidate ->
            val mult = roleMultiplier(candidate) ?: return@mapNotNull null
            val severity = (candidate.iBaseHist * (0.3 + 0.7 * candidate.riskTone) * mult)
                .coerceIn(0.0, 1.0)

            AnomalyDetection(
                type = "GIANT_ACTIVATION",
                dayDate = candidate.dayDate,
                severity = severity,
                zScore = 0.0,
                description = "Giant activation: ${candidate.combinedRole}",
            )
        }

    @Suppress("ReturnCount")
    private fun roleMultiplier(c: GiantCandidate): Double? = when (c.combinedRole) {
        CombinedRole.SLEEPING_GIANT_CONFIRMED -> 1.0
        CombinedRole.SLEEPING_GIANT_LOCAL -> 0.7
        CombinedRole.BASELINE_UNKNOWN -> if (c.followers > c.fP75Ref) 0.5 else null
        else -> null
    }
}
