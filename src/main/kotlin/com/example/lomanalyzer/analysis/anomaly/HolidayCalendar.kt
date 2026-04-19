package com.example.lomanalyzer.analysis.anomaly

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class HolidayEntry(val date: String, val name: String)

@Serializable
data class HolidaysData(
    val version: String = "",
    val holidays: List<HolidayEntry> = emptyList(),
)

data class HolidayInfo(val isHoliday: Boolean, val name: String?)

class HolidayCalendar {
    private val holidays: Map<LocalDate, String> by lazy { loadHolidays() }

    fun check(date: LocalDate): HolidayInfo {
        val name = holidays[date]
        return HolidayInfo(name != null, name)
    }

    fun isHoliday(date: LocalDate): Boolean = date in holidays

    private fun loadHolidays(): Map<LocalDate, String> {
        val stream = HolidayCalendar::class.java
            .getResourceAsStream("/resources/holidays.json")
            ?: return emptyMap()

        val json = Json { ignoreUnknownKeys = true }
        val data = json.decodeFromString<HolidaysData>(stream.bufferedReader().readText())
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE

        return data.holidays.associate { entry ->
            LocalDate.parse(entry.date, fmt) to entry.name
        }
    }
}
