package com.example.lomanalyzer.analysis.content

data class NegationAdjusted(
    val positiveCount: Int,
    val negativeCount: Int,
    val negationApplied: Boolean,
)

/**
 * Negation inversion in 1-3 token window.
 * Negators: не, ни, нет, без, нельзя, никогда.
 */
class NegationHandler(
    val windowSize: Int = 3,
    val negators: Set<String> = DEFAULT_NEGATORS,
) {
    companion object {
        val DEFAULT_NEGATORS = setOf("не", "ни", "нет", "без", "нельзя", "никогда")
        val EXTENDED_NEGATORS = DEFAULT_NEGATORS + setOf("едва", "вряд", "невозможно")
    }

    fun applyNegation(
        lemmas: List<String>,
        posSet: Set<String>,
        negSet: Set<String>,
    ): NegationAdjusted {
        var posCount = 0
        var negCount = 0
        var negApplied = false

        val negatedPositions = mutableSetOf<Int>()

        // Mark positions within window after a negator
        for ((i, lemma) in lemmas.withIndex()) {
            if (lemma.lowercase() in negators) {
                for (j in (i + 1)..minOf(i + windowSize, lemmas.size - 1)) {
                    negatedPositions.add(j)
                }
            }
        }

        for ((i, lemma) in lemmas.withIndex()) {
            val lower = lemma.lowercase()
            val isNegated = i in negatedPositions

            when {
                lower in posSet -> {
                    if (isNegated) { negCount++; negApplied = true }
                    else posCount++
                }
                lower in negSet -> {
                    if (isNegated) { posCount++; negApplied = true }
                    else negCount++
                }
            }
        }

        return NegationAdjusted(posCount, negCount, negApplied)
    }
}
