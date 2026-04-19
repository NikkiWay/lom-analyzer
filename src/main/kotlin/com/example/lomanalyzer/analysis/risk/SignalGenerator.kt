package com.example.lomanalyzer.analysis.risk

import com.example.lomanalyzer.analysis.anomaly.AnomalyDetection

data class RiskSignalData(
    val riskScore: Double,
    val ciLo: Double,
    val ciHi: Double,
    val isBorderline: Boolean,
    val category: String,
    val description: String,
    val recommendation: String,
    val decomposition: Map<String, Double>,
)

object SignalGenerator {
    fun generateSignal(
        riskCi: RiskCiResult,
        riskResult: RiskResult,
        anomalies: List<AnomalyDetection>,
    ): RiskSignalData {
        val category = categorize(riskCi.riskScore)
        val description = buildDescription(anomalies, riskResult)
        val recommendation = RecommendationEngine.recommend(category, anomalies)

        return RiskSignalData(
            riskScore = riskCi.riskScore,
            ciLo = riskCi.ciLo,
            ciHi = riskCi.ciHi,
            isBorderline = riskCi.isBorderline,
            category = category,
            description = description,
            recommendation = recommendation,
            decomposition = riskResult.decomposition,
        )
    }

    private fun categorize(score: Double): String = when {
        score >= 0.55 -> "HIGH"
        score >= 0.35 -> "MEDIUM"
        score >= 0.15 -> "LOW"
        else -> "MINIMAL"
    }

    private fun buildDescription(
        anomalies: List<AnomalyDetection>,
        risk: RiskResult,
    ): String {
        val types = anomalies.map { it.type }.distinct().joinToString(", ")
        return "Risk score %.3f (multi=%.2f). Active types: %s".format(
            risk.riskScore, risk.rMulti, types,
        )
    }
}
