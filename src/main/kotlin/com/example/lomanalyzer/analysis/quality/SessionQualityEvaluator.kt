package com.example.lomanalyzer.analysis.quality

/**
 * Computes 9 SQS components per v6 §24.1.
 * Each component returns a value in [0, 1]; some are hard gates.
 */
data class QualityComponent(
    val name: String,
    val value: Float,
    val isGate: Boolean,
    val passed: Boolean,
)

data class SessionQualityResult(
    val overallScore: Float,
    val components: List<QualityComponent>,
    val allGatesPassed: Boolean,
)

class SessionQualityEvaluator {
    @Suppress("LongParameterList")
    fun evaluate(
        coverageRatio: Float,
        topicPrecision: Float,
        topicRecall: Float,
        dedupRatio: Float,
        gammaR2: Float,
        cvIqrMax: Float,
        bootstrapWidthMean: Float,
        confidenceAbove25Pct: Float,
        referenceFreshness: Float,
    ): SessionQualityResult {
        val components = listOf(
            QualityComponent("Coverage Ratio", coverageRatio, false, true),
            QualityComponent("Topic Precision", topicPrecision, false, true),
            QualityComponent("Topic Recall", topicRecall, false, true),
            QualityComponent("Dedup Ratio", dedupRatio, false, true),
            QualityComponent("Gamma R2", gammaR2, false, true),
            QualityComponent("Norm Stability", normStabilityScore(cvIqrMax), true, cvIqrMax <= 0.5f),
            QualityComponent("Bootstrap Width", bootstrapWidthScore(bootstrapWidthMean), false, true),
            QualityComponent("Confidence Dist", confidenceAbove25Pct, true, confidenceAbove25Pct >= 0.5f),
            QualityComponent("Ref Freshness", referenceFreshness, false, true),
        )

        val allGates = components.filter { it.isGate }.all { it.passed }
        val overall = components.map { it.value }.average().toFloat()

        return SessionQualityResult(overall, components, allGates)
    }

    private fun normStabilityScore(cvIqr: Float): Float = when {
        cvIqr <= 0.25f -> 1.0f
        cvIqr <= 0.35f -> 0.6f
        cvIqr <= 0.5f -> 0.3f
        else -> 0.0f
    }

    private fun bootstrapWidthScore(meanWidth: Float): Float =
        (1.0f - meanWidth * 2).coerceIn(0f, 1f)
}
