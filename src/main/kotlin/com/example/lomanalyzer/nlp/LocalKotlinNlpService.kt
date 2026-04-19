package com.example.lomanalyzer.nlp

import com.example.lomanalyzer.preprocessing.LanguageDetectorProxy
import com.example.lomanalyzer.preprocessing.LemmatizerProxy
import com.example.lomanalyzer.preprocessing.Tokenizer

class LocalKotlinNlpService(
    private val lemmatizer: LemmatizerProxy,
    private val languageDetector: LanguageDetectorProxy,
) : NlpService {

    override suspend fun lemmatize(text: String): LemmatizeResult {
        val tokens = Tokenizer.tokenize(text)
        val results = lemmatizer.stemFallback(tokens)
        return LemmatizeResult(results.map { it.lemma })
    }

    override suspend fun detectLanguage(text: String): LanguageDetectResult {
        val tokens = Tokenizer.tokenize(text)
        val result = languageDetector.detectFallback(tokens)
        return LanguageDetectResult(result.language, result.confidence)
    }

    override suspend fun scoreSentiment(text: String, mode: String): SentimentScore {
        // Dictionary-based stub — will be completed in Prompt 14
        return SentimentScore(label = "NEUTRAL", score = 0f, method = "DICTIONARY_STUB")
    }

    override suspend fun semanticSimilarity(a: String, b: String): SimilarityResult {
        // Not available in FALLBACK mode
        return SimilarityResult(similarity = 0f)
    }

    override suspend fun embed(text: String): EmbeddingResult {
        // Not available in FALLBACK mode
        return EmbeddingResult(vector = emptyList())
    }

    override suspend fun extractEntities(text: String): List<NerEntity> {
        // NER not available in FALLBACK mode
        return emptyList()
    }
}
