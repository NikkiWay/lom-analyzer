/*
 * НАЗНАЧЕНИЕ
 * Тесты инструмента анализа чувствительности: каталог варьируемых параметров
 * (робастная статистика, бутстрап, дедуп, достаточность, сбор, тема), проверка
 * корректности их диапазонов, метрика хи-квадрат для сравнения распределений
 * ролей, классификация силы влияния, генерация отчёта и загрузка тест-корпуса.
 *
 * ЧТО ВНУТРИ
 * Класс SensitivityTest:
 *  - SensitivityParameters.ALL покрывают все обязательные категории;
 *  - для каждого параметра выполняется упорядоченность low <= default <= high;
 *  - конкретные значения huber_k (1.345/1.0/1.7) и baseline_window_days (60/30/120);
 *  - наличие параметров бутстрапа;
 *  - chiSquareRoleDist: 0 для идентичных распределений, >0 для различных;
 *  - classifyImpact: HIGH для большого хи-квадрата, LOW для малых изменений;
 *  - generateReport содержит все секции;
 *  - TestCorpusLoader валидирует схему и ловит отсутствие ground_truth.
 *
 * МЕТОД
 * Хи-квадрат — мера расхождения распределений ролей при варьировании параметра;
 * huber_k=1.345 — параметр M-оценки Хьюбера; бутстрап — оценка доверит. интервалов.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (assert*, assertEquals с дельтой для double).
 *
 * СВЯЗИ
 * SensitivityParameters, SensitivityHarness, SensitivityReport, SensitivityRunResult,
 * TestCorpusLoader (пакет tools.sensitivity).
 */
package com.example.lomanalyzer.tools

import com.example.lomanalyzer.tools.sensitivity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Тесты каталога параметров чувствительности, хи-квадрата, отчёта и корпуса. */
class SensitivityTest {

    /**
     * Каталог параметров должен покрывать все обязательные предметные области
     * (категории): робастная статистика, бутстрап, дедуп, достаточность, сбор, тема.
     */
    @Test
    fun `sensitivity parameters cover all required domains`() {
        // Уникальные категории среди всех параметров
        val categories = SensitivityParameters.ALL.map { it.category }.distinct()
        assertTrue("ROBUST_STATS" in categories, "missing ROBUST_STATS")
        assertTrue("BOOTSTRAP" in categories, "missing BOOTSTRAP")
        assertTrue("DEDUP" in categories, "missing DEDUP")
        assertTrue("SUFFICIENCY" in categories, "missing SUFFICIENCY")
        assertTrue("COLLECTION" in categories, "missing COLLECTION")
        assertTrue("TOPIC" in categories, "missing TOPIC")
    }

    /**
     * Инвариант диапазона: для каждого параметра нижнее значение не больше
     * значения по умолчанию, а оно не больше верхнего (low <= default <= high).
     */
    @Test
    fun `each parameter has valid low-default-high ordering`() {
        for (p in SensitivityParameters.ALL) {
            assertTrue(
                p.lowValue <= p.defaultValue && p.defaultValue <= p.highValue,
                "${p.name}: low=${p.lowValue} default=${p.defaultValue} high=${p.highValue}",
            )
        }
    }

    /**
     * Параметр huber_k (порог M-оценки Хьюбера) имеет каноничные значения:
     * default=1.345, low=1.0, high=1.7 (см. методологию робастной статистики).
     */
    @Test
    fun `huber_k parameter has correct default`() {
        val huberK = SensitivityParameters.ALL.first { it.name == "huber_k" }
        assertEquals(1.345, huberK.defaultValue, 0.001)
        assertEquals(1.0, huberK.lowValue, 0.001)
        assertEquals(1.7, huberK.highValue, 0.001)
    }

    /**
     * В каталоге присутствуют все параметры бутстрапа: одноуровневый B,
     * двухуровневые B_outer/B_inner и уровень доверия confidence_level.
     */
    @Test
    fun `bootstrap parameters present`() {
        val names = SensitivityParameters.ALL.map { it.name }
        assertTrue("bootstrap_B" in names)
        assertTrue("bootstrap_B_outer" in names)
        assertTrue("bootstrap_B_inner" in names)
        assertTrue("confidence_level" in names)
    }

    /**
     * Параметр baseline_window_days (окно фонового периода в днях) имеет
     * диапазон 30–60–120: default=60, low=30, high=120.
     */
    @Test
    fun `baseline_window_days range covers 30-60-120`() {
        val p = SensitivityParameters.ALL.first { it.name == "baseline_window_days" }
        assertEquals(60.0, p.defaultValue, 0.001)
        assertEquals(30.0, p.lowValue, 0.001)
        assertEquals(120.0, p.highValue, 0.001)
    }

