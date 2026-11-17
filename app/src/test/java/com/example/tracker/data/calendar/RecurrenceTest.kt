package com.example.tracker.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun dt(m: Int, day: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, m, day, h, min)
    private fun ev(start: LocalDateTime, r: Recurrence, id: Long = 1, hours: Long = 1) =
        CalEvent(id, "E", start, start.plusHours(hours), recurrence = r)
    private fun dates(e: CalEvent, from: LocalDate, to: LocalDate, ex: List<EventException> = emptyList()) =
        Recurrences.expand(e, ex, from, to).map { it.start.toLocalDate() }

    @Test fun singleEvent() {
        val e = ev(dt(11, 20, 10), Recurrence.NONE)
        assertEquals(listOf(d(2026, 11, 20)), dates(e, d(2026, 11, 1), d(2026, 11, 30)))
        assertTrue(dates(e, d(2026, 12, 1), d(2026, 12, 31)).isEmpty())
    }

    @Test fun dailyWithIntervalAndCount() {
        val e = ev(dt(11, 18, 7), Recurrence(Freq.DAILY, interval = 2, count = 3))
        assertEquals(listOf(d(2026, 11, 18), d(2026, 11, 20), d(2026, 11, 22)), dates(e, d(2026, 11, 1), d(2026, 12, 31)))
    }

    @Test fun weeklyOnChosenDaysUntil() {
        // starts Wed 18 Nov; Sat + Mon + Wed until Mon 30 Nov
        val e = ev(dt(11, 18, 18), Recurrence(Freq.WEEKLY, byDays = setOf(SATURDAY, MONDAY, WEDNESDAY), until = d(2026, 11, 30)))
        assertEquals(listOf(d(2026, 11, 18), d(2026, 11, 21), d(2026, 11, 23), d(2026, 11, 25), d(2026, 11, 28), d(2026, 11, 30)),
            dates(e, d(2026, 11, 1), d(2026, 12, 31)))
    }

    @Test fun weeklyDefaultsToStartDay() {
        val e = ev(dt(11, 20, 14), Recurrence(Freq.WEEKLY, count = 2)) // Friday
        assertEquals(listOf(d(2026, 11, 20), d(2026, 11, 27)), dates(e, d(2026, 11, 1), d(2026, 12, 31)))
    }

    @Test fun monthlyOn31stSkipsShortMonths() {
        val e = ev(LocalDateTime.of(2026, 10, 31, 9, 0), Recurrence(Freq.MONTHLY))
        assertEquals(listOf(d(2026, 10, 31), d(2026, 12, 31), d(2027, 1, 31), d(2027, 3, 31)),
            dates(e, d(2026, 10, 1), d(2027, 4, 15)))
    }

    @Test fun yearlyLeapDayOnlyInLeapYears() {
        val e = ev(LocalDateTime.of(2028, 2, 29, 8, 0), Recurrence(Freq.YEARLY))
        assertEquals(listOf(d(2028, 2, 29), d(2032, 2, 29)), dates(e, d(2028, 1, 1), d(2033, 1, 1)))
    }

    @Test fun skipAndMoveOneOccurrence() {
        val e = ev(dt(11, 18, 18), Recurrence(Freq.DAILY, count = 4))
        val ex = listOf(
            EventException(1, d(2026, 11, 19), cancelled = true),
            EventException(1, d(2026, 11, 20), start = dt(11, 20, 21), end = dt(11, 20, 22), title = "Moved"),
        )
        val occ = Recurrences.expand(e, ex, d(2026, 11, 1), d(2026, 11, 30))
        assertEquals(listOf(d(2026, 11, 18), d(2026, 11, 20), d(2026, 11, 21)), occ.map { it.start.toLocalDate() })
        val moved = occ.first { it.originalDate == d(2026, 11, 20) }
        assertEquals(dt(11, 20, 21), moved.start)
        assertEquals("Moved", moved.title)
        assertTrue(moved.modified)
        // COUNT still counts the skipped one (RFC 5545): 4 generated, 1 skipped → 3 shown
        assertEquals(3, occ.size)
    }

    @Test fun multiDayEventTouchesEveryDay() {
        val e = CalEvent(1, "Trip", dt(11, 20, 18), dt(11, 22, 10))
        val o = Recurrences.expand(e, emptyList(), d(2026, 11, 21), d(2026, 11, 21)).single()
        assertTrue(o.touches(d(2026, 11, 21)))
        assertFalse(o.touches(d(2026, 11, 23)))
        // all-day ending at next midnight covers only its own day
        val allDay = CalEvent(2, "Holiday", dt(12, 16, 0), dt(12, 17, 0), allDay = true)
        assertFalse(Recurrences.expand(allDay, emptyList(), d(2026, 12, 16), d(2026, 12, 16)).single().touches(d(2026, 12, 17)))
    }

    @Test fun rruleRoundTrip() {
        val r = Recurrence(Freq.WEEKLY, 2, setOf(SATURDAY, MONDAY), until = d(2026, 12, 17))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,SA;UNTIL=20261217", r.toRrule())
        assertEquals(r, Recurrence.parse(r.toRrule()))
        assertEquals(Recurrence.NONE, Recurrence.parse(null))
    }

    @Test fun remindersAreNextOccurrences() {
        val e = CalEvent(1, "Gym", dt(11, 18, 18), dt(11, 18, 19), recurrence = Recurrence(Freq.DAILY), reminders = listOf(0, 60))
        val r = Recurrences.upcomingReminders(listOf(e), emptyList(), dt(11, 18, 17, 30), days = 1)
        assertEquals(dt(11, 18, 18), r.first().third) // 17:00 already passed → next is "at time" 18:00
        assertEquals(dt(11, 19, 17), r[1].third)
    }

    @Test fun icsRoundTrip() {
        val e = CalEvent(7, "Study group, KT-604; bring notes", dt(11, 21, 15), dt(11, 21, 16, 30), location = "Library",
            notes = "Line 1\nLine 2", recurrence = Recurrence(Freq.WEEKLY, byDays = setOf(SATURDAY), count = 4), reminders = listOf(15, 1440))
        val ex = listOf(EventException(7, d(2026, 11, 28), cancelled = true),
            EventException(7, d(2026, 12, 5), start = dt(12, 5, 17), end = dt(12, 5, 18), title = "Moved"))
        val text = Ics.write(listOf(e), ex)
        assertTrue(text.contains("RRULE:FREQ=WEEKLY;BYDAY=SA;COUNT=4"))
        val p = Ics.parse(text).single()
        assertEquals(e.copy(id = 0), p.event)
        assertEquals(setOf(d(2026, 11, 28), d(2026, 12, 5)), p.exceptions.map { it.originalDate }.toSet())
        assertTrue(p.exceptions.first { it.originalDate == d(2026, 11, 28) }.cancelled)
        assertEquals(dt(12, 5, 17), p.exceptions.first { !it.cancelled }.start)
    }

    @Test fun icsParsesUtcAndAllDay() {
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Exam
            DTSTART:20261201T030000Z
            DTEND:20261201T050000Z
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Victory Day
            DTSTART;VALUE=DATE:20261216
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val ps = Ics.parse(text)
        assertEquals(dt(12, 1, 9), ps[0].event.start) // 03:00Z = 09:00 Dhaka
        assertTrue(ps[1].event.allDay)
        assertEquals(dt(12, 17, 0), ps[1].event.end)
    }
}
