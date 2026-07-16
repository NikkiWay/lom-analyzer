/*
 * НАЗНАЧЕНИЕ
 * Юнит-тесты контент-анализа (этапы обработки/оценок): словарный сентимент
 * с учётом отрицаний, медианная агрегация тональности и извлечение ключевых
 * терминов по TF-IDF.
 *
 * ЧТО ВНУТРИ
 * Класс ContentAnalysisTest c группами тестов:
 *   1) DictionarySentiment.score — определение метки POSITIVE/NEGATIVE/NEUTRAL
 *      по лексикону, метод NO_LEXICON_MATCH/LOW_CONFIDENCE, флаг применения отрицания;
 *   2) NegationHandler.applyNegation — инверсия тональности слов в окне после
 *      слова-отрицания, влияние размера окна (windowSize);
 *   3) ручная проверка свойств медианы (как робастной агрегатной меры тональности);
 *   4) TermExtractor.extractTopTerms — топ-N терминов по TF-IDF.
 *
 * МЕТОД
 * Сентимент — словарный с нормализацией score в [−1,1] и порогами уверенности.
 * Отрицание инвертирует знак тональности слов в окне фиксированной ширины.
 * Медиана — устойчивая к выбросам мера центральной тенденции тональности.
 * TF-IDF выделяет термины, частые у автора и редкие по корпусу сессии.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@Test, статические assert-методы).
 *
 * СВЯЗИ
 * Тестируемые типы из пакета analysis/content: DictionarySentiment,
 * NegationHandler, TermExtractor.
 */
package com.example.lomanalyzer.analysis.content

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Тесты словарного сентимента, обработки отрицаний, медианы и TF-IDF-извлечения терминов.
 */
class ContentAnalysisTest {

    // --- DictionarySentiment ---

    /**
     * Несколько позитивных лемм должны дать метку POSITIVE и положительный score.
     * Arrange: три позитивных слова + одно нейтральное. Assert: label=POSITIVE, score>0.
     */
    @Test
    fun `positive lemmas produce POSITIVE label`() {
        val dict = DictionarySentiment()
        // Act: словарная оценка набора лемм с преобладанием позитива
        val result = dict.score(listOf("хороший", "отличный", "замечательный", "день"))
        assertEquals("POSITIVE", result.label)
        assertTrue(result.score > 0)
    }

    /**
     * Несколько негативных лемм должны дать метку NEGATIVE и отрицательный score.
     */
    @Test
    fun `negative lemmas produce NEGATIVE label`() {
        val dict = DictionarySentiment()
        // Act: преобладание негативных слов
        val result = dict.score(listOf("ужасный", "кризис", "катастрофа", "день"))
        assertEquals("NEGATIVE", result.label)
        assertTrue(result.score < 0)
    }

    /**
     * Если ни одно слово не найдено в лексиконе, результат NEUTRAL,
     * а метод явно помечается как NO_LEXICON_MATCH (нет основания для оценки).
     */
    @Test
    fun `no lexicon match produces NEUTRAL with NO_LEXICON_MATCH`() {
        val dict = DictionarySentiment()
        // Act: слова без эмоциональной окраски (нет совпадений в словаре)
        val result = dict.score(listOf("стол", "стул", "окно"))
        assertEquals("NEUTRAL", result.label)
        assertEquals("NO_LEXICON_MATCH", result.method)
    }

    /**
     * Граничный случай: пустой ввод → NEUTRAL (оценивать нечего).
     */
    @Test
    fun `empty input produces NEUTRAL`() {
        val dict = DictionarySentiment()
        // Act
        val result = dict.score(emptyList())
        assertEquals("NEUTRAL", result.label)
    }

    /**
     * Смешанный сентимент с близким к нулю итоговым score трактуется как NEUTRAL
     * (низкая уверенность). Один позитив и один негатив компенсируют друг друга:
     * score = 0/(0+0+1) = 0 → LOW_CONFIDENCE → метка NEUTRAL.
     */
    @Test
    fun `mixed sentiment with low score produces LOW_CONFIDENCE`() {
        val dict = DictionarySentiment()
        // 1 pos + 1 neg → score = 0/(0+0+1) = 0 → LOW_CONFIDENCE
        val result = dict.score(listOf("хороший", "плохой"))
        assertEquals("NEUTRAL", result.label)
    }

    // --- NegationHandler ---

    /**
     * Отрицание перед позитивным словом инвертирует его тональность в негатив.
     * Arrange: «не хороший день». Assert: флаг negationApplied=true и итоговый score≤0.
     */
    @Test
    fun `negation inverts positive to negative`() {
        val dict = DictionarySentiment()
        // "не хороший" → negation inverts positive → counts as negative
        val result = dict.score(listOf("не", "хороший", "день"))
        assertTrue(result.negationApplied)
        // Should count "хороший" as negative due to negation
        assertTrue(result.score <= 0, "score=${result.score}")
    }

    /**
     * Симметричный случай: отрицание перед негативным словом инвертирует его в позитив.
     * Arrange: «не плохой день». Assert: negationApplied=true и итоговый score≥0.
     */
    @Test
    fun `negation inverts negative to positive`() {
        val dict = DictionarySentiment()
        // "не плохой" → inverts negative → counts as positive
        val result = dict.score(listOf("не", "плохой", "день"))
        assertTrue(result.negationApplied)
        assertTrue(result.score >= 0, "score=${result.score}")
    }

