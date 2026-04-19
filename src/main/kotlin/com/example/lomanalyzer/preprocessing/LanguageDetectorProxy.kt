package com.example.lomanalyzer.preprocessing

import com.example.lomanalyzer.nlp.NlpService

data class LanguageResult(
    val language: String,
    val confidence: Float,
    val flag: String, // OK, LANGUAGE_UNCERTAIN, FILTERED_OUT_LANGUAGE
)

class LanguageDetectorProxy(
    private val nlpService: NlpService? = null,
) {
    companion object {
        private const val RU_THRESHOLD = 0.30f
    }

    private val frequentLemmas: Set<String> by lazy {
        val stream = LanguageDetectorProxy::class.java
            .getResourceAsStream("/lang_heuristic/ru_frequent_lemmas.txt")
        stream?.bufferedReader()?.readLines()
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun detect(text: String, tokens: List<String>): LanguageResult {
        if (nlpService != null) {
            val result = nlpService.detectLanguage(text)
            val flag = when {
                result.language == "ru" && result.confidence >= 0.5f -> "OK"
                result.language == "ru" -> "LANGUAGE_UNCERTAIN"
                else -> "FILTERED_OUT_LANGUAGE"
            }
            return LanguageResult(result.language, result.confidence, flag)
        }
        return detectFallback(tokens)
    }

    @Suppress("ReturnCount")
    fun detectFallback(tokens: List<String>): LanguageResult {
        if (tokens.isEmpty()) {
            return LanguageResult("unknown", 0f, "FILTERED_OUT_LANGUAGE")
        }

        val wordTokens = tokens.filter { it.isNotEmpty() && it.first().isLetter() }
        if (wordTokens.isEmpty()) {
            return LanguageResult("unknown", 0f, "FILTERED_OUT_LANGUAGE")
        }

        val matchCount = wordTokens.count { it.lowercase() in frequentLemmas }
        val fraction = matchCount.toFloat() / wordTokens.size

        return when {
            fraction >= 0.5f -> LanguageResult("ru", fraction, "OK")
            fraction >= RU_THRESHOLD -> LanguageResult("ru", fraction, "LANGUAGE_UNCERTAIN")
            else -> LanguageResult("unknown", fraction, "FILTERED_OUT_LANGUAGE")
        }
    }
}
