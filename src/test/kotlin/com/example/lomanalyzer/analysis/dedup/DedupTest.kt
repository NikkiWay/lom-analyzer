package com.example.lomanalyzer.analysis.dedup

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DedupTest {

    // --- ExactHasher (Stage 1: ALL posts with ownTextLength >= 30) ---

    @Test
    fun `identical texts from different authors produce DETECTED_COPY`() {
        val text = "Это важный текст о экологии и загрязнении окружающей среды"
        fun post(id: Int, from: Int, at: Long) =
            HashablePost(id, from, at, text, text.length, true)
        val posts = listOf(
            post(1, 100, 1000L),
            post(2, 200, 2000L),
            HashablePost(3, 300, 3000L, text, text.length, null),
        )

        val groups = ExactHasher.findDuplicateGroups(posts)
        assertEquals(1, groups.size)

        val group = groups.values.first()
        assertEquals(3, group.size)
        // First by publishedAt is the leader
        assertEquals(1, group[0].postId)
        assertEquals(2, group[1].postId)
        assertEquals(3, group[2].postId)
    }

    @Test
    fun `different texts are not grouped`() {
        val posts = listOf(
            HashablePost(1, 100, 1000L, "Первый текст достаточной длины для хэширования", 48, true),
            HashablePost(2, 200, 2000L, "Второй совершенно другой текст для проверки", 45, true),
        )
        val groups = ExactHasher.findDuplicateGroups(posts)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `short texts below 30 chars are excluded from Stage 1`() {
        val text = "Короткий"
        val posts = listOf(
            HashablePost(1, 100, 1000L, text, text.length, true),
            HashablePost(2, 200, 2000L, text, text.length, true),
        )
        val groups = ExactHasher.findDuplicateGroups(posts)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `hash normalizes URLs and mentions`() {
        val hash1 = ExactHasher.normalizeAndHash("проверка [URL] текста [USER] пример")
        val hash2 = ExactHasher.normalizeAndHash("проверка текста пример")
        assertEquals(hash1, hash2)
    }

    // --- BoundedJaccard (Stage 2: ONLY topical posts with ownTextLength >= 100) ---

    @Test
    fun `near-duplicate within 72h detected`() {
        val jaccard = BoundedJaccard(threshold = 0.75f, windowHours = 72)
        val lemmasA = "экология загрязнение воздух город проблема решение вопрос дело".split(" ")
        val lemmasB = "экология загрязнение воздух город проблема решение вопрос ответ".split(" ")
        // 7 of 8 words shared → 6/8 bigrams shared = 0.75

        val withinWindow = 24 * 3600 * 1000L // 24h in ms
        val (isDup, sim) = jaccard.isNearDuplicate(
            lemmasA, lemmasB,
            publishedAtA = 1000000L,
            publishedAtB = 1000000L + withinWindow,
        )
        assertTrue(isDup, "similarity=$sim should be >= 0.75")
        assertTrue(sim >= 0.75f)
    }

    @Test
    fun `near-duplicate outside 72h window not detected`() {
        val jaccard = BoundedJaccard(threshold = 0.75f, windowHours = 72)
        val lemmas = "экология загрязнение воздух город проблема решение вопрос".split(" ")

        val outsideWindow = 73 * 3600 * 1000L // 73h in ms
        val (isDup, _) = jaccard.isNearDuplicate(
            lemmas, lemmas,
            publishedAtA = 1000000L,
            publishedAtB = 1000000L + outsideWindow,
        )
        assertFalse(isDup)
    }

    @Test
    fun `dissimilar texts not flagged as near-duplicate`() {
        val jaccard = BoundedJaccard(threshold = 0.75f, windowHours = 72)
        val lemmasA = "экология загрязнение воздух".split(" ")
        val lemmasB = "политика выборы кандидат".split(" ")

        val (isDup, sim) = jaccard.isNearDuplicate(
            lemmasA, lemmasB,
            publishedAtA = 1000000L,
            publishedAtB = 1000000L,
        )
        assertFalse(isDup)
        assertTrue(sim < 0.75f)
    }

    @Test
    fun `Stage 2 eligibility requires topical and ownTextLength 100+`() {
        val jaccard = BoundedJaccard()
        assertTrue(jaccard.isEligible(100, true))
        assertFalse(jaccard.isEligible(99, true))
        assertFalse(jaccard.isEligible(100, false))
        assertFalse(jaccard.isEligible(100, null))
    }

    // --- OriginalityClassifier (all 5 types) ---

    @Test
    fun `ORIGINAL — no copy history, text ge 20, not detected copy`() {
        val result = OriginalityClassifier.classify(
            hasCopyHistory = false, ownTextLength = 100,
            containsMedia = false, isDetectedCopy = false,
        )
        assertEquals(OriginalityType.ORIGINAL, result)
        assertEquals(1.0f, result.weight)
    }

    @Test
    fun `REPOST_WITH_COMMENT — has copy history, text ge 30`() {
        val result = OriginalityClassifier.classify(
            hasCopyHistory = true, ownTextLength = 50,
            containsMedia = false, isDetectedCopy = false,
        )
        assertEquals(OriginalityType.REPOST_WITH_COMMENT, result)
        assertEquals(0.5f, result.weight)
    }

    @Test
    fun `PURE_REPOST — has copy history, text lt 30`() {
        val result = OriginalityClassifier.classify(
            hasCopyHistory = true, ownTextLength = 10,
            containsMedia = false, isDetectedCopy = false,
        )
        assertEquals(OriginalityType.PURE_REPOST, result)
        assertEquals(0.0f, result.weight)
    }

    @Test
    fun `DETECTED_COPY — overrides everything`() {
        val result = OriginalityClassifier.classify(
            hasCopyHistory = false, ownTextLength = 200,
            containsMedia = true, isDetectedCopy = true,
        )
        assertEquals(OriginalityType.DETECTED_COPY, result)
        assertEquals(0.0f, result.weight)
    }

    @Test
    fun `MEDIA_ONLY — no copy history, text lt 20, has media`() {
        val result = OriginalityClassifier.classify(
            hasCopyHistory = false, ownTextLength = 5,
            containsMedia = true, isDetectedCopy = false,
        )
        assertEquals(OriginalityType.MEDIA_ONLY, result)
        assertEquals(0.25f, result.weight)
    }
}
