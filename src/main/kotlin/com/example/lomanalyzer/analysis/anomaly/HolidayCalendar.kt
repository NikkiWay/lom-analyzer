package com.example.lomanalyzer.analysis.anomaly

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class HolidayEntry(val date: String, val name: String)

@Serializable
data class HolidaysData(
    val version: String = "",
    @SerialName("valid_from") val validFrom: String = "",
    @SerialName("valid_to") val validTo: String = "",
    val holidays: List<HolidayEntry> = emptyList(),
)

data class HolidayInfo(val isHoliday: Boolean, val name: String?)

/**
 * Multi-year holiday calendar per v6 §19.2.3.
 * Loads holiday files from classpath; selects files whose [valid_from, valid_to]
 * intersects the session window.
 */
class HolidayCalendar {
    private val json = Json { ignoreUnknownKeys = true }
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    private var holidays: Map<LocalDate, String> = emptyMap()
    var partialCoverage: Boolean = false
        private set

    init {
        holidays = loadDefaultHolidays()
    }

    fun check(date: LocalDate): HolidayInfo {
        val name = holidays[date]
        return HolidayInfo(name != null, name)
    }

    fun isHoliday(date: LocalDate): Boolean = date in holidays

    /**
     * Load holidays for a specific session window.
     * Selects all year files whose validity intersects [start, end].
     */
    @Suppress("NestedBlockDepth")
    fun loadForSession(sessionStart: LocalDate, sessionEnd: LocalDate) {
        val allHolidays = mutableMapOf<LocalDate, String>()
        var anyCoverage = false

        val years = (sessionStart.year..sessionEnd.year)
        for (year in years) {
            val data = loadYearFile(year)
            if (data != null) {
                anyCoverage = true
                for (entry in data.holidays) {
                    val date = LocalDate.parse(entry.date, fmt)
                    if (!date.isBefore(sessionStart) && !date.isAfter(sessionEnd)) {
                        allHolidays[date] = entry.name
                    }
                }
            }
        }

        // Also load the default file as fallback
        val defaultHolidays = loadDefaultHolidays()
        for ((date, name) in defaultHolidays) {
            if (!date.isBefore(sessionStart) && !date.isAfter(sessionEnd)) {
                allHolidays.putIfAbsent(date, name)
            }
        }

        holidays = allHolidays
        partialCoverage = !anyCoverage || years.any { loadYearFile(it) == null }
    }

    private fun loadYearFile(year: Int): HolidaysData? {
        val path = "/resources/holidays/v${year}.json"
        val stream = HolidayCalendar::class.java.getResourceAsStream(path) ?: return null
        return json.decodeFromString<HolidaysData>(stream.bufferedReader().readText())
    }

    private fun loadDefaultHolidays(): Map<LocalDate, String> {
        val stream = HolidayCalendar::class.java
            .getResourceAsStream("/resources/holidays.json")
            ?: return emptyMap()
        val data = json.decodeFromString<HolidaysData>(stream.bufferedReader().readText())
        return data.holidays.associate { LocalDate.parse(it.date, fmt) to it.name }
    }
}
