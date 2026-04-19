package com.example.lomanalyzer.analysis.lom

/**
 * O_raw_a = (sum w_i + 0.5) / (N_topic_eff_a + 1)
 * Uses originalityWeight from OriginalityClassifier.
 */
object ContentOriginalityComponent {
    fun computeRaw(originalityWeights: List<Float>, topicEffCount: Int): Double {
        val sumW = originalityWeights.sumOf { it.toDouble() }
        return (sumW + 0.5) / (topicEffCount + 1)
    }
}
