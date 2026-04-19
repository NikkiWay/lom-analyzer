package com.example.lomanalyzer.nlp

data class LemmatizeResult(val lemmas: List<String>)
data class LanguageDetectResult(val language: String, val confidence: Float)
data class SentimentScore(val label: String, val score: Float, val method: String)
data class SimilarityResult(val similarity: Float)
data class EmbeddingResult(val vector: List<Float>)
data class NerEntity(val text: String, val type: String, val start: Int, val end: Int)

interface NlpService {
    suspend fun lemmatize(text: String): LemmatizeResult
    suspend fun detectLanguage(text: String): LanguageDetectResult
    suspend fun scoreSentiment(text: String, mode: String = "dostoevsky"): SentimentScore
    suspend fun semanticSimilarity(a: String, b: String): SimilarityResult
    suspend fun embed(text: String): EmbeddingResult
    suspend fun extractEntities(text: String): List<NerEntity>
}
