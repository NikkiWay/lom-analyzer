/*
 * НАЗНАЧЕНИЕ
 * Kotlin-реализация NLP-модуля — fallback-режим (диплом 2.2.7, architecture.md),
 * работающий без Python sidecar и ML-моделей. Используется, когда sidecar недоступен.
 *
 * ЧТО ВНУТРИ
 *  LocalKotlinNlpService — реализует NlpService на базе локальных Kotlin-компонентов:
 *  Tokenizer (токенизация), LemmatizerProxy (Snowball-стемминг), LanguageDetectorProxy
 *  (эвристика языка), DictionarySentiment (словарный sentiment).
 *
 * ОГРАНИЧЕНИЯ
 *  Семантическая близость, embeddings и NER в fallback-режиме недоступны и возвращают
 *  пустые/нулевые значения (для них нужны ML-модели из Python sidecar).
 *
 * СВЯЗИ
 *  Выбирается NlpServiceSelector как раз при отсутствии sidecar.
 */
package com.example.lomanalyzer.nlp

import com.example.lomanalyzer.analysis.content.DictionarySentiment
import com.example.lomanalyzer.preprocessing.LanguageDetectorProxy
import com.example.lomanalyzer.preprocessing.LemmatizerProxy
import com.example.lomanalyzer.preprocessing.Tokenizer

/**
 * Локальная Kotlin-реализация NlpService (fallback без ML-моделей).
 *
 * @param lemmatizer лемматизатор (используется Snowball-стемминг).
 * @param languageDetector детектор языка (эвристика).
 * @param dictionarySentiment словарный анализатор тональности.
 */
class LocalKotlinNlpService(
    private val lemmatizer: LemmatizerProxy,
    private val languageDetector: LanguageDetectorProxy,
    private val dictionarySentiment: DictionarySentiment = DictionarySentiment(),
) : NlpService {

    /** Лемматизация = токенизация + Snowball-стемминг. */
    override suspend fun lemmatize(text: String): LemmatizeResult {
        val tokens = Tokenizer.tokenize(text)
        val results = lemmatizer.stemFallback(tokens)
        return LemmatizeResult(results.map { it.lemma })
    }

    /** Определение языка по эвристике частотных русских лемм. */
    override suspend fun detectLanguage(text: String): LanguageDetectResult {
        val tokens = Tokenizer.tokenize(text)
        val result = languageDetector.detectFallback(tokens)
        return LanguageDetectResult(result.language, result.confidence)
    }

    /** Тональность по словарю: токенизация -> стемминг -> словарный score. */
    override suspend fun scoreSentiment(text: String, mode: String): SentimentScore {
        val tokens = Tokenizer.tokenize(text)
        val lemmas = lemmatizer.stemFallback(tokens).map { it.lemma }
        val result = dictionarySentiment.score(lemmas)
        return SentimentScore(label = result.label, score = result.score, method = "DICTIONARY")
    }

    override suspend fun semanticSimilarity(a: String, b: String): SimilarityResult {
        // Недоступно в режиме FALLBACK (требуются embeddings)
        return SimilarityResult(similarity = 0f)
    }

    override suspend fun embed(text: String): EmbeddingResult {
        // Недоступно в режиме FALLBACK (требуется RuBERT)
        return EmbeddingResult(vector = emptyList())
    }

    override suspend fun extractEntities(text: String): List<NerEntity> {
        // NER недоступен в режиме FALLBACK (требуется natasha)
        return emptyList()
    }
}
