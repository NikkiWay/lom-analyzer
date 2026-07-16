/*
 * НАЗНАЧЕНИЕ
 * Движок анализа чувствительности (sensitivity analysis): сравнивает итоги
 * пайплайна при базовых значениях параметров и при их сдвиге в low/high,
 * оценивает влияние каждого параметра на распределение ролей, средний риск и
 * число аномалий и формирует markdown-отчёт (см. v6 §25.5, docs/algorithm.md).
 *
 * ЧТО ВНУТРИ
 * data class SensitivityRunResult — итог одного прогона (параметр, вариант,
 * распределение ролей, средний риск, число аномалий). data class
 * SensitivityReport — базовая линия + все прогоны + список высоковлияющих
 * параметров. object SensitivityHarness — расчёт статистики хи-квадрат по
 * распределению ролей, классификация влияния и генерация отчёта.
 *
 * МЕТОД
 * Статистика хи-квадрат сравнивает наблюдаемое распределение ролей с базовым
 * (ожидаемым), масштабируя ожидаемые частоты под общий объём наблюдений.
 * Влияние параметра классифицируется по порогам хи-квадрат и модулю изменения
 * среднего риска: HIGH/MEDIUM/LOW.
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin (kotlin.math, StringBuilder) — внешних зависимостей нет.
 *
 * СВЯЗИ
 * Параметры берутся из SensitivityParameters (SensitivityParameter.kt).
 */
package com.example.lomanalyzer.tools.sensitivity

/**
 * Результат одного прогона анализа чувствительности.
 *
 * @property parameterName имя варьируемого параметра.
 * @property variant вариант значения: "LOW" или "HIGH".
 * @property value использованное числовое значение параметра.
 * @property roleDistribution распределение ролей (роль -> количество) в этом прогоне.
 * @property meanRisk средний риск по авторам в этом прогоне.
 * @property anomalyCount число обнаруженных аномалий в этом прогоне.
 */
data class SensitivityRunResult(
    val parameterName: String,
    val variant: String, // "LOW" or "HIGH"
    val value: Double,
    val roleDistribution: Map<String, Int>,
    val meanRisk: Double,
    val anomalyCount: Int,
)

/**
 * Полный отчёт анализа чувствительности: базовая линия и все прогоны.
 *
 * @property baselineRoleDistribution распределение ролей при значениях по умолчанию.
 * @property baselineMeanRisk средний риск базового прогона.
 * @property baselineAnomalyCount число аномалий базового прогона.
 * @property runs результаты всех прогонов с варьированием параметров.
 * @property highImpactParameters имена параметров, отнесённых к высоковлияющим.
 */
data class SensitivityReport(
    val baselineRoleDistribution: Map<String, Int>,
    val baselineMeanRisk: Double,
    val baselineAnomalyCount: Int,
    val runs: List<SensitivityRunResult>,
    val highImpactParameters: List<String>,
)

/** Расчётная часть анализа чувствительности: статистика, классификация, отчёт. */
object SensitivityHarness {
    /**
     * Считает статистику хи-квадрат между наблюдаемым и ожидаемым (базовым)
     * распределениями ролей. Ожидаемые частоты масштабируются под общий объём
     * наблюдений, чтобы сравнивать распределения разного размера.
     *
     * @return сумма по ролям (obs - exp)^2 / exp.
     */
    fun chiSquareRoleDist(
        observed: Map<String, Int>,
        expected: Map<String, Int>,
    ): Double {
        // Объединённое множество ролей из обоих распределений
        val allRoles = (observed.keys + expected.keys).distinct()
        // Суммарные объёмы (не меньше 1, чтобы избежать деления на ноль)
        val totalObs = observed.values.sum().toDouble().coerceAtLeast(1.0)
        val totalExp = expected.values.sum().toDouble().coerceAtLeast(1.0)

        // Накапливаем вклад каждой роли в статистику хи-квадрат
        return allRoles.sumOf { role ->
            val obs = (observed[role] ?: 0).toDouble()
            // Ожидаемую долю роли масштабируем до объёма наблюдений
            val exp = ((expected[role] ?: 0).toDouble() / totalExp) * totalObs
            if (exp > 0) (obs - exp) * (obs - exp) / exp else 0.0
        }
    }

    /**
     * Классифицирует влияние параметра как HIGH/MEDIUM/LOW по статистике
     * хи-квадрат и модулю изменения среднего риска.
     *
     * @param anomalyDelta изменение числа аномалий (в текущей логике не используется).
     */
    fun classifyImpact(
        chiSquare: Double,
        riskDelta: Double,
        @Suppress("unused") anomalyDelta: Int,
    ): String = when {
        // Сильное смещение распределения ролей или риска — высокое влияние
        chiSquare > 10 || kotlin.math.abs(riskDelta) > 0.2 -> "HIGH"
        // Умеренное смещение — среднее влияние
        chiSquare > 5 || kotlin.math.abs(riskDelta) > 0.1 -> "MEDIUM"
        else -> "LOW"
    }

    /**
     * Формирует markdown-отчёт по результатам прогонов: базовая линия, таблица
     * влияния по каждому прогону и список высоковлияющих параметров.
     */
    fun generateReport(report: SensitivityReport): String {
        val sb = StringBuilder()
        sb.appendLine("# Sensitivity Analysis Report")
        sb.appendLine()
        sb.appendLine("## Baseline")
        sb.appendLine("- Roles: ${report.baselineRoleDistribution}")
        sb.appendLine("- Mean Risk: ${"%.4f".format(report.baselineMeanRisk)}")
        sb.appendLine("- Anomalies: ${report.baselineAnomalyCount}")
        sb.appendLine()
        sb.appendLine("## Parameter Impact Summary")
        sb.appendLine()
        sb.appendLine("| Parameter | Variant | Value | Chi² | Risk Δ | Impact |")
        sb.appendLine("|-----------|---------|-------|------|--------|--------|")

        // По каждому прогону считаем отклонения от базовой линии и класс влияния
        for (run in report.runs) {
            // Сдвиг распределения ролей относительно базового
            val chi = chiSquareRoleDist(run.roleDistribution, report.baselineRoleDistribution)
            // Изменение среднего риска относительно базового
            val riskDelta = run.meanRisk - report.baselineMeanRisk
            val impact = classifyImpact(chi, riskDelta, run.anomalyCount - report.baselineAnomalyCount)
            val row = "| ${run.parameterName} | ${run.variant} " +
                "| ${"%.3f".format(run.value)} | ${"%.2f".format(chi)} " +
                "| ${"%.4f".format(riskDelta)} | $impact |"
            sb.appendLine(row)
        }

        sb.appendLine()
        sb.appendLine("## High-Impact Parameters")
        for (p in report.highImpactParameters) {
            sb.appendLine("- $p")
        }

        return sb.toString()
    }
}
