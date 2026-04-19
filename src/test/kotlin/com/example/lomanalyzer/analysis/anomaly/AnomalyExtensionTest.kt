package com.example.lomanalyzer.analysis.anomaly

import com.example.lomanalyzer.analysis.content.ExperimentalFrameClassifier
import com.example.lomanalyzer.analysis.content.Frame
import com.example.lomanalyzer.analysis.content.FrameValidator
import com.example.lomanalyzer.analysis.lom.CombinedRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AnomalyExtensionTest {

    // --- SeasonalityNormalizer: separate holiday mean ---

    @Test
    fun `holiday mean is separate from weekly mean`() {
        val cal = HolidayCalendar()
        val norm = SeasonalityNormalizer(cal, minHolidayObs = 2)

        // Create series with holidays (Jan 1, Jan 7 2025) and weekdays
        val series = listOf(
            DailyValue(LocalDate.of(2025, 1, 1), 50.0),  // holiday
            DailyValue(LocalDate.of(2025, 1, 2), 100.0),  // weekday Thu
            DailyValue(LocalDate.of(2025, 1, 3), 100.0),  // weekday Fri
            DailyValue(LocalDate.of(2025, 1, 6), 100.0),  // weekday Mon
            DailyValue(LocalDate.of(2025, 1, 7), 40.0),   // holiday
            DailyValue(LocalDate.of(2025, 1, 8), 100.0),  // holiday (per our calendar)
        )

        val result = norm.normalize(series, baselineDays = 30)
        assertFalse(result.seasonalityDisabled)

        // Holiday days should be normalized by holiday mean, not weekday mean
        val jan1 = result.values.first { it.date == LocalDate.of(2025, 1, 1) }
        val jan2 = result.values.first { it.date == LocalDate.of(2025, 1, 2) }

        // Jan 1 (holiday, value=50) normalized by holiday mean
        // Jan 2 (weekday, value=100) normalized by weekday mean
        assertNotEquals(jan1.value, jan2.value, 0.001,
            "Holiday and weekday should use different means")
    }

    @Test
    fun `insufficient holiday observations flags and skips`() {
        val cal = HolidayCalendar()
        val norm = SeasonalityNormalizer(cal, minHolidayObs = 5) // Need 5 holidays

        val series = listOf(
            DailyValue(LocalDate.of(2025, 1, 1), 50.0),  // holiday
            DailyValue(LocalDate.of(2025, 1, 2), 100.0),
            DailyValue(LocalDate.of(2025, 1, 3), 100.0),
        )

        val result = norm.normalize(series, baselineDays = 30)
        assertTrue(result.holidayAnomalyDisabled)
        assertTrue(result.skippedHolidayDates.isNotEmpty())
    }

    @Test
    fun `short baseline disables seasonality`() {
        val cal = HolidayCalendar()
        val norm = SeasonalityNormalizer(cal)
        val series = listOf(DailyValue(LocalDate.of(2025, 6, 1), 10.0))
        val result = norm.normalize(series, baselineDays = 20)
        assertTrue(result.seasonalityDisabled)
    }

    // --- GiantActivationDetector: routine protection ---

    @Test
    fun `routine protection suppresses with 2+ baseline topical posts`() {
        val candidates = listOf(
            GiantCandidate(
                1, CombinedRole.SLEEPING_GIANT_CONFIRMED, 0.8, 5000, 0.7,
                LocalDate.now(), nTopicBaseline = 3, baselineDays = 60,
            ),
        )
        val results = GiantActivationDetector.detect(candidates)
        assertEquals(1, results.size)
        assertEquals(0.0, results[0].severity)
        assertTrue(results[0].routineProtectionApplied)
    }

    @Test
    fun `1 baseline topical post applies 0_8 multiplier`() {
        val candidates = listOf(
            GiantCandidate(
                1, CombinedRole.SLEEPING_GIANT_CONFIRMED, 0.8, 5000, 0.7,
                LocalDate.now(), nTopicBaseline = 1, baselineDays = 60,
            ),
        )
        val results = GiantActivationDetector.detect(candidates)
        assertTrue(results[0].severity > 0)
        assertTrue(results[0].description.contains("routine"))
    }

    @Test
    fun `0 baseline topical posts gets full severity`() {
        val candidates = listOf(
            GiantCandidate(
                1, CombinedRole.SLEEPING_GIANT_CONFIRMED, 0.8, 5000, 0.7,
                LocalDate.now(), nTopicBaseline = 0, baselineDays = 60,
            ),
        )
        val results = GiantActivationDetector.detect(candidates)
        assertTrue(results[0].severity > 0)
        assertFalse(results[0].description.contains("routine"))
    }

    @Test
    fun `short baseline does not trigger routine protection`() {
        val candidates = listOf(
            GiantCandidate(
                1, CombinedRole.SLEEPING_GIANT_CONFIRMED, 0.8, 5000, 0.7,
                LocalDate.now(), nTopicBaseline = 5, baselineDays = 30,
            ),
        )
        val results = GiantActivationDetector.detect(candidates)
        assertTrue(results[0].severity > 0, "Short baseline should not suppress")
    }

    // --- HolidayCalendar multi-year ---

    @Test
    fun `default calendar covers 2025-2026`() {
        val cal = HolidayCalendar()
        assertTrue(cal.isHoliday(LocalDate.of(2025, 1, 1)))
        assertTrue(cal.isHoliday(LocalDate.of(2026, 5, 9)))
        assertFalse(cal.isHoliday(LocalDate.of(2025, 3, 15)))
    }

    @Test
    fun `loadForSession sets partial coverage for unknown years`() {
        val cal = HolidayCalendar()
        cal.loadForSession(
            LocalDate.of(2030, 1, 1),
            LocalDate.of(2030, 12, 31),
        )
        assertTrue(cal.partialCoverage)
    }

    @Test
    fun `loadForSession covers known years`() {
        val cal = HolidayCalendar()
        cal.loadForSession(
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31),
        )
        assertTrue(cal.isHoliday(LocalDate.of(2025, 5, 9)))
    }

    // --- ExperimentalFrameClassifier ---

    @Test
    fun `frame classifier detects dominant frame`() {
        val lemmas = listOf("угроза", "опасность", "риск", "город", "день")
        val results = ExperimentalFrameClassifier.classify(lemmas)
        assertTrue(results.isNotEmpty())
        assertEquals(Frame.THREAT, results[0].frame)
    }

    @Test
    fun `frame classifier returns empty for unrelated text`() {
        val lemmas = listOf("стол", "стул", "окно")
        val results = ExperimentalFrameClassifier.classify(lemmas)
        assertTrue(results.isEmpty())
    }

    // --- Frame validation gating ---

    @Test
    fun `frame validation passes at 50pct accuracy`() {
        val n = 40
        val predictions = (0 until n).map {
            if (it < 20) Frame.THREAT else Frame.OPPORTUNITY
        }
        val truth = (0 until n).map {
            if (it < 20) Frame.THREAT else Frame.OPPORTUNITY
        }
        val result = FrameValidator.validate(predictions, truth)
        assertTrue(result.passedThreshold)
        assertTrue(result.accuracy >= 0.5)
    }

    @Test
    fun `frame validation fails below 50pct accuracy`() {
        val n = 40
        val predictions = (0 until n).map { Frame.THREAT }
        val truth = (0 until n).map {
            Frame.entries[it % Frame.entries.size]
        }
        val result = FrameValidator.validate(predictions, truth)
        // Only 1/6 match → ~17% accuracy
        assertFalse(result.passedThreshold)
    }

    @Test
    fun `frame validation bootstrap CI is valid`() {
        val n = 50
        val predictions = (0 until n).map {
            if (it < 35) Frame.THREAT else Frame.OPPORTUNITY
        }
        val truth = (0 until n).map {
            if (it < 30) Frame.THREAT else Frame.OPPORTUNITY
        }
        val result = FrameValidator.validate(predictions, truth)
        assertTrue(result.f1CiLo <= result.macroF1)
        assertTrue(result.f1CiHi >= result.macroF1)
        assertEquals(n, result.sampleSize)
    }
}
