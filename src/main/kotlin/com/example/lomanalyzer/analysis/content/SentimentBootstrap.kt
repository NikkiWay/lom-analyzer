package com.example.lomanalyzer.analysis.content

/**
 * Full 20-variant sentiment bootstrap per v6 §12.3.
 *
 * FALLBACK: 3 negator sets × 3 windows × 3 thresholds = 27 combos → pick 20 representative.
 * FULL: 5 dostoevsky seeds × 15 post-processing variants = 20 combos.
 * (FULL mode is dispatched through NlpService; this class handles FALLBACK.)
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
        val labels = generateVariants(lemmas, dict)

        val primary = dict.score(lemmas).label
        val agreement = labels.count { it == primary }.toFloat() / labels.size

        return BootstrapSentimentResult(primary, agreement, labels)
    }

    private fun generateVariants(lemmas: List<String>, dict: DictionarySentiment): List<String> {
        val negatorSets = listOf(
            NegationHandler.DEFAULT_NEGATORS,
            NegationHandler.EXTENDED_NEGATORS,
            NegationHandler.DEFAULT_NEGATORS - setOf("без"),
        )

        val combos = negatorSets.flatMap { negators ->
            WINDOWS.flatMap { window ->
                THRESHOLDS.map { threshold ->
                    Triple(negators, window, threshold)
                }
            }
        }.take(20)

        return combos.map { (negators, window, threshold) ->
            val handler = NegationHandler(window, negators)
            categorize(dict.score(lemmas, handler).score, threshold)
        }
    }

    private fun categorize(score: Float, threshold: Float): String = when {
        kotlin.math.abs(score) < threshold -> "NEUTRAL"
        score > 0 -> "POSITIVE"
        else -> "NEGATIVE"
    }
}
