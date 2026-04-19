package com.example.lomanalyzer.analysis.risk

import com.example.lomanalyzer.analysis.anomaly.AnomalyDetection

/**
 * Produces draft recommendations per v6 §20.4.
 * All recommendations tagged [ЧЕРНОВИК].
 */
object RecommendationEngine {
    fun recommend(category: String, anomalies: List<AnomalyDetection>): String {
        val types = anomalies.map { it.type }.distinct()
        val parts = mutableListOf<String>()

        if ("VOLUME_SPIKE" in types) {
            parts.add("Всплеск публикационной активности — проверить источник")
        }
        if ("TONE_SHIFT_NEGATIVE" in types) {
            parts.add("Негативный сдвиг тональности — оценить контент")
        }
        if ("TONE_SHIFT_POSITIVE" in types) {
            parts.add("Позитивный сдвиг тональности — возможна кампания")
        }
        if ("GIANT_ACTIVATION" in types) {
            parts.add("Активация спящего гиганта — повышенное внимание")
        }

        val prefix = when (category) {
            "HIGH" -> "[ЧЕРНОВИК] ВЫСОКИЙ РИСК. "
            "MEDIUM" -> "[ЧЕРНОВИК] СРЕДНИЙ РИСК. "
            "LOW" -> "[ЧЕРНОВИК] НИЗКИЙ РИСК. "
            else -> "[ЧЕРНОВИК] "
        }

        return prefix + parts.joinToString("; ")
    }
}