    // ── Chi-square ──

    /** Хи-квадрат двух идентичных распределений ролей равен 0 (нет расхождения). */
    @Test
    fun `chi-square is 0 for identical distributions`() {
        val dist = mapOf("A" to 10, "B" to 20, "C" to 15)
        // Сравниваем распределение само с собой
        val chi = SensitivityHarness.chiSquareRoleDist(dist, dist)
        assertEquals(0.0, chi, 0.001)
    }

    /** Для различающихся распределений хи-квадрат строго положителен. */
    @Test
    fun `chi-square is positive for different distributions`() {
        val baseline = mapOf("A" to 10, "B" to 20, "C" to 15)
        // Перераспределение частот между A и B
        val shifted = mapOf("A" to 20, "B" to 10, "C" to 15)
        val chi = SensitivityHarness.chiSquareRoleDist(shifted, baseline)
        assertTrue(chi > 0, "chi=$chi")
    }

    // ── Impact classification ──

    /** Большой хи-квадрат (15.0) классифицируется как сильное влияние HIGH. */
    @Test
    fun `high impact for large chi-square`() {
        assertEquals("HIGH", SensitivityHarness.classifyImpact(15.0, 0.05, 1))
    }

    /** Малые изменения (малый хи-квадрат, нет смен ролей) → слабое влияние LOW. */
    @Test
    fun `low impact for small changes`() {
        assertEquals("LOW", SensitivityHarness.classifyImpact(1.0, 0.01, 0))
    }

    // ── Report generation ──

    /**
     * generateReport формирует Markdown-отчёт, содержащий все ключевые секции:
     * заголовок, базовый сценарий (Baseline) и строку по варьируемому параметру (huber_k).
     */
    @Test
    fun `report contains all sections`() {
        // Готовим модель отчёта с одним прогоном по huber_k
        val report = SensitivityReport(
            baselineRoleDistribution = mapOf("TOPIC_ACTIVIST" to 10, "BACKGROUND_AUTHOR" to 20),
            baselineMeanRisk = 0.0,
            baselineAnomalyCount = 0,
            runs = listOf(
                SensitivityRunResult("huber_k", "LOW", 1.0,
                    mapOf("TOPIC_ACTIVIST" to 12, "BACKGROUND_AUTHOR" to 18), 0.0, 0),
            ),
            highImpactParameters = listOf("bootstrap_B"),
        )
        val md = SensitivityHarness.generateReport(report)
        assertTrue(md.contains("Sensitivity Analysis Report"))
        assertTrue(md.contains("Baseline"))
        assertTrue(md.contains("huber_k"))
    }

    // ── Test corpus ──

    /**
     * TestCorpusLoader: корректный JSON-корпус (с полным ground_truth у поста)
     * проходит валидацию без ошибок.
     */
    @Test
    fun `corpus loader validates schema`() {
        // Валидный корпус: пост содержит ground_truth
        val json = """{
            "version": "test",
            "posts": [
                {"id": 1, "from_id": 1, "published_at": 1000, "text_clean": "test",
                 "likes": 5, "reposts": 1, "comments": 1, "own_text_length": 4,
                 "has_copy_history": false, "contains_media": false,
                 "ground_truth": {"is_topic_relevant": true, "sentiment": "POSITIVE"}}
            ],
            "authors": [{"id": 1, "followers_count": 100}]
        }"""
        val corpus = TestCorpusLoader.load(json)
        val errors = TestCorpusLoader.validate(corpus)
        assertTrue(errors.isEmpty(), "errors=$errors")
    }

    /**
     * Негативный случай: пост без блока ground_truth должен дать ошибку валидации
     * с упоминанием "missing ground_truth" (корпус для эталонной разметки неполон).
     */
    @Test
    fun `corpus loader catches missing ground truth`() {
        // Пост без ground_truth — невалиден
        val json = """{
            "version": "test",
            "posts": [
                {"id": 1, "from_id": 1, "published_at": 1000, "text_clean": "test",
                 "likes": 5, "reposts": 1, "comments": 1, "own_text_length": 4,
                 "has_copy_history": false, "contains_media": false}
            ],
            "authors": [{"id": 1, "followers_count": 100}]
        }"""
        val corpus = TestCorpusLoader.load(json)
        val errors = TestCorpusLoader.validate(corpus)
        assertTrue(errors.any { it.contains("missing ground_truth") })
    }
}
