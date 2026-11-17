package com.example.tracker.data.calendar

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Freq { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Repeat rule, stored as an iCalendar RRULE string ("FREQ=WEEKLY;INTERVAL=1;BYDAY=SA,MO;UNTIL=20261217;COUNT=10")
 * so .ics export/import is lossless. Follows RFC 5545 where it matters:
 *  - a monthly rule on the 31st skips months without a 31st (it does not move to the 30th),
 *  - a yearly rule on 29 Feb only happens in leap years,
 *  - COUNT counts generated occurrences (including ones later skipped).
 */
data class Recurrence(
    val freq: Freq = Freq.NONE,
    val interval: Int = 1,
    /** WEEKLY only; empty = the start date's weekday. */
    val byDays: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
    val count: Int? = null,
) {
    val repeats get() = freq != Freq.NONE

    fun toRrule(): String? {
        if (freq == Freq.NONE) return null
        val parts = mutableListOf("FREQ=${freq.name}")
        if (interval > 1) parts += "INTERVAL=$interval"
        if (freq == Freq.WEEKLY && byDays.isNotEmpty()) parts += "BYDAY=" + byDays.sortedBy { it.value }.joinToString(",") { DAY_CODES.getValue(it) }
        until?.let { parts += "UNTIL=" + it.format(BASIC_DATE) }
        count?.let { parts += "COUNT=$it" }
        return parts.joinToString(";")
    }

    /** Plain-language summary for the UI, e.g. "Weekly on Sat, Mon until 17 Dec". */
    fun describe(): String {
        if (freq == Freq.NONE) return "Does not repeat"
        val every = when (freq) {
            Freq.DAILY -> if (interval == 1) "Every day" else "Every $interval days"
            Freq.WEEKLY -> (if (interval == 1) "Weekly" else "Every $interval weeks") +
                (if (byDays.isNotEmpty()) " on " + byDays.sortedBy { (it.value + 1) % 7 }.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) } else "")
            Freq.MONTHLY -> if (interval == 1) "Monthly" else "Every $interval months"
            Freq.YEARLY -> if (interval == 1) "Yearly" else "Every $interval years"
            Freq.NONE -> ""
        }
        val end = when {
            until != null -> " until ${until.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
            count != null -> ", $count times"
            else -> ""
        }
        return every + end
    }

    companion object {
        val NONE = Recurrence()
        private val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val DAY_CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )

        fun parse(rrule: String?): Recurrence {
            if (rrule.isNullOrBlank()) return NONE
            val m = rrule.removePrefix("RRULE:").split(";").mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()
            val freq = runCatching { Freq.valueOf(m["FREQ"]!!.uppercase()) }.getOrDefault(Freq.NONE)
            val until = m["UNTIL"]?.take(8)?.let { runCatching { LocalDate.parse(it, BASIC_DATE) }.getOrNull() }
            val days = m["BYDAY"]?.split(",")?.mapNotNull { code -> DAY_CODES.entries.firstOrNull { it.value == code.takeLast(2).uppercase() }?.key }?.toSet() ?: emptySet()
            return Recurrence(freq, m["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1, days, until, m["COUNT"]?.toIntOrNull())
        }
    }
}

/** A calendar event you created. Times are Asia/Dhaka local. All-day events use 00:00 start, end = next day 00:00. */
data class CalEvent(
    val id: Long = 0,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val location: String = "",
    val notes: String = "",
    val color: Long = 0xFF4FA8E8,
    val recurrence: Recurrence = Recurrence.NONE,
    /** Minutes before start, e.g. [0, 60, 1440]. */
    val reminders: List<Int> = emptyList(),
) {
    val duration: Duration get() = Duration.between(start, end).let { if (it.isNegative) Duration.ZERO else it }
}

/** One occurrence changed or skipped ("edit this event only" / "skip this one"). */
data class EventException(
    val eventId: Long,
    val originalDate: LocalDate,
    val cancelled: Boolean = false,
    val title: String? = null,
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    val location: String? = null,
    val notes: String? = null,
)

data class Occurrence(
    val event: CalEvent,
    /** Date the rule produced (identifies the occurrence even if it was moved). */
    val originalDate: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val title: String,
    val location: String,
    val notes: String,
    val modified: Boolean,
) {
    val key get() = "${event.id}_$originalDate"
    val allDay get() = event.allDay
    /** Last calendar date this occurrence covers (an end at exactly 00:00 belongs to the day before). */
    val lastDate: LocalDate get() {
        val s = start.toLocalDate()
        val e = if (end.toLocalTime() == java.time.LocalTime.MIDNIGHT && end.isAfter(start)) end.toLocalDate().minusDays(1) else end.toLocalDate()
        return maxOf(s, e)
    }

    /** Does this occurrence touch [date]? (multi-day and all-day aware) */
    fun touches(date: LocalDate): Boolean = !date.isBefore(start.toLocalDate()) && !date.isAfter(lastDate)
}

