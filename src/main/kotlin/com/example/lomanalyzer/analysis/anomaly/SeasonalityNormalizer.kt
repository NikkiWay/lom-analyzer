package com.example.lomanalyzer.analysis.anomaly

import java.time.DayOfWeek
import java.time.LocalDate

data class DailyValue(val date: LocalDate, val value: Double)

/**
 * Full seasonality normalizer per v6 §19.2.
 * Weekly pattern: x_wd = mean(x_d | weekday, NOT holiday).
 * Holiday mean: x_holiday = mean(x_d | isHoliday) over baseline+current.
 * Holiday days normalized by x_holiday if >= 3 holiday observations
 * and x_holiday >= x_min_seas; otherwise flag and skip z-score.
 */
class SeasonalityNormalizer(
    private val calendar: HolidayCalendar,
    private val minHolidayObs: Int = 3,
    private val minSeasonalValue: Double = 0.1,
) {
    data class NormalizedSeries(
        val values: List<DailyValue>,
        val seasonalityDisabled: Boolean,
        val disableReason: String?,
        val holidayAnomalyDisabled: Boolean = false,
        val skippedHolidayDates: List<LocalDate> = emptyList(),
    )

    fun normalize(series: List<DailyValue>, baselineDays: Int): NormalizedSeries {
        if (baselineDays < 30) {
            return NormalizedSeries(series, true, "SEASONALITY_DISABLED_SHORT_BASELINE")
        }

        val nonHoliday = series.filter { !calendar.isHoliday(it.date) }
        val holidayDays = series.filter { calendar.isHoliday(it.date) }
        val weekdayMeans = computeWeekdayMeans(nonHoliday)
        val holidayMean = computeHolidayMean(holidayDays)

        val canNormalizeHolidays = holidayDays.size >= minHolidayObs &&
            (holidayMean ?: 0.0) >= minSeasonalValue

        val skippedDates = mutableListOf<LocalDate>()

        val normalized = series.map { dv ->
            if (calendar.isHoliday(dv.date)) {
                if (canNormalizeHolidays) {
                    DailyValue(dv.date, dv.value / holidayMean!!)
                } else {
                    skippedDates.add(dv.date)
                    dv // Skip normalization
                }
            } else {
                val wd = dv.date.dayOfWeek
                val mean = weekdayMeans[wd] ?: 1.0
                if (mean > 0) DailyValue(dv.date, dv.value / mean) else dv
            }
        }

        return NormalizedSeries(
            values = normalized,
            seasonalityDisabled = false,
            disableReason = null,
            holidayAnomalyDisabled = !canNormalizeHolidays && holidayDays.isNotEmpty(),
            skippedHolidayDates = skippedDates,
        )
    }

    private fun computeWeekdayMeans(values: List<DailyValue>): Map<DayOfWeek, Double> =
        values.groupBy { it.date.dayOfWeek }
            .mapValues { (_, dvs) -> dvs.map { it.value }.average() }

    private fun computeHolidayMean(holidays: List<DailyValue>): Double? =
        if (holidays.isEmpty()) null else holidays.map { it.value }.average()
}
