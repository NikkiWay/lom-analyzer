/*
 * НАЗНАЧЕНИЕ
 * Извлечение ключевых терминов автора методом TF-IDF — формирует «тематический
 * профиль» автора (топ-N значимых лемм) по его тематическим публикациям.
 * Часть контент-анализа (пакет content, см. docs/architecture.md, NLP-модуль).
 *
 * ЧТО ВНУТРИ
 *  - TermScore — пара «термин + его вес TF-IDF»;
 *  - TermExtractor — класс с публичным extractTopTerms() и приватными computeTf()/computeIdf().
 *
 * МЕТОД
 *  TF-IDF: вес термина = TF (нормированная частота в текстах автора) × IDF
 *  (логарифм обратной документной частоты по всей коллекции сессии).
 *  TF нормируется на максимальную частоту: count / max(count).
 *  IDF = ln(N / df), где N — число документов, df — в скольких документах встречается термин.
 *
 * БИБЛИОТЕКИ
 *  kotlin.math.ln — натуральный логарифм для IDF.
 */
package com.example.lomanalyzer.analysis.content

import kotlin.math.ln

/**
 * Термин с его весом TF-IDF.
 *
 * @param term лемма (термин).
 * @param tfidf вес TF-IDF: чем выше, тем характернее термин для автора.
 */
data class TermScore(val term: String, val tfidf: Double)

/**
 * Извлекатель топ-10 терминов автора по TF-IDF на тематических публикациях.
 * IDF рассчитывается по всей коллекции документов сессии.
 */
class TermExtractor {
    /**
     * Возвращает топ-N терминов автора по убыванию TF-IDF.
     *
     * @param authorLemmas леммы документов конкретного автора (список текстов).
     * @param allDocLemmas леммы всех документов коллекции сессии (для расчёта IDF).
     * @param topN сколько верхних терминов вернуть (по умолчанию 10).
     */
    fun extractTopTerms(
        authorLemmas: List<List<String>>,
        allDocLemmas: List<List<String>>,
        topN: Int = 10,
    ): List<TermScore> {
        // Без данных автора или коллекции вернуть пусто
        if (authorLemmas.isEmpty() || allDocLemmas.isEmpty()) return emptyList()

        // IDF по всей коллекции; TF — по объединённым леммам автора
        val idf = computeIdf(allDocLemmas)
        val tf = computeTf(authorLemmas.flatten())

        // Перемножаем TF×IDF, сортируем по убыванию веса и берём верхние topN
        return tf.entries
            .map { (term, freq) ->
                TermScore(term, freq * (idf[term] ?: 0.0))
            }
            .sortedByDescending { it.tfidf }
            .take(topN)
    }

    /** Нормированная частота терминов (TF): частота слова делится на максимальную частоту. */
    private fun computeTf(tokens: List<String>): Map<String, Double> {
        if (tokens.isEmpty()) return emptyMap()
        // Подсчёт вхождений каждой леммы (в нижнем регистре)
        val counts = tokens.groupingBy { it.lowercase() }.eachCount()
        val max = counts.values.max().toDouble()
        // Нормализация на максимум — TF в диапазоне (0..1]
        return counts.mapValues { (_, count) -> count / max }
    }

    /** Обратная документная частота (IDF) = ln(N / df) по всем документам коллекции. */
    private fun computeIdf(allDocs: List<List<String>>): Map<String, Double> {
        val n = allDocs.size.toDouble()
        val docFreq = mutableMapOf<String, Int>()
        // df: в скольких документах встречается термин (каждый термин в документе считается один раз)
        for (doc in allDocs) {
            for (term in doc.map { it.lowercase() }.toSet()) {
                docFreq[term] = (docFreq[term] ?: 0) + 1
            }
        }
        // IDF: редкие термины (малое df) получают больший вес
        return docFreq.mapValues { (_, df) -> ln(n / df) }
    }
}
