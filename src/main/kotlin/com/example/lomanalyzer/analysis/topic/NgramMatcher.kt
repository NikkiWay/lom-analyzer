package com.example.lomanalyzer.analysis.topic

data class NgramMatchResult(
    val primaryHits: Int,
    val secondaryHits: Int,
    val excludedHit: Boolean,
)

class NgramMatcher(
    private val primaryNgrams: List<List<String>>,
    private val secondaryNgrams: List<List<String>>,
    private val excludedNgrams: List<List<String>>,
) {
    fun match(lemmas: List<String>): NgramMatchResult {
        val lowerLemmas = lemmas.map { it.lowercase() }

        // Excluded first
        if (excludedNgrams.any { containsNgram(lowerLemmas, it) }) {
            return NgramMatchResult(0, 0, excludedHit = true)
        }

        val primaryHits = primaryNgrams.count { containsNgram(lowerLemmas, it) }
        val secondaryHits = secondaryNgrams.count { containsNgram(lowerLemmas, it) }

        return NgramMatchResult(primaryHits, secondaryHits, excludedHit = false)
    }

    @Suppress("ReturnCount")
    private fun containsNgram(lemmas: List<String>, ngram: List<String>): Boolean {
        if (ngram.isEmpty()) return false
        if (ngram.size == 1) return ngram[0].lowercase() in lemmas
        val target = ngram.map { it.lowercase() }
        for (i in 0..lemmas.size - target.size) {
            if (lemmas.subList(i, i + target.size) == target) return true
        }
        return false
    }
}
