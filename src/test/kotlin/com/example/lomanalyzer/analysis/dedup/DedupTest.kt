/*
 * НАЗНАЧЕНИЕ
 * Юнит-тесты дедупликации и классификации оригинальности (этап 5 алгоритма):
 * точное хэширование копий, поиск near-дубликатов по нормализованному
 * расстоянию Левенштейна и определение типа оригинальности поста.
 *
 * ЧТО ВНУТРИ
 * Класс DedupTest c группами тестов:
 *   1) ExactHasher — этап 1 дедупа: группировка точных копий (тексты длиной ≥30),
 *      лидер группы = самый ранний по publishedAt; нормализация URL и упоминаний;
 *   2) NormalizedLevenshtein — этап 2: порог близости 0.90 и окно ±72 ч,
 *      проверка пригодности постов (тематический и собственный текст ≥100);
 *   3) OriginalityClassifier — пять типов оригинальности и их веса
 *      (ORIGINAL=1.0, REPOST_WITH_COMMENT=0.5, MEDIA_ONLY=0.25, PURE_REPOST=0.0,
 *      DETECTED_COPY=0.0, переопределяет всё).
 *
 * МЕТОД
 * Нормализованная близость = 1 − editDistance / max(len). Near-дубликат: близость
 * ≥ порога И посты в одном временном окне (см. docs/formulas.md, этап 5).
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@Test, статические assert-методы).
 *
 * СВЯЗИ
 * Тестируемые типы из пакета analysis/dedup: ExactHasher, HashablePost,
 * NormalizedLevenshtein, OriginalityClassifier, OriginalityType.
 */
package com.example.lomanalyzer.analysis.dedup

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Тесты точного хэширования, near-дубликатов (Левенштейн 0.90/72ч) и типов оригинальности.
 */
class DedupTest {

    // --- ExactHasher (Stage 1: ALL posts with ownTextLength >= 30) ---

    /**
     * Этап 1 дедупа: три поста с идентичным текстом от разных авторов образуют одну
     * группу точных копий.
     * Arrange: три HashablePost с одинаковым текстом (длина ≥30) и временами 1000/2000/3000.
     * Assert: ровно одна группа из 3 постов; внутри группа упорядочена по publishedAt,
     * поэтому лидер группы — самый ранний пост (id=1), затем 2, затем 3.
     */
    @Test
    fun `identical texts from different authors produce DETECTED_COPY`() {
        val text = "Это важный текст о экологии и загрязнении окружающей среды"
        // Локальный конструктор поста с общим текстом
        fun post(id: Int, from: Int, at: Long) =
            HashablePost(id, from, at, text, text.length, true)
        val posts = listOf(
            post(1, 100, 1000L),
            post(2, 200, 2000L),
            HashablePost(3, 300, 3000L, text, text.length, null),
        )

        // Act: группировка точных копий по нормализованному хэшу текста
        val groups = ExactHasher.findDuplicateGroups(posts)
        assertEquals(1, groups.size)

        val group = groups.values.first()
        assertEquals(3, group.size)
        // First by publishedAt is the leader
        assertEquals(1, group[0].postId)
        assertEquals(2, group[1].postId)
        assertEquals(3, group[2].postId)
    }

    /**
     * Разные тексты не должны попадать в одну группу: при отсутствии совпадений
     * хэшей групп дубликатов нет.
     */
    @Test
    fun `different texts are not grouped`() {
        val posts = listOf(
            HashablePost(1, 100, 1000L, "Первый текст достаточной длины для хэширования", 48, true),
            HashablePost(2, 200, 2000L, "Второй совершенно другой текст для проверки", 45, true),
        )
        val groups = ExactHasher.findDuplicateGroups(posts)
        assertTrue(groups.isEmpty())
    }

    /**
     * Тексты короче 30 символов исключаются из этапа 1 (порог минимальной длины):
     * даже идентичные короткие тексты не образуют группу.
     */
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

    /**
     * Нормализация перед хэшированием убирает URL и упоминания (плейсхолдеры
     * [URL] и [USER]), поэтому тексты, отличающиеся только ими, дают одинаковый хэш.
     */
    @Test
    fun `hash normalizes URLs and mentions`() {
        // Текст с плейсхолдерами ссылок и упоминаний
        val hash1 = ExactHasher.normalizeAndHash("проверка [URL] текста [USER] пример")
        // Тот же текст без них
        val hash2 = ExactHasher.normalizeAndHash("проверка текста пример")
        // Хэши совпадают — нормализация удалила незначимые элементы
        assertEquals(hash1, hash2)
    }

    // --- NormalizedLevenshtein (Stage 2: threshold 0.90, 72h window) ---

    /**
     * Этап 2: near-дубликат внутри окна 72 ч определяется по Левенштейну.
     * Arrange: две последовательности по 10 лемм, отличающиеся одним токеном
     * (последний: тема против тезис) → дистанция правок 1, близость = 1 − 1/10 = 0.90;
     * посты опубликованы с разницей 24 ч (внутри окна 72 ч).
     * Assert: пост помечен как дубликат и близость ≥ порога 0.90.
     */
    @Test
    fun `near-duplicate within 72h detected by Levenshtein`() {
        val lev = NormalizedLevenshtein(threshold = 0.90f, windowHours = 72)
        // 9 of 10 tokens identical -> edit distance 1, sim = 1 - 1/10 = 0.90
        val lemmasA = "экология загрязнение воздух город проблема решение вопрос дело важный тема".split(" ")
        val lemmasB = "экология загрязнение воздух город проблема решение вопрос дело важный тезис".split(" ")

        val withinWindow = 24 * 3600L // 24h in seconds
        // Act: проверка near-дубликата с учётом близости и временного окна
        val (isDup, sim) = lev.isNearDuplicate(
            lemmasA, lemmasB,
            publishedAtA = 1000000L,
            publishedAtB = 1000000L + withinWindow,
        )
        assertTrue(isDup, "similarity=$sim should be >= 0.90")
        assertTrue(sim >= 0.90f)
    }

