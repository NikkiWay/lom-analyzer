package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.HolidayDayStatsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class HolidayDayStatsDao(private val db: Database) {
    fun insert(
        sessionId: Int,
        date: Long,
        isHoliday: Boolean,
        holidayName: String? = null,
        volumeObserved: Int = 0,
        toneMeanObserved: Float? = null,
        postCount: Int = 0,
    ): Int = transaction(db) {
        HolidayDayStatsTable.insertAndGetId {
            it[HolidayDayStatsTable.sessionId] = sessionId
            it[HolidayDayStatsTable.date] = date
            it[HolidayDayStatsTable.isHoliday] = isHoliday
            it[HolidayDayStatsTable.holidayName] = holidayName
            it[HolidayDayStatsTable.volumeObserved] = volumeObserved
            it[HolidayDayStatsTable.toneMeanObserved] = toneMeanObserved
            it[HolidayDayStatsTable.postCount] = postCount
        }.value
    }

    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        HolidayDayStatsTable.selectAll()
            .where { HolidayDayStatsTable.sessionId eq sessionId }
            .orderBy(HolidayDayStatsTable.date)
            .toList()
    }
}
