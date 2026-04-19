package com.example.lomanalyzer.analysis.anomaly

import com.example.lomanalyzer.analysis.lom.CombinedRole
import java.time.LocalDate

/**
 * Giant activation detector with routine protection per v6 §19.4.
 *
 * Routine protection belt-and-suspenders:
 * - baseline >= 60 days AND N_topic_baseline >= 2 → suppress (ROUTINE_TOPIC_POSTS_IN_BASELINE)
 * - N_topic_baseline == 1 → severity × 0.8
 * - N_topic_baseline == 0 → full severity × 1.0
 */
data class GiantCandidate(
    val authorId: Int,
    val combinedRole: CombinedRole,
    val iBaseHist: Double,
    val followers: Int,
    val riskTone: Double,
    val dayDate: LocalDate,
    val fP75Ref: Int = 13000,
    val nTopicBaseline: Int = 0,
    val baselineDays: Int = 60,
)

object GiantActivationDetector {

    fun detect(candidates: List<GiantCandidate>): List<AnomalyDetection> =
        candidates.mapNotNull { detectSingle(it) }

    @Suppress("ReturnCount")
    private fun detectSingle(c: GiantCandidate): AnomalyDetection? {
        val roleMult = roleMultiplier(c) ?: return null

        // Routine protection per §19.4
        if (c.baselineDays >= 60 && c.nTopicBaseline >= 2) {
            return AnomalyDetection(
                type = "GIANT_ACTIVATION",
                dayDate = c.dayDate,
                severity = 0.0,
                zScore = 0.0,
                description = "Suppressed: ROUTINE_TOPIC_POSTS_IN_BASELINE",
                routineProtectionApplied = true,
            )
        }

        val routineMult = when {
            c.nTopicBaseline == 1 -> 0.8
            else -> 1.0 // N_topic_baseline == 0
        }

        val severity = (c.iBaseHist * (0.3 + 0.7 * c.riskTone) * roleMult * routineMult)
            .coerceIn(0.0, 1.0)

        return AnomalyDetection(
            type = "GIANT_ACTIVATION",
            dayDate = c.dayDate,
            severity = severity,
            zScore = 0.0,
            description = "Giant activation: ${c.combinedRole}" +
                if (routineMult < 1.0) " (routine ×$routineMult)" else "",
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
