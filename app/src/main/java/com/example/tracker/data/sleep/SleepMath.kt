package com.example.tracker.data.sleep

import com.example.tracker.data.db.DayLog
import com.example.tracker.data.db.SleepLogEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure sleep / mood logic — no Android, unit-tested. */
object SleepMath {

    /** Before 3 PM you're logging last night; after that, tonight. */
    fun defaultNight(now: LocalDateTime): LocalDate =
        if (now.hour < 15) now.toLocalDate().minusDays(1) else now.toLocalDate()

    /**
     * Turns two clock times into real date-times for the night of [night].
     * Bed at 18:00–23:59 is that evening; 00:00–17:59 is after midnight. Wake is always after bed.
     */
    fun toDateTimes(night: LocalDate, bed: LocalTime, wake: LocalTime): Pair<LocalDateTime, LocalDateTime> {
        val bedAt = if (bed.hour >= 18) night.atTime(bed) else night.plusDays(1).atTime(bed)
        var wakeAt = night.plusDays(1).atTime(wake)
        if (!wakeAt.isAfter(bedAt)) wakeAt = wakeAt.plusDays(1)
        return bedAt to wakeAt
    }

    /** Minutes after 18:00 — lets bedtimes either side of midnight be averaged. */
    private fun eveningMinutes(t: LocalDateTime): Int = ((t.hour * 60 + t.minute) - 18 * 60).mod(24 * 60)

    data class Summary(
        val nights: Int,
        val avgMinutes: Long,
        val avgBed: LocalTime?,
        val onTarget: Int,
        val avgQuality: Double?,
    )

    /**
     * Last [days] nights ending with the night before [today].
     * "On target" = in bed no later than [targetBed](night) + 15 minutes.
     */
    fun summary(logs: List<SleepLogEntity>, today: LocalDate, days: Int = 7, targetBed: (LocalDate) -> LocalDateTime): Summary {
        val from = today.minusDays(days.toLong())
        val recent = logs.filter { !it.night.isBefore(from) && it.night.isBefore(today) }
        if (recent.isEmpty()) return Summary(0, 0, null, 0, null)
        val avgEvening = recent.map { eveningMinutes(it.bedAt) }.average().toInt()
        val avgBed = LocalTime.of(18, 0).plusMinutes(avgEvening.toLong())
        val onTarget = recent.count { !it.bedAt.isAfter(targetBed(it.night).plusMinutes(15)) }
        val q = recent.mapNotNull { it.quality }
        return Summary(recent.size, recent.map { it.minutes }.average().toLong(), avgBed, onTarget, q.takeIf { it.isNotEmpty() }?.average())
    }

    fun formatDuration(minutes: Long): String = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

    fun minutesBetween(a: LocalDateTime, b: LocalDateTime): Long = Duration.between(a, b).toMinutes()
}

object MoodCheck {
    /**
     * Gentle check-in trigger: at least 3 moods logged in the last 4 days (today included)
     * and every one of them is Low (1) or Meh (2). One good day resets it.
     */
    fun lowStreak(logs: List<DayLog>, today: LocalDate): Boolean {
        val recent = logs.filter { it.mood != null && !it.date.isAfter(today) && it.date.isAfter(today.minusDays(4)) }
        return recent.size >= 3 && recent.all { it.mood!! <= 2 }
    }

    fun average(logs: List<DayLog>, today: LocalDate, days: Int = 7): Double? =
        logs.filter { it.mood != null && !it.date.isAfter(today) && it.date.isAfter(today.minusDays(days.toLong())) }
            .mapNotNull { it.mood }.takeIf { it.isNotEmpty() }?.average()
}
