/*
 * НАЗНАЧЕНИЕ
 * Инструмент сравнения (benchmark) NLP-моделей эмбеддингов для тематической
 * фильтрации: оценивает, насколько хорошо эмбеддинги модели согласуются с
 * размеченной экспертом тематической релевантностью, и даёт рекомендацию о
 * целесообразности смены текущей модели (см. v6 §29.1).
 *
 * ЧТО ВНУТРИ
 * data class ModelSpec — описание модели (имя, id в Hugging Face, размер, размер
 * эмбеддинга). data class BenchmarkResult — итог по модели (корреляция,
 * латентность, размер, рекомендация). object NlpModelBenchmark — список
 * сравниваемых моделей, расчёт корреляции Пирсона и формирование рекомендации.
 *
 * МЕТОД
 * Качество модели оценивается корреляцией Пирсона между сходством эмбеддингов и
 * бинарной меткой релевантности (1 — релевантно, 0 — нет). Рекомендация сменить
 * модель выдаётся, если лучшая модель опережает текущую (rubert-tiny2) по
 * корреляции более чем на 0.05.
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin (kotlin.math) — расчёты без внешних зависимостей.
 * Текущая базовая модель — rubert-tiny2; альтернатива — ruBERT-base-cased.
 */
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

/**
 * Описание сравниваемой модели эмбеддингов.
 *
 * @property name краткое имя модели.
 * @property huggingFaceId идентификатор модели в Hugging Face.
 * @property sizeMb размер модели на диске, МБ.
 * @property embeddingDim размерность вектора эмбеддинга.
 */
data class ModelSpec(
    val name: String,
    val huggingFaceId: String,
    val sizeMb: Int,
    val embeddingDim: Int,
)

/**
 * Результат бенчмарка одной модели.
 *
 * @property correlationWithRelevance корреляция Пирсона эмбеддинг-сходства с релевантностью.
 * @property avgLatencyMs средняя латентность инференса, мс на текст.
 * @property sizeMb размер модели, МБ.
 * @property recommendation текстовая рекомендация по модели.
 */
data class BenchmarkResult(
    val modelName: String,
    val correlationWithRelevance: Double,
    val avgLatencyMs: Double,
    val sizeMb: Int,
    val recommendation: String,
)

/** Сравнение NLP-моделей эмбеддингов и выдача рекомендации. */
object NlpModelBenchmark {
    /** Список сравниваемых моделей: текущая rubert-tiny2 и альтернатива ruBERT-base. */
    val MODELS = listOf(
        ModelSpec("rubert-tiny2", "cointegrated/rubert-tiny2", 29, 312),
        ModelSpec("ruBERT-base", "DeepPavlov/rubert-base-cased", 680, 768),
    )

    /**
     * Оценивает качество эмбеддингов модели на размеченных данных.
     * @param similarities значения сходства эмбеддингов для пар текстов.
     * @param relevanceLabels эталонные метки релевантности (true/false), той же длины.
     * @return корреляция Пирсона между сходством и релевантностью (0.0 при несовпадении размеров/пустых данных).
     */
    fun evaluateCorrelation(
        similarities: List<Double>,
        relevanceLabels: List<Boolean>,
    ): Double {
        if (similarities.size != relevanceLabels.size || similarities.isEmpty()) return 0.0
        val x = similarities
        // Бинарные метки релевантности переводим в числа 1.0/0.0 для корреляции
        val y = relevanceLabels.map { if (it) 1.0 else 0.0 }
        return pearsonCorrelation(x, y)
    }

    /**
     * Формирует рекомендацию: мигрировать на лучшую модель, если её корреляция
     * превосходит текущую (rubert-tiny2) более чем на 0.05; иначе — оставить текущую.
     */
    fun generateRecommendation(results: List<BenchmarkResult>): String {
        // Лучшая модель по корреляции и результат текущей модели
        val best = results.maxByOrNull { it.correlationWithRelevance }
        val current = results.find { it.modelName == "rubert-tiny2" }

        if (best == null || current == null) return "Insufficient data"

        // Прирост корреляции лучшей модели относительно текущей
        val improvement = best.correlationWithRelevance - current.correlationWithRelevance
        @Suppress("ImplicitDefaultLocale")
        // Рекомендуем смену только при заметном приросте (более 0.05) и иной модели
        return if (improvement > 0.05 && best.modelName != "rubert-tiny2") {
            "Recommend migrating to ${best.modelName}: " +
                "+${"%.1f".format(improvement * 100)}% correlation improvement. " +
                "Size: ${best.sizeMb}MB (vs ${current.sizeMb}MB)."
        } else {
            "Keep rubert-tiny2: correlation ${"%.3f".format(current.correlationWithRelevance)}, " +
                "size ${current.sizeMb}MB. No substantial improvement from alternatives."
        }
    }

    /**
     * Корреляция Пирсона двух числовых рядов равной длины.
     * @return значение в диапазоне [-1, 1]; 0.0 если знаменатель равен нулю.
     */
    private fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        val n = x.size
        // Средние значения рядов
        val xMean = x.average()
        val yMean = y.average()
        // Накапливаем ковариацию и дисперсии по отклонениям от средних
        var cov = 0.0; var varX = 0.0; var varY = 0.0
        for (i in 0 until n) {
            val dx = x[i] - xMean; val dy = y[i] - yMean
            cov += dx * dy; varX += dx * dx; varY += dy * dy
        }
        // Знаменатель — корень из произведения дисперсий; при нуле возвращаем 0.0
        val denom = kotlin.math.sqrt(varX * varY)
        return if (denom > 0) cov / denom else 0.0
    }
}
