/*
 * НАЗНАЧЕНИЕ
 * Тесты инструментов контроля качества (этап 8 — качество): детектор регрессий
 * производительности по бенчмаркам, согласие экспертов Cohen's kappa, набор из
 * 6 quality-gates и контроль времени пайплайна (NFR-03: не более 90 минут).
 *
 * ЧТО ВНУТРИ
 * Класс QualityGatesTest:
 *  - BenchmarkRunner.compare: нет регрессии при близких таймингах; регрессия при
 *    превышении этапа более чем на 20%; JSON-roundtrip результата бенчмарка;
 *  - CohenKappa.compute: kappa=1 при полном согласии; >0.6 при существенном;
 *    около 0 при случайном согласии;
 *  - QualityGates.evaluateAll/generateSummary: 6 гейтов (G-01..G-06), валидный Markdown;
 *  - NFR-03: baseline-бенчмарк укладывается в 90 минут (если файл присутствует).
 *
 * МЕТОД
 * Cohen's kappa — мера согласия двух экспертов с поправкой на случайность;
 * порог регрессии бенчмарка — рост времени этапа более чем на 20% от baseline.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (assert*, assertEquals с дельтой для double).
 *
 * СВЯЗИ
 * BenchmarkRunner, BenchmarkResult, StageTiming, CohenKappa, QualityGates
 * (пакет tools.quality), файлы benchmarks/baseline.json.
 */
package com.example.lomanalyzer.tools

import com.example.lomanalyzer.tools.quality.BenchmarkRunner
import com.example.lomanalyzer.tools.quality.BenchmarkResult
import com.example.lomanalyzer.tools.quality.CohenKappa
import com.example.lomanalyzer.tools.quality.QualityGates
import com.example.lomanalyzer.tools.quality.StageTiming
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Тесты бенчмарков, Cohen's kappa и quality-gates. */
class QualityGatesTest {

    // --- Benchmark regression detection ---

    /**
     * Регрессий нет, если тайминги текущего прогона близки к baseline (рост в
     * пределах допустимого порога ~20%). Assert: список регрессий пуст.
     */
    @Test
    fun `no regression when timings are similar`() {
        // Базовые тайминги этапов
        val baseline = BenchmarkResult(
            totalMs = 300,
            stages = listOf(StageTiming("A", 100), StageTiming("B", 200)),
            postCount = 500, authorCount = 50,
        )
        // Текущий прогон чуть медленнее, но в пределах порога
        val current = BenchmarkResult(
            totalMs = 310,
            stages = listOf(StageTiming("A", 105), StageTiming("B", 205)),
            postCount = 500, authorCount = 50,
        )
        val regressions = BenchmarkRunner.compare(current, baseline)
        assertTrue(regressions.isEmpty())
    }

    /**
     * Регрессия фиксируется, когда время этапа превышает baseline более чем на 20%.
     * Здесь этап A (+50%) и B (+25%) выходят за порог. Assert: оба этапа в отчёте.
     */
    @Test
    fun `regression detected when stage exceeds 20pct`() {
        val baseline = BenchmarkResult(
            totalMs = 300,
            stages = listOf(StageTiming("A", 100), StageTiming("B", 200)),
            postCount = 500, authorCount = 50,
        )
        // Этапы заметно медленнее baseline → должны попасть в регрессии
        val current = BenchmarkResult(
            totalMs = 400,
            stages = listOf(StageTiming("A", 150), StageTiming("B", 250)),
            postCount = 500, authorCount = 50,
        )
        val regressions = BenchmarkRunner.compare(current, baseline)
        assertTrue(regressions.any { it.contains("A") })
        assertTrue(regressions.any { it.contains("B") })
    }

    /**
     * Сериализация бенчмарка: toJson → fromJson сохраняет ключевые поля
     * (totalMs и число этапов) без потерь (roundtrip).
     */
    @Test
    fun `benchmark JSON roundtrip`() {
        val result = BenchmarkResult(
            totalMs = 290,
            stages = listOf(StageTiming("X", 100)),
            postCount = 500, authorCount = 50,
        )
        // Сериализуем и десериализуем обратно
        val json = BenchmarkRunner.toJson(result)
        val parsed = BenchmarkRunner.fromJson(json)
        assertEquals(result.totalMs, parsed.totalMs)
        assertEquals(result.stages.size, parsed.stages.size)
    }

    // --- Cohen's kappa ---

    /** Полное совпадение разметки двух экспертов даёт kappa = 1.0. */
    @Test
    fun `perfect agreement gives kappa 1`() {
        val labels = listOf("A", "B", "C", "A", "B")
        // Один и тот же список сравнивается сам с собой
        val kappa = CohenKappa.compute(labels, labels)
        assertEquals(1.0, kappa, 0.001)
    }

    /**
     * Существенное согласие (расхождение лишь в одной из 10 меток) даёт kappa > 0.6
     * (по шкале Лэндиса–Коха — "существенное").
     */
    @Test
    fun `substantial agreement gives kappa above 0_6`() {
        val r1 = listOf("A", "B", "A", "B", "A", "B", "A", "B", "A", "B")
        // r2 отличается от r1 ровно в одной позиции
        val r2 = listOf("A", "B", "A", "B", "A", "B", "A", "A", "A", "B")
        val kappa = CohenKappa.compute(r1, r2)
        assertTrue(kappa > 0.6, "kappa=$kappa")
    }

    /** Практически случайная разметка даёт kappa около 0 (порог < 0.3). */
    @Test
    fun `random agreement gives kappa near 0`() {
        val r1 = listOf("A", "B", "C", "A", "B", "C")
        // r2 почти не коррелирует с r1
        val r2 = listOf("C", "A", "B", "B", "C", "A")
        val kappa = CohenKappa.compute(r1, r2)
        assertTrue(kappa < 0.3, "kappa=$kappa for random")
    }

    // --- Quality gates ---

    /**
     * evaluateAll возвращает ровно 6 гейтов качества с идентификаторами G-01..G-06.
     * Assert: размер 6 и присутствие крайних идентификаторов G-01 и G-06.
     */
    @Test
    fun `gates summary contains all 6 gates`() {
        val results = QualityGates.evaluateAll()
        assertEquals(6, results.size)
        val ids = results.map { it.gateId }
        assertTrue("G-01" in ids)
        assertTrue("G-06" in ids)
    }

    /**
     * generateSummary формирует корректный Markdown-отчёт: содержит заголовок
     * "Quality Gates Summary" и шапку таблицы "| Gate |".
     */
    @Test
    fun `gates summary markdown format is valid`() {
        val results = QualityGates.evaluateAll()
        val summary = QualityGates.generateSummary(results)
        assertTrue(summary.contains("Quality Gates Summary"))
        assertTrue(summary.contains("| Gate |"))
    }

    // --- NFR-03: pipeline time ---

    /**
     * NFR-03: если файл baseline-бенчмарка присутствует, полное время пайплайна
     * должно укладываться в 90 минут (totalMs / 60000 < 90).
     */
    @Test
    fun `baseline benchmark under 90 minutes`() {
        val baseline = java.io.File("benchmarks/baseline.json")
        // Проверяем только при наличии файла бенчмарка
        if (baseline.exists()) {
            val result = BenchmarkRunner.fromJson(baseline.readText())
            // Переводим миллисекунды в минуты
            val minutes = result.totalMs / 60000.0
            assertTrue(
                minutes < 90,
                "Pipeline took ${minutes}min, limit is 90min",
            )
        }
    }
}
