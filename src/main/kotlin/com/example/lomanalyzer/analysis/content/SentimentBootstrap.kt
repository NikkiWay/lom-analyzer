package com.example.lomanalyzer.analysis.content

/**
 * MVP simplified 10-variant bootstrap:
 * 3 negation windows × 3 thresholds + 1 extended negator variant = 10.
 * Full 20-variant version deferred to Prompt 19.
 */
object SentimentBootstrap {
    private val WINDOWS = listOf(1, 2, 3)
    private val THRESHOLDS = listOf(0.10f, 0.15f, 0.20f)

    data class BootstrapSentimentResult(
        val primaryLabel: String,
        val agreement: Float,
        val variantLabels: List<String>,
    )

    fun bootstrap(lemmas: List<String>): BootstrapSentimentResult {
        val dict = DictionarySentiment()
        val labels = mutableListOf<String>()

        // 9 variants: window × threshold
        for (window in WINDOWS) {
            for (threshold in THRESHOLDS) {
                val handler = NegationHandler(windowSize = window)
                val result = dict.score(lemmas, handler)
                val label = categorize(result.score, threshold)
                labels.add(label)
            }
        }

        // 1 variant with extended negators, default threshold
        val extHandler = NegationHandler(
            windowSize = 2,
            negators = NegationHandler.EXTENDED_NEGATORS,
        )
        val extResult = dict.score(lemmas, extHandler)
        labels.add(categorize(extResult.score, 0.15f))

        val primary = dict.score(lemmas).label
        val agreement = labels.count { it == primary }.toFloat() / labels.size

        return BootstrapSentimentResult(primary, agreement, labels)
    }

    private fun categorize(score: Float, threshold: Float): String = when {
        kotlin.math.abs(score) < threshold -> "NEUTRAL"
        score > 0 -> "POSITIVE"
        else -> "NEGATIVE"
    }
}
