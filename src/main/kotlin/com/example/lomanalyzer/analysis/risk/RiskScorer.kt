package com.example.lomanalyzer.analysis.risk

import com.example.lomanalyzer.analysis.anomaly.AnomalyDetection

/**
 * Corrected formula from v6 §20.1 with coefficient 1.2:
 * R_dom = max severity across all anomalies.
 * S_total = sum of all severities.
 * R_multi = 1 + 1.2 * (1 - R_dom / (S_total + 0.001)) * 1[n_active >= 2]
 * R = clamp(R_dom * R_multi, 0, 1)
 */
object RiskScorer {
    private const val MULTI_COEFF = 1.2
    private const val EPSILON = 0.001

    fun computeRisk(anomalies: List<AnomalyDetection>): RiskResult {
        if (anomalies.isEmpty()) {
            return RiskResult(0.0, 0.0, 0, emptyMap())
        }

        val rDom = anomalies.maxOf { it.severity }
        val sTotal = anomalies.sumOf { it.severity }
        val nActive = anomalies.map { it.type }.distinct().size

        val rMulti = if (nActive >= 2) {
            1.0 + MULTI_COEFF * (1.0 - rDom / (sTotal + EPSILON))
        } else {
            1.0
        }

        val risk = (rDom * rMulti).coerceIn(0.0, 1.0)

        val decomposition = anomalies.groupBy { it.type }
            .mapValues { (_, group) -> group.maxOf { it.severity } }

        return RiskResult(risk, rMulti, nActive, decomposition)
    }
}

data class RiskResult(
    val riskScore: Double,
    val rMulti: Double,
    val activeTypes: Int,
    val decomposition: Map<String, Double>,
)
