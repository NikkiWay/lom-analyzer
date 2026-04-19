package com.example.lomanalyzer.preprocessing

import com.example.lomanalyzer.nlp.NlpService
import org.tartarus.snowball.ext.RussianStemmer

data class LemmaResult(
    val position: Int,
    val original: String,
    val lemma: String,
)

class LemmatizerProxy(
    private val nlpService: NlpService? = null,
) {
    private val stemmer = RussianStemmer()

    suspend fun lemmatize(tokens: List<String>): List<LemmaResult> {
        if (nlpService != null) {
            val text = tokens.joinToString(" ")
            val result = nlpService.lemmatize(text)
            return result.lemmas.mapIndexed { i, lemma ->
                LemmaResult(i, tokens.getOrElse(i) { lemma }, lemma)
            }
        }
        return stemFallback(tokens)
    }

    fun stemFallback(tokens: List<String>): List<LemmaResult> =
        tokens.mapIndexed { index, token ->
            val stem = if (token.isNotEmpty() && token.first().isLetter()) {
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
