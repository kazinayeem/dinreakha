package com.example.tracker.data.sleep

import com.example.tracker.data.db.DayLog
import com.example.tracker.data.db.SleepLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SleepMathTest {
    private val night = LocalDate.of(2026, 11, 20)

    @Test fun defaultNight_morningMeansLastNight_eveningMeansTonight() {
        assertEquals(night, SleepMath.defaultNight(LocalDateTime.of(2026, 11, 21, 7, 30)))
        assertEquals(night, SleepMath.defaultNight(LocalDateTime.of(2026, 11, 20, 22, 0)))
    }

    @Test fun bedBeforeMidnight_staysOnThatEvening() {
        val (bed, wake) = SleepMath.toDateTimes(night, LocalTime.of(23, 30), LocalTime.of(7, 0))
        assertEquals(LocalDateTime.of(2026, 11, 20, 23, 30), bed)
        assertEquals(LocalDateTime.of(2026, 11, 21, 7, 0), wake)
        assertEquals(450, SleepMath.minutesBetween(bed, wake))
    }

    @Test fun bedAfterMidnight_movesToNextDay() {
        val (bed, wake) = SleepMath.toDateTimes(night, LocalTime.of(0, 45), LocalTime.of(8, 0))
        assertEquals(LocalDateTime.of(2026, 11, 21, 0, 45), bed)
        assertEquals(435, SleepMath.minutesBetween(bed, wake))
    }

    @Test fun summary_averagesBedtimesAcrossMidnight() {
        val logs = listOf(
            SleepLogEntity(night, LocalDateTime.of(2026, 11, 20, 23, 30), LocalDateTime.of(2026, 11, 21, 7, 30), 4),
            SleepLogEntity(night.minusDays(1), LocalDateTime.of(2026, 11, 20, 0, 30), LocalDateTime.of(2026, 11, 20, 7, 30), 2),
        )
        val s = SleepMath.summary(logs, night.plusDays(1)) { it.atTime(23, 45) }
        assertEquals(2, s.nights)
        assertEquals(LocalTime.of(0, 0), s.avgBed) // 23:30 and 00:30 average to midnight, not noon
        assertEquals(450L, s.avgMinutes)
        assertEquals(1, s.onTarget)
        assertEquals(3.0, s.avgQuality!!, 0.001)
    }

    @Test fun summary_emptyIsSafe() {
        val s = SleepMath.summary(emptyList(), night) { it.atTime(23, 0) }
        assertEquals(0, s.nights)
        assertNull(s.avgBed)
    }

    @Test fun formatDuration() = assertEquals("7h 05m", SleepMath.formatDuration(425))
}

class MoodCheckTest {
    private val today = LocalDate.of(2026, 11, 25)
    private fun log(daysAgo: Long, mood: Int?) = DayLog(today.minusDays(daysAgo), mood)

    @Test fun threeLowDays_trigger() =
        assertTrue(MoodCheck.lowStreak(listOf(log(0, 1), log(1, 2), log(2, 2)), today))

    @Test fun oneGoodDay_resets() =
        assertFalse(MoodCheck.lowStreak(listOf(log(0, 1), log(1, 4), log(2, 2), log(3, 1)), today))

    @Test fun twoLowDays_notEnough() =
        assertFalse(MoodCheck.lowStreak(listOf(log(0, 1), log(1, 2), log(2, null)), today))

    @Test fun oldLowDays_ignored() =
        assertFalse(MoodCheck.lowStreak(listOf(log(5, 1), log(6, 1), log(7, 1)), today))

    @Test fun average_lastWeekOnly() =
        assertEquals(4.0, MoodCheck.average(listOf(log(0, 5), log(3, 3), log(10, 1)), today)!!, 0.001)
}
