/*
 * НАЗНАЧЕНИЕ
 * Юнит-тесты этапа 6 (тематическая фильтрация): двухпроходный фильтр L1/L2,
 * сопоставление n-грамм и байесовская валидация качества фильтра по Beta-модели.
 *
 * ЧТО ВНУТРИ
 * Класс TopicFilterTest c группами тестов:
 *   1) TopicRelevanceFilter.score — расчёт L1 по ключевым словам, отнесение к
 *      стратам CONFIDENT/DISPUTED/EXCLUDED, признак relevant (порог уверенности 0.50);
 *      в режиме FALLBACK_ONLY второй проход L2 (RuBERT) не выполняется (l2=null);
 *   2) NgramMatcher.match — поиск униграмм/биграмм, блокировка по исключающим n-граммам;
 *   3) BayesBetaValidator.computeMetrics — апостериорные precision и recall с CI 95
 *      процентов на основе равномерного Beta-приора, сужение CI с ростом данных.
 *
 * МЕТОД
 * L1 = min(primaryHits + 0.3·secondaryHits, 3) / 3 — нормированная сила ключевых слов;
 * наличие исключающей n-граммы обнуляет L1 и стратифицирует как EXCLUDED.
 * Beta-апостериор: precision и recall с равномерным приором (0.5 при отсутствии данных),
 * доверительный интервал сужается по мере накопления голосов. См. docs/algorithm.md, этап 6.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@Test); kotlinx.coroutines.runBlocking — score является suspend-функцией.
 *
 * СВЯЗИ
 * Тестируемые типы из пакета analysis/topic: TopicRelevanceFilter, NgramMatcher,
 * NgramMatchResult, TopicStratum, BayesBetaValidator.
 */
package com.example.lomanalyzer.analysis.topic

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Тесты двухпроходного тематического фильтра, сопоставления n-грамм и Beta-валидации (этап 6).
 */
class TopicFilterTest {

    // --- TopicRelevanceFilter ---

    /**
     * В режиме FALLBACK_ONLY (без RuBERT) сильный L1 даёт страту CONFIDENT.
     * Arrange: 2 первичных + 1 вторичное попадание. По формуле L1=min(2+0.3·1,3)/3=0.7667.
     * Assert: L1≈0.767, второй проход не выполнялся (l2=null), метод L1_CONFIDENT,
     * страта CONFIDENT, пост релевантен (0.767 ≥ порог уверенности 0.50).
     */
    @Test
    fun `FALLBACK_ONLY mode with strong L1 is CONFIDENT`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        // Arrange: результат сопоставления n-грамм (2 первичных, 1 вторичное, без исключений)
        val match = NgramMatchResult(primaryHits = 2, secondaryHits = 1, excludedHit = false)
        // Act: только первый проход (L1), так как режим без NLP-сервиса
        val result = filter.score(match, "dummy text")

