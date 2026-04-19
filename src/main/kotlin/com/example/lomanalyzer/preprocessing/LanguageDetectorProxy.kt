package com.example.lomanalyzer.preprocessing

data class LanguageResult(
    val language: String,
    val confidence: Float,
    val flag: String, // OK, LANGUAGE_UNCERTAIN, FILTERED_OUT_LANGUAGE
)

class LanguageDetectorProxy {
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

    @Suppress("ReturnCount")
    fun detectFallback(tokens: List<String>): LanguageResult {
        if (tokens.isEmpty()) {
            return LanguageResult("unknown", 0f, "FILTERED_OUT_LANGUAGE")
        }

        val wordTokens = tokens.filter { it.first().isLetter() }
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
