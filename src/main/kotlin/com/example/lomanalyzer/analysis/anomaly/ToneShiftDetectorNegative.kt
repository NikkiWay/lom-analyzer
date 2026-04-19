package com.example.lomanalyzer.analysis.anomaly

import java.time.LocalDate
import kotlin.math.abs

/**
 * Detect negative tone shift: z < -2.0, requires >= 3 posts/day.
 * Severity = clamp(|z|/5.0, 0, 1).
 */
class ToneShiftDetectorNegative(
    private val zThreshold: Double = 2.0,
    private val minPostsPerDay: Int = 3,
    private val calendar: HolidayCalendar,
) {
    fun detect(
        dailyTones: List<DailyValue>,
        dailyPostCounts: List<Int>,
        zScores: List<ZScorePoint>,
    ): List<AnomalyDetection> {
        val anomalies = mutableListOf<AnomalyDetection>()

        for ((i, zp) in zScores.withIndex()) {
            val eligible = i < dailyTones.size && i < dailyPostCounts.size &&
                zp.zScore < -zThreshold && dailyPostCounts[i] >= minPostsPerDay
            if (eligible) {
                val date = dailyTones[i].date
                val severity = (abs(zp.zScore) / 5.0).coerceIn(0.0, 1.0)
                anomalies.add(AnomalyDetection(
                    type = "TONE_SHIFT_NEGATIVE",
                    dayDate = date,
                    severity = severity,
                    zScore = zp.zScore,
                    description = "Negative tone shift: z=%.2f".format(zp.zScore),
                    isHolidayDay = calendar.isHoliday(date),
                ))
            }
        }
        return anomalies
    }
}
