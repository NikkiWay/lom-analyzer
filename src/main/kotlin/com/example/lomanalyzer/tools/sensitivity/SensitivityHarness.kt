package com.example.lomanalyzer.tools.sensitivity

/**
 * Sensitivity analysis harness per v6 §25.5.
 * Runs the pipeline with each parameter at low/high ends
 * and computes impact metrics.
 */
data class SensitivityRunResult(
    val parameterName: String,
    val variant: String, // "LOW" or "HIGH"
    val value: Double,
    val roleDistribution: Map<String, Int>,
    val meanRisk: Double,
    val anomalyCount: Int,
)

data class SensitivityReport(
    val baselineRoleDistribution: Map<String, Int>,
    val baselineMeanRisk: Double,
    val baselineAnomalyCount: Int,
    val runs: List<SensitivityRunResult>,
    val highImpactParameters: List<String>,
)

object SensitivityHarness {
    /**
     * Compute chi-square statistic comparing observed vs expected role distribution.
     */
    fun chiSquareRoleDist(
        observed: Map<String, Int>,
        expected: Map<String, Int>,
    ): Double {
        val allRoles = (observed.keys + expected.keys).distinct()
        val totalObs = observed.values.sum().toDouble().coerceAtLeast(1.0)
        val totalExp = expected.values.sum().toDouble().coerceAtLeast(1.0)

        return allRoles.sumOf { role ->
            val obs = (observed[role] ?: 0).toDouble()
            val exp = ((expected[role] ?: 0).toDouble() / totalExp) * totalObs
            if (exp > 0) (obs - exp) * (obs - exp) / exp else 0.0
        }
    }

    /**
     * Classify parameter impact as HIGH/MEDIUM/LOW based on chi-square
     * and risk delta.
     */
    fun classifyImpact(
        chiSquare: Double,
        riskDelta: Double,
        @Suppress("unused") anomalyDelta: Int,
    ): String = when {
        chiSquare > 10 || kotlin.math.abs(riskDelta) > 0.2 -> "HIGH"
        chiSquare > 5 || kotlin.math.abs(riskDelta) > 0.1 -> "MEDIUM"
        else -> "LOW"
    }

    /**
     * Generate markdown report from sensitivity runs.
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

        for (run in report.runs) {
            val chi = chiSquareRoleDist(run.roleDistribution, report.baselineRoleDistribution)
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