    /**
     * Проверяет, что ширина окна отрицания (windowSize) корректно ограничивает зону
     * действия слова-отрицания «не».
     * Arrange: леммы «не»(0) «день»(1) «хороший»(2); словарь позитива = {хороший}.
     * При окне 1 инверсия достаёт только позицию 1, «хороший» на позиции 2 остаётся
     * позитивным → positiveCount=1. При окне 3 инверсия покрывает позицию 2,
     * «хороший» инвертируется → positiveCount=0, negativeCount=1.
     */
    @Test
    fun `negation window size is respected`() {
        // Arrange: два обработчика с разной шириной окна отрицания
        val handler1 = NegationHandler(windowSize = 1)
        val handler3 = NegationHandler(windowSize = 3)
        val posSet = setOf("хороший")
        val negSet = emptySet<String>()

        // "не" at 0, "день" at 1, "хороший" at 2
        val lemmas = listOf("не", "день", "хороший")

        // Act: узкое окно — «хороший» вне зоны действия отрицания
        val adj1 = handler1.applyNegation(lemmas, posSet, negSet)
        // Window=1: only position 1 is negated, "хороший" at 2 is not
        assertEquals(1, adj1.positiveCount)

        // Act: широкое окно — «хороший» попадает под отрицание и инвертируется
        val adj3 = handler3.applyNegation(lemmas, posSet, negSet)
        // Window=3: positions 1,2,3 are negated, "хороший" at 2 IS negated
        assertEquals(0, adj3.positiveCount)
        assertEquals(1, adj3.negativeCount) // inverted
    }

    // --- Median aggregation ---

    /**
     * Проверяет медиану для нечётного числа оценок: это средний элемент
     * отсортированного ряда. Для 5 значений медиана — элемент с индексом 2 (0.3).
     */
    @Test
    fun `median of odd count`() {
        val scores = listOf(-0.5f, 0.0f, 0.3f, 0.7f, 0.8f)
        // Сортируем и берём центральный элемент
        val sorted = scores.sorted()
        val median = sorted[sorted.size / 2]
        assertEquals(0.3f, median)
    }

    /**
     * Проверяет медиану для чётного числа оценок: среднее двух центральных
     * элементов. Для [-0.5, 0.0, 0.3, 0.8] это (0.0+0.3)/2 = 0.15.
     */
    @Test
    fun `median of even count`() {
        val scores = listOf(-0.5f, 0.0f, 0.3f, 0.8f)
        val sorted = scores.sorted()
        val mid = sorted.size / 2
        // Среднее двух центральных значений
        val median = (sorted[mid - 1] + sorted[mid]) / 2f
        assertEquals(0.15f, median, 0.001f)
    }

    /**
     * Демонстрирует устойчивость медианы как меры центральной тенденции тональности:
     * при преобладании негатива медиана остаётся отрицательной несмотря на один позитив.
     */
    @Test
    fun `median of mixed tones reflects central tendency`() {
        // Many negative + few positive → median should be negative
        val scores = listOf(-0.8f, -0.6f, -0.4f, -0.3f, 0.5f)
        val sorted = scores.sorted()
        val median = sorted[sorted.size / 2]
        assertTrue(median < 0, "median=$median")
    }

    // --- TermExtractor ---

    /**
     * Проверяет извлечение топ-10 терминов по TF-IDF.
     * Arrange: 3 документа автора об экологии (загрязнение/экология повторяются)
     * и сессионный корпус из 6 документов (часть — на другие темы).
     * Ожидание: «загрязнение» попадает в топ (часто у автора, нечасто по корпусу),
     * выдаётся не более 10 терминов, и они отсортированы по убыванию TF-IDF.
     */
    @Test
    fun `TermExtractor returns top-10 by TF-IDF`() {
        val extractor = TermExtractor()

        // Author's docs
        val authorDocs = listOf(
            listOf("экология", "загрязнение", "воздух", "город"),
            listOf("экология", "вода", "загрязнение", "река"),
            listOf("экология", "проблема", "загрязнение", "решение"),
        )

        // Session-wide docs (author + others)
        val allDocs = authorDocs + listOf(
            listOf("политика", "выборы", "кандидат", "партия"),
            listOf("спорт", "футбол", "команда", "победа"),
            listOf("экология", "лес", "пожар", "защита"),
        )

        // Act: извлекаем топ-10 терминов автора относительно корпуса сессии
        val terms = extractor.extractTopTerms(authorDocs, allDocs, topN = 10)
        assertTrue(terms.isNotEmpty(), "Should extract some terms")
        assertTrue(terms.size <= 10, "At most 10 terms")

        // "загрязнение" appears in 3 author docs but only 3/6 session docs → high TF-IDF
        val termNames = terms.map { it.term }
        assertTrue("загрязнение" in termNames, "загрязнение should be top term")

        // Assert: термины отсортированы по убыванию TF-IDF (самые характерные — сверху)
        for (i in 0 until terms.size - 1) {
            assertTrue(terms[i].tfidf >= terms[i + 1].tfidf)
        }
    }

    /**
     * Граничный случай: пустой ввод (нет документов) → пустой список терминов.
     */
    @Test
    fun `TermExtractor handles empty input`() {
        val extractor = TermExtractor()
        val terms = extractor.extractTopTerms(emptyList(), emptyList())
        assertTrue(terms.isEmpty())
    }

}
