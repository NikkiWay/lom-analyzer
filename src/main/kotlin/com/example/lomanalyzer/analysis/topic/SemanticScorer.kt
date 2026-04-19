package com.example.lomanalyzer.analysis.topic

import com.example.lomanalyzer.nlp.NlpService

class SemanticScorer(
    private val nlpService: NlpService,
) {
    private var referenceEmbedding: List<Float>? = null

    suspend fun initializeReference(referenceTexts: List<String>) {
        if (referenceTexts.isEmpty()) return
        val embeddings = referenceTexts.map { nlpService.embed(it).vector }
        referenceEmbedding = averageVectors(embeddings)
    }

    @Suppress("ReturnCount")
    suspend fun score(text: String): Float {
        val ref = referenceEmbedding ?: return 0f
        if (ref.isEmpty()) return 0f
        val postEmb = nlpService.embed(text).vector
        if (postEmb.isEmpty()) return 0f
        return cosineSimilarity(postEmb, ref).coerceIn(0f, 1f)
    }

    fun isInitialized(): Boolean = referenceEmbedding != null

    private fun averageVectors(vectors: List<List<Float>>): List<Float> {
        if (vectors.isEmpty()) return emptyList()
        val dim = vectors[0].size
        val avg = FloatArray(dim)
        for (v in vectors) {
            for (i in 0 until dim) avg[i] += v[i]
        }
        val n = vectors.size.toFloat()
        return avg.map { it / n }
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())
        return if (denom > 0) (dot / denom).toFloat() else 0f
    }
}
