package com.example.lomanalyzer.analysis.anomaly

import java.time.LocalDate

data class AnomalyDetection(
    val type: String,
    val dayDate: LocalDate,
    val severity: Double,
    val zScore: Double,
    val description: String,
    val isHolidayDay: Boolean = false,
    val routineProtectionApplied: Boolean = false,
)

/**
 * Detect z > 2.5; severity = clamp(z/6.0, 0, 1); max over days.
 */
class VolumeSpikeDetector(
    private val zThreshold: Double = 2.5,
    private val calendar: HolidayCalendar,
) {
    fun detect(
        dailyVolumes: List<DailyValue>,
        zScores: List<ZScorePoint>,
    ): List<AnomalyDetection> {
        val anomalies = mutableListOf<AnomalyDetection>()

        for ((i, zp) in zScores.withIndex()) {
            if (zp.zScore > zThreshold && i < dailyVolumes.size) {
                val date = dailyVolumes[i].date
                val severity = (zp.zScore / 6.0).coerceIn(0.0, 1.0)
                anomalies.add(AnomalyDetection(
                    type = "VOLUME_SPIKE",
                    dayDate = date,
                    severity = severity,
                    zScore = zp.zScore,
                    description = "Volume spike: z=%.2f".format(zp.zScore),
                    isHolidayDay = calendar.isHoliday(date),
                ))
            }
        }

        return anomalies
    }
}
