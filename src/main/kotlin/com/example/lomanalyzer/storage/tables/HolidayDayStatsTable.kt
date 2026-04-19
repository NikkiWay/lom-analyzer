package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object HolidayDayStatsTable : IntIdTable("holiday_day_stats") {
    val sessionId = reference("session_id", AnalysisSessions)
    val date = long("date")
    val isHoliday = bool("is_holiday").default(false)
    val holidayName = text("holiday_name").nullable()
    val volumeObserved = integer("volume_observed").default(0)
    val toneMeanObserved = float("tone_mean_observed").nullable()
    val postCount = integer("post_count").default(0)
}