object Recurrences {
    private const val MAX_STEPS = 20_000

    /** Dates (in order) the rule generates, from the event's start date, stopping after [limit]. */
    fun generatedDates(ev: CalEvent, limit: LocalDate): Sequence<LocalDate> = sequence {
        val r = ev.recurrence
        val first = ev.start.toLocalDate()
        var produced = 0
        fun stop(d: LocalDate) = d.isAfter(limit) || (r.until != null && d.isAfter(r.until)) || (r.count != null && produced >= r.count)
        when (r.freq) {
            Freq.NONE -> if (!first.isAfter(limit)) yield(first)
            Freq.DAILY -> {
                var d = first; var i = 0
                while (!stop(d) && i++ < MAX_STEPS) { yield(d); produced++; d = d.plusDays(r.interval.toLong()) }
            }
            Freq.WEEKLY -> {
                val days = r.byDays.ifEmpty { setOf(first.dayOfWeek) }.sortedBy { it.value }
                var weekStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); var i = 0
                outer@ while (i++ < MAX_STEPS) {
                    for (dow in days) {
                        val d = weekStart.plusDays((dow.value - 1).toLong())
                        if (d.isBefore(first)) continue
                        if (stop(d)) break@outer
                        yield(d); produced++
                    }
                    weekStart = weekStart.plusWeeks(r.interval.toLong())
                }
            }
            Freq.MONTHLY -> {
                val dom = first.dayOfMonth; var k = 0L; var i = 0
                while (i++ < MAX_STEPS) {
                    val ym = YearMonth.from(first).plusMonths(k * r.interval); k++
                    if (dom > ym.lengthOfMonth()) { if (ym.atDay(1).isAfter(limit)) break else continue } // RFC: skip
                    val d = ym.atDay(dom)
                    if (stop(d)) break
                    yield(d); produced++
                }
            }
            Freq.YEARLY -> {
                var k = 0L; var i = 0
                while (i++ < MAX_STEPS) {
                    val y = first.year + k * r.interval; k++
                    val ym = YearMonth.of(y.toInt(), first.month)
                    if (first.dayOfMonth > ym.lengthOfMonth()) { if (ym.atDay(1).isAfter(limit)) break else continue }
                    val d = ym.atDay(first.dayOfMonth)
                    if (stop(d)) break
                    yield(d); produced++
                }
            }
        }
    }

    /** All occurrences overlapping [from]..[to] (inclusive dates), with exceptions applied, sorted by start. */
    fun expand(ev: CalEvent, exceptions: List<EventException>, from: LocalDate, to: LocalDate): List<Occurrence> {
        val byDate = exceptions.filter { it.eventId == ev.id }.associateBy { it.originalDate }
        val spanDays = ChronoUnit.DAYS.between(ev.start.toLocalDate(), ev.end.toLocalDate()).coerceAtLeast(0)
        val earliest = from.minusDays(spanDays + 1)
        return generatedDates(ev, to.plusDays(1)) // +1: an exception may move an occurrence into range
            .filter { !it.isBefore(earliest) }
            .mapNotNull { d ->
                val ex = byDate[d]
                if (ex?.cancelled == true) return@mapNotNull null
                val baseStart = d.atTime(ev.start.toLocalTime())
                val start = ex?.start ?: baseStart
                val end = ex?.end ?: start.plus(ev.duration)
                Occurrence(ev, d, start, end, ex?.title ?: ev.title, ex?.location ?: ev.location, ex?.notes ?: ev.notes, ex != null)
            }
            .filter { o -> !o.start.toLocalDate().isAfter(to) && !o.lastDate.isBefore(from) }
            .sortedBy { it.start }
            .toList()
    }

    fun expandAll(events: List<CalEvent>, exceptions: List<EventException>, from: LocalDate, to: LocalDate): List<Occurrence> =
        events.flatMap { expand(it, exceptions, from, to) }.sortedBy { it.start }

    /** The next [Occurrence]-based reminder times after [now], as (occurrence, minutesBefore, fireAt). */
    fun upcomingReminders(events: List<CalEvent>, exceptions: List<EventException>, now: LocalDateTime, days: Long = 14):
        List<Triple<Occurrence, Int, LocalDateTime>> =
        expandAll(events.filter { it.reminders.isNotEmpty() }, exceptions, now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(days))
            .flatMap { o -> o.event.reminders.map { m -> Triple(o, m, o.start.minusMinutes(m.toLong())) } }
            .filter { it.third.isAfter(now) }
            .sortedBy { it.third }
}