    /**
     * Базовый инвариант близости: идентичные последовательности лемм дают близость 1.0
     * (1 − 0/max = 1).
     */
    @Test
    fun `identical texts have similarity 1_0`() {
        val lev = NormalizedLevenshtein()
        val lemmas = "экология загрязнение воздух город проблема".split(" ")
        val sim = lev.computeSimilarity(lemmas, lemmas)
        assertEquals(1.0f, sim)
    }

    /**
     * Временное окно обязательно: даже идентичные тексты (близость 1.0) НЕ считаются
     * near-дубликатами, если разрыв публикаций превышает 72 ч (здесь 73 ч).
     */
    @Test
    fun `near-duplicate outside 72h window not detected`() {
        val lev = NormalizedLevenshtein(threshold = 0.90f, windowHours = 72)
        val lemmas = "экология загрязнение воздух город проблема решение вопрос".split(" ")

        val outsideWindow = 73 * 3600L // 73h in seconds
        // Act: тексты идентичны, но опубликованы за пределами окна
        val (isDup, _) = lev.isNearDuplicate(
            lemmas, lemmas,
            publishedAtA = 1000000L,
            publishedAtB = 1000000L + outsideWindow,
        )
        assertFalse(isDup)
    }

    /**
     * Непохожие тексты (разные темы) не помечаются как near-дубликаты даже в одном окне:
     * близость заметно ниже порога 0.90.
     */
    @Test
    fun `dissimilar texts not flagged as near-duplicate`() {
        val lev = NormalizedLevenshtein(threshold = 0.90f, windowHours = 72)
        val lemmasA = "экология загрязнение воздух".split(" ")
        val lemmasB = "политика выборы кандидат".split(" ")

        val (isDup, sim) = lev.isNearDuplicate(
            lemmasA, lemmasB,
            publishedAtA = 1000000L,
            publishedAtB = 1000000L,
        )
        assertFalse(isDup)
        assertTrue(sim < 0.90f)
    }

    /**
     * Условия попадания поста на этап 2 (isEligible): пост должен быть тематическим
     * И иметь собственный текст длиной не меньше 100. Проверяются все комбинации:
     * 100+тематический → да; 99 символов → нет; нетематический → нет; неизвестно (null) → нет.
     */
    @Test
    fun `Stage 2 eligibility requires topical and ownTextLength 100+`() {
        val lev = NormalizedLevenshtein()
        assertTrue(lev.isEligible(100, true))
        assertFalse(lev.isEligible(99, true))
        assertFalse(lev.isEligible(100, false))
        assertFalse(lev.isEligible(100, null))
    }

    // --- OriginalityClassifier (all 5 types) ---

    /**
     * Тип ORIGINAL: нет истории репоста, достаточный собственный текст, не выявленная копия.
     * Самостоятельный авторский пост → вес вклада 1.0 (полный).
     */
    @Test
    fun `ORIGINAL — no copy history, text ge 20, not detected copy`() {
        val result = OriginalityClassifier.classify(
            hasCopyHistory = false, ownTextLength = 100,
            containsMedia = false, isDetectedCopy = false,
        )
        assertEquals(OriginalityType.ORIGINAL, result)
        assertEquals(1.0f, result.weight)
    }

    /**
     * Тип REPOST_WITH_COMMENT: есть история репоста, но добавлен значимый комментарий
     * (собственный текст ≥30). Частичный вклад → вес 0.5.
     */
    @Test
    fun `REPOST_WITH_COMMENT — has copy history, text ge 30`() {
        val result = OriginalityClassifier.classify(
            hasCopyHistory = true, ownTextLength = 50,
            containsMedia = false, isDetectedCopy = false,
        )
        assertEquals(OriginalityType.REPOST_WITH_COMMENT, result)
        assertEquals(0.5f, result.weight)
    }

    /**
     * Тип PURE_REPOST: есть история репоста и почти нет своего текста (<30).
     * Несамостоятельный контент → вес 0.0 (не учитывается в оригинальности).
     */
    @Test
    fun `PURE_REPOST — has copy history, text lt 30`() {
        val result = OriginalityClassifier.classify(
            hasCopyHistory = true, ownTextLength = 10,
            containsMedia = false, isDetectedCopy = false,
        )
        assertEquals(OriginalityType.PURE_REPOST, result)
        assertEquals(0.0f, result.weight)
    }

    /**
     * Тип DETECTED_COPY имеет наивысший приоритет: признак isDetectedCopy=true
     * переопределяет все прочие признаки (даже длинный текст и наличие медиа) → вес 0.0.
     */
    @Test
    fun `DETECTED_COPY — overrides everything`() {
        val result = OriginalityClassifier.classify(
            hasCopyHistory = false, ownTextLength = 200,
            containsMedia = true, isDetectedCopy = true,
        )
        assertEquals(OriginalityType.DETECTED_COPY, result)
        assertEquals(0.0f, result.weight)
    }

    /**
     * Тип MEDIA_ONLY: нет репоста, мало текста (<20), но есть медиа — пост несёт
     * в основном визуальный контент. Частичный вклад → вес 0.25.
     */
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
