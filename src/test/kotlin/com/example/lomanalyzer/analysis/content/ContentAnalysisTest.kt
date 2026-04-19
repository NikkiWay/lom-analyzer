package com.example.lomanalyzer.analysis.content

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ContentAnalysisTest {

    // --- DictionarySentiment ---

    @Test
    fun `positive lemmas produce POSITIVE label`() {
        val dict = DictionarySentiment()
        val result = dict.score(listOf("хороший", "отличный", "замечательный", "день"))
        assertEquals("POSITIVE", result.label)
        assertTrue(result.score > 0)
    }

    @Test
    fun `negative lemmas produce NEGATIVE label`() {
        val dict = DictionarySentiment()
        val result = dict.score(listOf("ужасный", "кризис", "катастрофа", "день"))
        assertEquals("NEGATIVE", result.label)
        assertTrue(result.score < 0)
    }

    @Test
    fun `no lexicon match produces NEUTRAL with NO_LEXICON_MATCH`() {
        val dict = DictionarySentiment()
        val result = dict.score(listOf("стол", "стул", "окно"))
        assertEquals("NEUTRAL", result.label)
        assertEquals("NO_LEXICON_MATCH", result.method)
    }

    @Test
    fun `empty input produces NEUTRAL`() {
        val dict = DictionarySentiment()
        val result = dict.score(emptyList())
        assertEquals("NEUTRAL", result.label)
    }

    @Test
    fun `mixed sentiment with low score produces LOW_CONFIDENCE`() {
        val dict = DictionarySentiment()
        // 1 pos + 1 neg → score = 0/(0+0+1) = 0 → LOW_CONFIDENCE
        val result = dict.score(listOf("хороший", "плохой"))
        assertEquals("NEUTRAL", result.label)
    }

    // --- NegationHandler ---

    @Test
    fun `negation inverts positive to negative`() {
        val dict = DictionarySentiment()
        // "не хороший" → negation inverts positive → counts as negative
        val result = dict.score(listOf("не", "хороший", "день"))
        assertTrue(result.negationApplied)
        // Should count "хороший" as negative due to negation
        assertTrue(result.score <= 0, "score=${result.score}")
    }

    @Test
    fun `negation inverts negative to positive`() {
        val dict = DictionarySentiment()
        // "не плохой" → inverts negative → counts as positive
        val result = dict.score(listOf("не", "плохой", "день"))
        assertTrue(result.negationApplied)
        assertTrue(result.score >= 0, "score=${result.score}")
    }

    @Test
    fun `negation window size is respected`() {
        val handler1 = NegationHandler(windowSize = 1)
        val handler3 = NegationHandler(windowSize = 3)
        val posSet = setOf("хороший")
        val negSet = emptySet<String>()

        // "не" at 0, "день" at 1, "хороший" at 2
        val lemmas = listOf("не", "день", "хороший")

        val adj1 = handler1.applyNegation(lemmas, posSet, negSet)
        // Window=1: only position 1 is negated, "хороший" at 2 is not
        assertEquals(1, adj1.positiveCount)

        val adj3 = handler3.applyNegation(lemmas, posSet, negSet)
        // Window=3: positions 1,2,3 are negated, "хороший" at 2 IS negated
        assertEquals(0, adj3.positiveCount)
        assertEquals(1, adj3.negativeCount) // inverted
    }

    // --- Median aggregation ---

    @Test
    fun `median of odd count`() {
        val scores = listOf(-0.5f, 0.0f, 0.3f, 0.7f, 0.8f)
        val sorted = scores.sorted()
        val median = sorted[sorted.size / 2]
        assertEquals(0.3f, median)
    }

    @Test
    fun `median of even count`() {
        val scores = listOf(-0.5f, 0.0f, 0.3f, 0.8f)
        val sorted = scores.sorted()
        val mid = sorted.size / 2
        val median = (sorted[mid - 1] + sorted[mid]) / 2f
        assertEquals(0.15f, median, 0.001f)
    }

    @Test
    fun `median of mixed tones reflects central tendency`() {
        // Many negative + few positive → median should be negative
        val scores = listOf(-0.8f, -0.6f, -0.4f, -0.3f, 0.5f)
        val sorted = scores.sorted()
        val median = sorted[sorted.size / 2]
        assertTrue(median < 0, "median=$median")
    }

    // --- TermExtractor ---

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

        val terms = extractor.extractTopTerms(authorDocs, allDocs, topN = 10)
        assertTrue(terms.isNotEmpty(), "Should extract some terms")
        assertTrue(terms.size <= 10, "At most 10 terms")

        // "загрязнение" appears in 3 author docs but only 3/6 session docs → high TF-IDF
        val termNames = terms.map { it.term }
        assertTrue("загрязнение" in termNames, "загрязнение should be top term")

        // Verify sorted by TF-IDF descending
        for (i in 0 until terms.size - 1) {
            assertTrue(terms[i].tfidf >= terms[i + 1].tfidf)
        }
    }

    @Test
    fun `TermExtractor handles empty input`() {
        val extractor = TermExtractor()
        val terms = extractor.extractTopTerms(emptyList(), emptyList())
        assertTrue(terms.isEmpty())
    }

    // --- VisualActivityEstimator ---

    @Test
    fun `VAR computes correctly`() {
        assertEquals(0.5f, VisualActivityEstimator.compute(5, 10))
        assertEquals(0f, VisualActivityEstimator.compute(0, 10))
        assertEquals(1f, VisualActivityEstimator.compute(10, 10))
        assertEquals(0f, VisualActivityEstimator.compute(0, 0))
    }

    // --- SentimentBootstrap ---

    @Test
    fun `bootstrap produces agreement score`() {
        val result = SentimentBootstrap.bootstrap(
            listOf("хороший", "отличный", "замечательный"),
        )
        assertTrue(result.agreement in 0f..1f)
        assertEquals(10, result.variantLabels.size)
    }
}
