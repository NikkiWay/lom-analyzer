/*
 * НАЗНАЧЕНИЕ
 * Второй проход (L2) тематической фильтрации (этап 6, docs/algorithm.md):
 * семантическая оценка близости поста к теме через эмбеддинги RuBERT
 * (rubert-tiny2). Применяется только к пограничным постам, не прошедшим уверенно
 * по ключевым словам в проходе 1.
 *
 * ЧТО ВНУТРИ
 * class SemanticScorer: построение эталонного эмбеддинга темы по reference-текстам
 * (initializeReference), оценка cosine similarity поста с эталоном (score),
 * усреднение векторов (averageVectors) и расчёт cosine similarity (cosineSimilarity).
 *
 * МЕТОД
 * Эталон темы = усреднённый вектор эмбеддингов нескольких reference-текстов.
 * Близость поста = cosine similarity между эмбеддингом поста и эталоном,
 * обрезанная в [0..1]. Порог принятия (0.55) применяется в TopicRelevanceFilter.
 *
 * ФРЕЙМВОРКИ / БИБЛИОТЕКИ
 * - NlpService — обращение к Python FastAPI sidecar (rubert-tiny2) за эмбеддингами.
 * - kotlinx.coroutines.withTimeoutOrNull — защита от «зависания» sidecar:
 *   на каждый вызов embed отведено 10 секунд. При таймауте/ошибке проход 2
 *   полностью отключается (флаг disabled) для всех оставшихся постов, чтобы не
 *   тормозить пайплайн.
 *
 * СВЯЗИ
 * Создаётся и инициализируется в TopicFilterExecutor, вызывается из
 * TopicRelevanceFilter для пограничных постов.
 */
package com.example.lomanalyzer.analysis.topic

import com.example.lomanalyzer.nlp.NlpService
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Оценщик семантической близости поста к теме на эмбеддингах RuBERT.
 * @param nlpService клиент NLP-сервиса (sidecar), выдающий эмбеддинги.
 */
class SemanticScorer(
    private val nlpService: NlpService,
) {
    /** Усреднённый эталонный эмбеддинг темы; null, пока не инициализирован. */
    private var referenceEmbedding: List<Float>? = null
    /** Set to true after first timeout/error — disables all further embed calls.
     *  Выставляется в true после первого таймаута/ошибки — отключает проход 2. */
    private var disabled = false

    /**
     * Строит эталонный эмбеддинг темы по набору reference-текстов.
     * Каждый текст превращается в вектор через sidecar (с таймаутом 10 с),
     * затем векторы усредняются. При любом таймауте/пустом векторе/исключении
     * проход 2 отключается (disabled=true).
     * @param referenceTexts эталонные тексты, описывающие тему.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun initializeReference(referenceTexts: List<String>) {
        // Без эталонных текстов инициализировать нечего
        if (referenceTexts.isEmpty()) return
        try {
            // Получаем эмбеддинг каждого reference-текста; таймаут -> пустой вектор
            val embeddings = referenceTexts.map {
                withTimeoutOrNull(10_000L) { nlpService.embed(it).vector } ?: emptyList()
            }
            // Если хотя бы один эмбеддинг не получен (sidecar недоступен/медленный) —
            // отключаем проход 2 целиком, чтобы не получить искажённый эталон
            if (embeddings.any { it.isEmpty() }) {
                disabled = true
                referenceEmbedding = null
                return
            }
            // Эталон темы = покомпонентное среднее всех reference-векторов
            referenceEmbedding = averageVectors(embeddings)
        } catch (e: Exception) {
            // Любая ошибка sidecar — безопасно отключаем семантический проход
            disabled = true
            referenceEmbedding = null
        }
    }

    /**
     * Оценивает cosine similarity поста с эталоном темы.
     * @param text исходный текст поста.
     * @return L2 в [0..1]; 0f, если проход 2 отключён или эмбеддинг недоступен.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    suspend fun score(text: String): Float {
        // Проход 2 ранее отключён -> сразу 0
        if (disabled) return 0f
        // Нет эталона -> оценивать не с чем
        val ref = referenceEmbedding ?: return 0f
        if (ref.isEmpty()) return 0f
        return try {
            // Эмбеддинг поста с таймаутом 10 с
            val postEmb = withTimeoutOrNull(10_000L) { nlpService.embed(text).vector }
            if (postEmb == null) {
                // Sidecar too slow — disable Pass 2 for all remaining posts
                // Sidecar слишком медленный — отключаем проход 2 для всех остальных постов
                disabled = true
                return 0f
            }
            if (postEmb.isEmpty()) return 0f
            // cosine similarity поста с эталоном, ограниченная диапазоном [0..1]
            cosineSimilarity(postEmb, ref).coerceIn(0f, 1f)
        } catch (e: Exception) {
            // Ошибка sidecar -> отключаем проход 2 и возвращаем 0
            disabled = true
            0f
        }
    }

    /** @return true, если эталонный эмбеддинг успешно построен (проход 2 готов). */
    fun isInitialized(): Boolean = referenceEmbedding != null

    /**
     * Покомпонентное усреднение списка векторов одинаковой размерности.
     * @return усреднённый вектор (центроид) либо пустой список.
     */
    private fun averageVectors(vectors: List<List<Float>>): List<Float> {
        if (vectors.isEmpty()) return emptyList()
        // Размерность берём по первому вектору
        val dim = vectors[0].size
        val avg = FloatArray(dim)
        // Суммируем покомпонентно все векторы
        for (v in vectors) {
            for (i in 0 until dim) avg[i] += v[i]
        }
        // Делим на число векторов -> среднее
        val n = vectors.size.toFloat()
        return avg.map { it / n }
    }

    /**
     * Косинусная близость (cosine similarity) двух векторов:
     * dot(a,b) / (||a|| * ||b||).
     * @return значение близости; 0f, если один из векторов нулевой.
     */
    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        // Одновременно накапливаем скалярное произведение и квадраты норм
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        // Знаменатель = произведение евклидовых норм векторов
        val denom = Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())
        return if (denom > 0) (dot / denom).toFloat() else 0f
    }
}
