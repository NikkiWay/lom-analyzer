package com.example.lomanalyzer.preprocessing

import org.apache.lucene.analysis.snowball.SnowballFilter
import org.tartarus.snowball.ext.RussianStemmer

data class LemmaResult(
    val position: Int,
    val original: String,
    val lemma: String,
)

class LemmatizerProxy {
    private val stemmer = RussianStemmer()

    fun stemFallback(tokens: List<String>): List<LemmaResult> =
        tokens.mapIndexed { index, token ->
            val stem = if (token.first().isLetter()) {
                stem(token)
            } else {
                token
            }
            LemmaResult(position = index, original = token, lemma = stem)
        }

    private fun stem(word: String): String {
        synchronized(stemmer) {
            stemmer.current = word.lowercase()
            stemmer.stem()
            return stemmer.current
        }
    }
}
