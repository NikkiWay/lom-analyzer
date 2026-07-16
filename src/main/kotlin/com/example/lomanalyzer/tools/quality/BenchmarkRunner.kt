/*
 * НАЗНАЧЕНИЕ
 * Бенчмарк производительности пайплайна: измерение времени выполнения этапов,
 * сравнение текущего прогона с базовой линией (baseline) и выявление регрессий
 * (gate G-02: рост более 20% относительно базы). Часть контроля качества.
 *
 * ЧТО ВНУТРИ
 * @Serializable data class StageTiming — время одного этапа; BenchmarkResult —
 * полный результат прогона (метка времени, суммарное время, тайминги этапов,
 * число постов/авторов). object BenchmarkRunner — сравнение с базой (compare),
 * сериализация в/из JSON и генерация markdown-отчёта.
 *
 * МЕТОД
 * Регрессией считается этап, чьё время превышает базовое более чем в 1.20 раза
 * (т. е. рост более 20%); процент роста округляется вниз.
 *
 * БИБЛИОТЕКИ
 * kotlinx.serialization (Json) — хранение результатов в JSON; StringBuilder —
 * сборка отчёта.
 *
 * СВЯЗИ
 * Артефакты используются QualityGates (G-02).
 */
package com.example.lomanalyzer.tools.quality

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Время выполнения одного этапа пайплайна, мс. */
@Serializable
data class StageTiming(val stage: String, val durationMs: Long)

/**
 * Результат одного бенчмарк-прогона.
 *
 * @property timestamp метка времени прогона (мс эпохи).
 * @property totalMs суммарное время прогона, мс.
 * @property stages тайминги по этапам.
 * @property postCount число обработанных постов.
 * @property authorCount число обработанных авторов.
 */
@Serializable
data class BenchmarkResult(
    val timestamp: Long = System.currentTimeMillis(),
    val totalMs: Long,
    val stages: List<StageTiming>,
    val postCount: Int,
    val authorCount: Int,
)

/** Сравнение бенчмарков, (де)сериализация и генерация отчёта. */
object BenchmarkRunner {
    /** JSON-сериализатор с форматированием для хранения результатов. */
    private val json = Json { prettyPrint = true }

    /**
     * Сравнивает текущий прогон с базовым и возвращает список регрессий по этапам
     * (рост времени более 20%).
     */
    fun compare(current: BenchmarkResult, baseline: BenchmarkResult): List<String> {
        val regressions = mutableListOf<String>()
        // Индексируем этапы базовой линии по имени для быстрого поиска
        val baselineMap = baseline.stages.associateBy { it.stage }

        for (stage in current.stages) {
            // Сопоставляем этап с базовым; если в базе его нет — пропускаем
            val base = baselineMap[stage.stage] ?: continue
            if (base.durationMs > 0) {
                // Отношение текущего времени к базовому
                val ratio = stage.durationMs.toDouble() / base.durationMs
                // Порог регрессии — рост более чем в 1.20 раза (>20%)
                if (ratio > 1.20) {
                    val pct = ((ratio - 1.0) * 100).toInt()
                    regressions.add("${stage.stage}: +$pct% regression " +
                        "(${base.durationMs}ms → ${stage.durationMs}ms)")
                }
            }
        }
        return regressions
    }

    /** Сериализует результат бенчмарка в JSON-строку. */
    fun toJson(result: BenchmarkResult): String = json.encodeToString(result)

    /** Десериализует результат бенчмарка из JSON-строки. */
    fun fromJson(jsonStr: String): BenchmarkResult = json.decodeFromString(jsonStr)

    /** Формирует markdown-отчёт: сводка, тайминги этапов и список регрессий (если есть). */
    fun generateReport(
        result: BenchmarkResult,
        regressions: List<String>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Benchmark Report")
        sb.appendLine()
        sb.appendLine("- Posts: ${result.postCount}")
        sb.appendLine("- Authors: ${result.authorCount}")
        sb.appendLine("- Total: ${result.totalMs}ms")
        sb.appendLine()
        sb.appendLine("## Stage Timings")
        sb.appendLine("| Stage | Duration (ms) |")
        sb.appendLine("|-------|--------------|")
        for (s in result.stages) {
            sb.appendLine("| ${s.stage} | ${s.durationMs} |")
        }
        if (regressions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Regressions (> 20%)")
            for (r in regressions) sb.appendLine("- $r")
        }
        return sb.toString()
    }
}
