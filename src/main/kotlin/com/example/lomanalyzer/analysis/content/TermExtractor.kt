package com.example.lomanalyzer.analysis.content

import kotlin.math.ln

data class TermScore(val term: String, val tfidf: Double)

/**
 * TF-IDF top-10 terms per author on topical posts.
 * IDF computed over the session collection.
 */
class TermExtractor {
    fun extractTopTerms(
        authorLemmas: List<List<String>>,
        allDocLemmas: List<List<String>>,
        topN: Int = 10,
    ): List<TermScore> {
        if (authorLemmas.isEmpty() || allDocLemmas.isEmpty()) return emptyList()

        val idf = computeIdf(allDocLemmas)
        val tf = computeTf(authorLemmas.flatten())

        return tf.entries
            .map { (term, freq) ->
                TermScore(term, freq * (idf[term] ?: 0.0))
            }
            .sortedByDescending { it.tfidf }
            .take(topN)
    }

    private fun computeTf(tokens: List<String>): Map<String, Double> {
        if (tokens.isEmpty()) return emptyMap()
        val counts = tokens.groupingBy { it.lowercase() }.eachCount()
        val max = counts.values.max().toDouble()
        return counts.mapValues { (_, count) -> count / max }
    }

    private fun computeIdf(allDocs: List<List<String>>): Map<String, Double> {
        val n = allDocs.size.toDouble()
        val docFreq = mutableMapOf<String, Int>()
        for (doc in allDocs) {
            for (term in doc.map { it.lowercase() }.toSet()) {
                docFreq[term] = (docFreq[term] ?: 0) + 1
            }
        }
        return docFreq.mapValues { (_, df) -> ln(n / df) }
    }
}
