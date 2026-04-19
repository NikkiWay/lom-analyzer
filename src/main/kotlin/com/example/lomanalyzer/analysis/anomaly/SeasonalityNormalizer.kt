package com.example.lomanalyzer.analysis.anomaly

import java.time.DayOfWeek
import java.time.LocalDate

data class DailyValue(val date: LocalDate, val value: Double)

/**
 * Weekly pattern only in MVP: x_wd = mean(x_d | weekday(d) == wd, NOT holiday).
 * Divide x_d by x_wd if weekday. Holiday days left unnormalized in MVP.
 * Full holiday-mean support comes in Prompt 23.
 */
class SeasonalityNormalizer(
    private val calendar: HolidayCalendar,
) {
    data class NormalizedSeries(
        val values: List<DailyValue>,
        val seasonalityDisabled: Boolean,
        val disableReason: String?,
    )

    fun normalize(series: List<DailyValue>, baselineDays: Int): NormalizedSeries {
        if (baselineDays < 30) {
            return NormalizedSeries(series, true, "SEASONALITY_DISABLED_SHORT_BASELINE")
        }

        val nonHoliday = series.filter { !calendar.isHoliday(it.date) }
        val weekdayMeans = computeWeekdayMeans(nonHoliday)

        val normalized = series.map { dv ->
            if (calendar.isHoliday(dv.date)) {
                dv // Leave holiday days unnormalized in MVP
            } else {
                val wd = dv.date.dayOfWeek
                val mean = weekdayMeans[wd] ?: 1.0
                if (mean > 0) DailyValue(dv.date, dv.value / mean)
                else dv
            }
        }

        return NormalizedSeries(normalized, false, null)
    }

    private fun computeWeekdayMeans(values: List<DailyValue>): Map<DayOfWeek, Double> =
        values.groupBy { it.date.dayOfWeek }
            .mapValues { (_, dvs) -> dvs.map { it.value }.average() }
}
