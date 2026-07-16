/*
 * НАЗНАЧЕНИЕ
 * Публичный контракт (интерфейс) NLP-модуля — модуля 3 архитектуры
 * (диплом 2.2.3, 2.2.7). NLP работает в двух режимах: основной (Python
 * sidecar — FastAPI с dostoevsky/pymorphy3/natasha/rubert-tiny2) и резервный
 * fallback (чистый Kotlin). Контракт скрывает выбор режима от вызывающего кода.
 *
 * ЧТО ВНУТРИ
 * Интерфейс NlpServiceContract: батчевая лемматизация, батчевый анализ
 * тональности для постов и для комментариев, свойство текущего режима mode.
 *
 * МЕТОД
 * Все методы пакетные (batch), чтобы амортизировать накладные расходы IPC
 * (межпроцессного взаимодействия с Python sidecar) на один HTTP-вызов.
 *
 * СВЯЗИ
 * Реализации — в пакете nlp/ (например, NlpServiceSelector выбирает основной или
 * резервный режим). Все методы suspend (корутины), результаты пишутся в SQLite.
 */
package com.example.lomanalyzer.core

/**
 * Public contract for the NLP module (diploma 2.2.3, module 3; 2.2.7).
 * Two modes: primary (Python sidecar) and fallback (Kotlin).
 *
 * Публичный контракт NLP-модуля. Два режима: основной (Python sidecar) и
 * резервный (Kotlin).
 *
 * All methods are batch-oriented to amortize IPC overhead.
 */
interface NlpServiceContract {
    /**
     * Лемматизировать пакет текстов.
     *
     * Lemmatize a batch of texts
     *
     * @param texts входные тексты.
     * @return для каждого текста — список его лемм (в том же порядке).
     */
    suspend fun batchLemmatize(texts: List<String>): List<List<String>>

    /**
     * Тональность для пакета постов: по одному SentimentDistribution на текст.
     *
     * Sentiment for a batch of posts: returns SentimentDistribution per text
     *
     * @param texts тексты постов.
     * @return распределение тональности для каждого поста.
     */
    suspend fun batchSentimentForPosts(texts: List<String>): List<SentimentDistribution>

    /**
     * Тональность для пакета комментариев.
     *
     * Sentiment for a batch of comments
     *
     * @param texts тексты комментариев.
     * @return распределение тональности для каждого комментария.
     */
    suspend fun batchSentimentForComments(texts: List<String>): List<SentimentDistribution>

    /**
     * Текущий режим NLP: "FULL" (Python) или "FALLBACK_ONLY" (Kotlin).
     *
     * Current NLP mode: "FULL" (Python) or "FALLBACK_ONLY" (Kotlin)
     */
    val mode: String
}
