package com.example.lomanalyzer.analysis.anomaly

/**
 * Suppress TONE_SHIFT when explained by GIANT_ACTIVATION (§19.5 ABSORBED_BY_GIANT).
 * A TONE_SHIFT is suppressed if a GIANT_ACTIVATION occurs on the same day
 * and has higher severity.
 */
object AnomalyDeduplicator {
    fun deduplicate(anomalies: List<AnomalyDetection>): List<AnomalyDetection> {
        val giants = anomalies
            .filter { it.type == "GIANT_ACTIVATION" }
            .associateBy { it.dayDate }

        return anomalies.filter { anomaly ->
            if (anomaly.type.startsWith("TONE_SHIFT")) {
                val giant = giants[anomaly.dayDate]
                giant == null || giant.severity <= anomaly.severity
            } else {
                true
            }
        }
    }
}
