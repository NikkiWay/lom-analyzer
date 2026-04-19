package com.example.lomanalyzer.analysis.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SentimentResult(
    val label: String,
    val score: Float,
    val method: String,
    val negationApplied: Boolean,
)

@Serializable
data class SentilexData(
    val version: String = "",
    val positive: List<String> = emptyList(),
    val negative: List<String> = emptyList(),
)

class DictionarySentiment {
    companion object {
        private const val LOW_CONFIDENCE_THRESHOLD = 0.15f
    }

    private val sentilex: SentilexData by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val stream = DictionarySentiment::class.java
            .getResourceAsStream("/resources/sentilex_base.json")
        if (stream != null) {
            json.decodeFromString<SentilexData>(stream.bufferedReader().readText())
        } else {
            SentilexData()
        }
    }

    private val posSet by lazy { sentilex.positive.map { it.lowercase() }.toSet() }
    private val negSet by lazy { sentilex.negative.map { it.lowercase() }.toSet() }

    @Suppress("ReturnCount")
    fun score(
        lemmas: List<String>,
        negationHandler: NegationHandler = NegationHandler(),
    ): SentimentResult {
        if (lemmas.isEmpty()) {
            return SentimentResult("NEUTRAL", 0f, "NO_LEXICON_MATCH", false)
        }

        val adjusted = negationHandler.applyNegation(lemmas, posSet, negSet)
        val nPos = adjusted.positiveCount
        val nNeg = adjusted.negativeCount

        if (nPos == 0 && nNeg == 0) {
            return SentimentResult("NEUTRAL", 0f, "NO_LEXICON_MATCH", false)
        }

        val rawScore = (nPos - nNeg).toFloat() / (nPos + nNeg + 1)

        val label = when {
            kotlin.math.abs(rawScore) < LOW_CONFIDENCE_THRESHOLD -> "NEUTRAL"
            rawScore > 0 -> "POSITIVE"
            else -> "NEGATIVE"
        }

        val method = if (kotlin.math.abs(rawScore) < LOW_CONFIDENCE_THRESHOLD) {
            "LOW_CONFIDENCE"
        } else {
            "DICTIONARY"
        }

        return SentimentResult(label, rawScore, method, adjusted.negationApplied)
    }
}