        // l1 = min(2 + 0.3*1, 3) / 3 = min(2.3, 3) / 3 = 0.7667
        assertTrue(result.l1 > 0.76f && result.l1 < 0.77f, "l1=${result.l1}")
        assertNull(result.l2)
        assertEquals("L1_CONFIDENT", result.method)
        assertEquals(TopicStratum.CONFIDENT, result.stratum)
        assertTrue(result.relevant) // 0.767 >= 0.50 confident threshold
    }

    /**
     * Проверяет ограничение (clamp) числителя L1 на 3: при 5 первичных попаданиях
     * L1=min(5,3)/3=1.0 — большее число ключевых слов не повышает оценку выше 1.0.
     */
    @Test
    fun `L1 formula clamps at 3 primary hits`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        val match = NgramMatchResult(primaryHits = 5, secondaryHits = 0, excludedHit = false)
        val result = filter.score(match, "")

        assertEquals(1.0f, result.combined) // min(5, 3)/3 = 1.0
    }

    /**
     * Наличие исключающей n-граммы (excludedHit=true) полностью блокирует релевантность:
     * L1 обнуляется, пост нерелевантен, страта EXCLUDED — независимо от числа совпадений.
     */
    @Test
    fun `excluded ngrams produce EXCLUDED stratum`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        val match = NgramMatchResult(primaryHits = 3, secondaryHits = 2, excludedHit = true)
        val result = filter.score(match, "")

        assertEquals(0f, result.l1)
        assertFalse(result.relevant)
        assertEquals(TopicStratum.EXCLUDED, result.stratum)
    }

    /**
     * Полное отсутствие попаданий → итоговая оценка 0, пост нерелевантен, страта DISPUTED
     * (нет оснований ни отнести к теме, ни исключить).
     */
    @Test
    fun `no hits produce score 0 and DISPUTED`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        val match = NgramMatchResult(primaryHits = 0, secondaryHits = 0, excludedHit = false)
        val result = filter.score(match, "")

        assertEquals(0f, result.combined)
        assertFalse(result.relevant)
        assertEquals(TopicStratum.DISPUTED, result.stratum)
    }

    /**
     * Пограничный L1 в режиме FALLBACK_ONLY: одно первичное попадание даёт
     * L1=min(1,3)/3≈0.333 — ниже порога уверенности 0.50, но выше минимума.
     * Без второго прохода L2 такой пост относится к страте DISPUTED и нерелевантен.
     */
    @Test
    fun `borderline L1 in FALLBACK mode is DISPUTED`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        // l1 = min(1, 3)/3 = 0.333... (below confident threshold 0.50, above MIN_L1 0.01)
        val match = NgramMatchResult(primaryHits = 1, secondaryHits = 0, excludedHit = false)
        val result = filter.score(match, "")

        assertEquals(TopicStratum.DISPUTED, result.stratum)
        assertFalse(result.relevant, "borderline L1 in fallback mode is DISPUTED")
    }

    /**
     * Сильный L1 (2 первичных → L1=0.667 ≥ 0.50) сразу даёт страту CONFIDENT и
     * релевантность без обращения ко второму проходу.
     */
    @Test
    fun `strong L1 is CONFIDENT and relevant`() = runBlocking {
        val filter = TopicRelevanceFilter(nlpMode = "FALLBACK_ONLY")
        // l1 = min(2, 3)/3 = 0.667 (above confident threshold 0.50)
        val match = NgramMatchResult(primaryHits = 2, secondaryHits = 0, excludedHit = false)
        val result = filter.score(match, "")

        assertEquals(TopicStratum.CONFIDENT, result.stratum)
        assertTrue(result.relevant)
    }

    // --- NgramMatcher ---

    /**
     * NgramMatcher находит первичные униграммы: среди лемм текста встречается одно
     * ключевое слово («экология») → primaryHits=1, исключений нет.
     */
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

    /**
     * NgramMatcher находит биграммы: последовательность лемм «изменение климата»
     * соответствует первичной биграмме → primaryHits=1 (учитывается порядок слов).
     */
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

    /**
     * Исключающая n-грамма блокирует учёт первичных: текст содержит и исключающую
     * биграмму «экологичный продукт», и первичное «экология». Совпадение исключения
     * выставляет excludedHit=true и обнуляет primaryHits.
     */
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

    /**
     * Beta-валидация при идеальной точности: 50 голосов вида (предсказано true,
     * фактически true) — все срабатывания верны. Апостериорное среднее precision
     * приближается к 1 (>0.95), а нижняя граница CI 95 процентов уверенно выше 0.85.
     * Пара (predicted, actual): первый — метка фильтра, второй — эталон (null = неразмечено).
     */
    @Test
    fun `BayesBeta with perfect precision gives posterior mean near 1`() {
        val votes = (1..50).map { true to true as Boolean? }
        val metrics = BayesBetaValidator.computeMetrics(votes)

        assertTrue(metrics.precision.mean > 0.95, "precision=${metrics.precision.mean}")
        assertTrue(metrics.precision.ci95Lo > 0.85)
    }

    /**
     * Beta-валидация при точности 50/50: 25 истинных срабатываний (TP) и 25 ложных (FP).
     * Апостериорное среднее precision около 0.5.
     */
    @Test
    fun `BayesBeta with 50-50 precision gives posterior mean near 0_5`() {
        val tp = (1..25).map { true to true as Boolean? }
        val fp = (1..25).map { true to false as Boolean? }
        val metrics = BayesBetaValidator.computeMetrics(tp + fp)

        assertTrue(metrics.precision.mean in 0.4..0.6, "precision=${metrics.precision.mean}")
    }

    /**
     * Recall учитывает ложные пропуски: 20 TP и 10 FN (фактически тема, но фильтр
     * не распознал) → recall ≈ 20/30 = 0.667. Проверяет окрестность этого значения.
     */
    @Test
    fun `BayesBeta recall tracks false negatives`() {
        // 20 TP, 10 FN → recall ~ 20/30 = 0.667
        val tp = (1..20).map { true to true as Boolean? }
        val fn = (1..10).map { false to true as Boolean? }
        val metrics = BayesBetaValidator.computeMetrics(tp + fn)

        assertTrue(metrics.recall.mean in 0.55..0.75, "recall=${metrics.recall.mean}")
    }

    /**
     * Без размеченных голосов модель опирается на равномерный Beta-приор:
     * апостериорные средние precision и recall равны 0.5 (отсутствие информации).
     */
    @Test
    fun `BayesBeta with no votes returns uniform prior mean 0_5`() {
        val metrics = BayesBetaValidator.computeMetrics(emptyList())
        assertEquals(0.5, metrics.precision.mean, 0.01)
        assertEquals(0.5, metrics.recall.mean, 0.01)
    }

    /**
     * Свойство байесовского оценивания: с ростом числа наблюдений CI сужается.
     * При 100 одинаковых голосах ширина CI precision меньше, чем при 5 голосах
     * (больше данных → меньше неопределённость).
     */
    @Test
    fun `BayesBeta CI narrows with more data`() {
        val small = (1..5).map { true to true as Boolean? }
        val large = (1..100).map { true to true as Boolean? }

        val metricsSmall = BayesBetaValidator.computeMetrics(small)
        val metricsLarge = BayesBetaValidator.computeMetrics(large)

        // Сравниваем ширины CI precision для малой и большой выборок
        val widthSmall = metricsSmall.precision.ci95Hi - metricsSmall.precision.ci95Lo
        val widthLarge = metricsLarge.precision.ci95Hi - metricsLarge.precision.ci95Lo

        assertTrue(widthLarge < widthSmall, "CI should narrow: small=$widthSmall, large=$widthLarge")
    }
}
