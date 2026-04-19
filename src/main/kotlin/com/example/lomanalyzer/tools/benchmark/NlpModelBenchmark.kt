package com.example.lomanalyzer.tools.benchmark

/**
 * NLP model benchmark tool per v6 §29.1.
 *
 * Benchmarks embedding models on test_corpus.json:
 * - rubert-tiny2 (current, 312M params, 29MB)
 * - rubert-tiny3 (if available)
 * - ruBERT-base-cased (768M params, 680MB)
 * - ru-e5-small (if available)
 *
 * Metrics:
 * - Embedding correlation with analyst-labeled topical relevance
 * - Inference latency (ms per text)
 * - Model size (MB)
 */

data class ModelSpec(
    val name: String,
    val huggingFaceId: String,
    val sizeMb: Int,
    val embeddingDim: Int,
)

data class BenchmarkResult(
    val modelName: String,
    val correlationWithRelevance: Double,
    val avgLatencyMs: Double,
    val sizeMb: Int,
    val recommendation: String,
)

object NlpModelBenchmark {
    val MODELS = listOf(
        ModelSpec("rubert-tiny2", "cointegrated/rubert-tiny2", 29, 312),
        ModelSpec("ruBERT-base", "DeepPavlov/rubert-base-cased", 680, 768),
    )

    /**
     * Evaluate a model's embedding quality on labeled test data.
     * Returns Pearson correlation between embedding similarity and relevance.
     */
    fun evaluateCorrelation(
        similarities: List<Double>,
        relevanceLabels: List<Boolean>,
    ): Double {
        if (similarities.size != relevanceLabels.size || similarities.isEmpty()) return 0.0
        val x = similarities
        val y = relevanceLabels.map { if (it) 1.0 else 0.0 }
        return pearsonCorrelation(x, y)
    }

    fun generateRecommendation(results: List<BenchmarkResult>): String {
        val best = results.maxByOrNull { it.correlationWithRelevance }
        val current = results.find { it.modelName == "rubert-tiny2" }

        if (best == null || current == null) return "Insufficient data"

        val improvement = best.correlationWithRelevance - current.correlationWithRelevance
        @Suppress("ImplicitDefaultLocale")
        return if (improvement > 0.05 && best.modelName != "rubert-tiny2") {
            "Recommend migrating to ${best.modelName}: " +
                "+${"%.1f".format(improvement * 100)}% correlation improvement. " +
                "Size: ${best.sizeMb}MB (vs ${current.sizeMb}MB)."
        } else {
            "Keep rubert-tiny2: correlation ${"%.3f".format(current.correlationWithRelevance)}, " +
                "size ${current.sizeMb}MB. No substantial improvement from alternatives."
        }
    }

    private fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        val n = x.size
        val xMean = x.average()
        val yMean = y.average()
        var cov = 0.0; var varX = 0.0; var varY = 0.0
        for (i in 0 until n) {
            val dx = x[i] - xMean; val dy = y[i] - yMean
            cov += dx * dy; varX += dx * dx; varY += dy * dy
        }
        val denom = kotlin.math.sqrt(varX * varY)
        return if (denom > 0) cov / denom else 0.0
    }
}
