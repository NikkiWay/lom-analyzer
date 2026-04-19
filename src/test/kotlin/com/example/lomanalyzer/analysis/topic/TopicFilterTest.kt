package com.example.lomanalyzer.analysis.topic

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TopicFilterTest {

    // --- TopicRelevanceFilter ---

    @Test
    fun `FALLBACK_ONLY mode uses only L1`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY", threshold = 0.33f)
        val match = NgramMatchResult(primaryHits = 2, secondaryHits = 1, excludedHit = false)
        val result = filter.score(match, "dummy text")

        // l1 = min(2 + 0.3*1, 3) / 3 = min(2.3, 3) / 3 = 0.7667
        assertTrue(result.l1 > 0.76f && result.l1 < 0.77f, "l1=${result.l1}")
        assertNull(result.l2)
        assertEquals(result.l1, result.combined)
        assertEquals("L1_ONLY", result.method)
        assertTrue(result.relevant) // 0.767 > 0.33
    }

    @Test
    fun `L1 formula clamps at 3 primary hits`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        val match = NgramMatchResult(primaryHits = 5, secondaryHits = 0, excludedHit = false)
        val result = filter.score(match, "")

        assertEquals(1.0f, result.combined) // min(5, 3)/3 = 1.0
    }

    @Test
    fun `excluded ngrams produce l1 = 0`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        val match = NgramMatchResult(primaryHits = 3, secondaryHits = 2, excludedHit = true)
        val result = filter.score(match, "")

        assertEquals(0f, result.l1)
        assertFalse(result.relevant)
    }

    @Test
    fun `no hits produce score 0`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        val match = NgramMatchResult(primaryHits = 0, secondaryHits = 0, excludedHit = false)
        val result = filter.score(match, "")

        assertEquals(0f, result.combined)
        assertFalse(result.relevant)
    }

    @Test
    fun `threshold boundary — just below is not relevant`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY", threshold = 0.34f)
        // l1 = min(1, 3)/3 = 0.333...
        val match = NgramMatchResult(primaryHits = 1, secondaryHits = 0, excludedHit = false)
        val result = filter.score(match, "")

        assertFalse(result.relevant, "score=${result.combined} should be < 0.34")
    }

    @Test
    fun `threshold boundary — at threshold is relevant`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY", threshold = 0.33f)
        val match = NgramMatchResult(primaryHits = 1, secondaryHits = 0, excludedHit = false)
        val result = filter.score(match, "")

        assertTrue(result.relevant, "score=${result.combined} should be >= 0.33")
    }

    // --- NgramMatcher ---

    @Test
    fun `NgramMatcher matches primary unigrams`() {
        val matcher = NgramMatcher(
            primaryNgrams = listOf(listOf("экология"), listOf("загрязнение")),
            secondaryNgrams = emptyList(),
            excludedNgrams = emptyList(),
        )
        val result = matcher.match(listOf("проблема", "экология", "города"))
        assertEquals(1, result.primaryHits)
        assertFalse(result.excludedHit)
    }

    @Test
    fun `NgramMatcher matches bigrams`() {
        val matcher = NgramMatcher(
            primaryNgrams = listOf(listOf("изменение", "климата")),
            secondaryNgrams = emptyList(),
            excludedNgrams = emptyList(),
        )
        val result = matcher.match(listOf("изменение", "климата", "опасно"))
        assertEquals(1, result.primaryHits)
    }

    @Test
    fun `NgramMatcher excluded blocks everything`() {
        val matcher = NgramMatcher(
            primaryNgrams = listOf(listOf("экология")),
            secondaryNgrams = emptyList(),
            excludedNgrams = listOf(listOf("экологичный", "продукт")),
        )
        val result = matcher.match(listOf("экологичный", "продукт", "экология"))
        assertTrue(result.excludedHit)
        assertEquals(0, result.primaryHits)
    }

    // --- BayesBetaValidator ---

    @Test
    fun `BayesBeta with perfect precision gives posterior mean near 1`() {
        val votes = (1..50).map { true to true as Boolean? }
        val metrics = BayesBetaValidator.computeMetrics(votes)

        assertTrue(metrics.precision.mean > 0.95, "precision=${metrics.precision.mean}")
        assertTrue(metrics.precision.ci95Lo > 0.85)
    }

    @Test
    fun `BayesBeta with 50-50 precision gives posterior mean near 0_5`() {
        val tp = (1..25).map { true to true as Boolean? }
        val fp = (1..25).map { true to false as Boolean? }
        val metrics = BayesBetaValidator.computeMetrics(tp + fp)

        assertTrue(metrics.precision.mean in 0.4..0.6, "precision=${metrics.precision.mean}")
    }

    @Test
    fun `BayesBeta recall tracks false negatives`() {
        // 20 TP, 10 FN → recall ~ 20/30 = 0.667
        val tp = (1..20).map { true to true as Boolean? }
        val fn = (1..10).map { false to true as Boolean? }
        val metrics = BayesBetaValidator.computeMetrics(tp + fn)

        assertTrue(metrics.recall.mean in 0.55..0.75, "recall=${metrics.recall.mean}")
    }

    @Test
    fun `BayesBeta with no votes returns uniform prior mean 0_5`() {
        val metrics = BayesBetaValidator.computeMetrics(emptyList())
        assertEquals(0.5, metrics.precision.mean, 0.01)
        assertEquals(0.5, metrics.recall.mean, 0.01)
    }

    @Test
    fun `BayesBeta CI narrows with more data`() {
        val small = (1..5).map { true to true as Boolean? }
        val large = (1..100).map { true to true as Boolean? }

        val metricsSmall = BayesBetaValidator.computeMetrics(small)
        val metricsLarge = BayesBetaValidator.computeMetrics(large)

        val widthSmall = metricsSmall.precision.ci95Hi - metricsSmall.precision.ci95Lo
        val widthLarge = metricsLarge.precision.ci95Hi - metricsLarge.precision.ci95Lo

        assertTrue(widthLarge < widthSmall, "CI should narrow: small=$widthSmall, large=$widthLarge")
    }
}
