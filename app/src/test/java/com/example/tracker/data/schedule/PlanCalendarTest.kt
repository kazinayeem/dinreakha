package com.example.tracker.data.schedule

import com.example.tracker.data.schedule.Schedule.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PlanCalendarTest {

    @org.junit.Before fun resetPlan() { PlanCalendar.config = ScheduleConfig.DEFAULT }

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)
    private fun dt(m: Int, day: Int, h: Int, min: Int) = LocalDateTime.of(2026, m, day, h, min)

    // ---- days & phases ----

    @Test fun dayNumbers() {
        assertEquals(1, PlanCalendar.dayNumber(d(11, 18)))
        assertEquals(30, PlanCalendar.dayNumber(d(12, 17)))
        assertEquals(29, PlanCalendar.dayNumber(d(12, 16)))
        assertEquals(0, PlanCalendar.dayNumber(d(11, 17)))
        assertEquals(d(12, 17), PlanCalendar.planEnd)
    }

    @Test fun phaseBoundaries() {
        assertEquals(1, PlanCalendar.phaseFor(d(11, 24)).number) // day 7
        assertEquals(2, PlanCalendar.phaseFor(d(11, 25)).number) // day 8
        assertEquals(2, PlanCalendar.phaseFor(d(12, 1)).number)  // day 14
        assertEquals(3, PlanCalendar.phaseFor(d(12, 2)).number)  // day 15
        assertEquals(4, PlanCalendar.phaseFor(d(12, 9)).number)  // day 22
        assertEquals(4, PlanCalendar.phaseFor(d(12, 17)).number) // day 30
    }

    @Test fun phaseClampsOutsidePlan() {
        assertEquals(1, PlanCalendar.phaseFor(d(11, 1)).number)
        assertEquals(4, PlanCalendar.phaseFor(d(12, 31)).number)
    }

    @Test fun weeks() {
        assertEquals(1, PlanCalendar.weekNumber(d(11, 24)))
        assertEquals(2, PlanCalendar.weekNumber(d(11, 25)))
        assertEquals(5, PlanCalendar.weekNumber(d(12, 17)))
        assertEquals(5, PlanCalendar.WEEK_COUNT)
        assertEquals(listOf(d(12, 16), d(12, 17)), PlanCalendar.weekDates(5))
        assertTrue(PlanCalendar.isReviewDay(d(11, 24)))
        assertFalse(PlanCalendar.isReviewDay(d(11, 25)))
    }

    // ---- wake ----

    @Test fun wakeUsesPhaseTimeOnNormalDays() {
        assertEquals(t(8, 0), PlanCalendar.wakeTime(d(11, 19)))  // Thu, phase 1
        assertEquals(t(7, 30), PlanCalendar.wakeTime(d(11, 26))) // Thu, phase 2
        assertEquals(t(7, 0), PlanCalendar.wakeTime(d(12, 3)))   // Thu, phase 3
        assertEquals(t(6, 30), PlanCalendar.wakeTime(d(12, 10))) // Thu, phase 4
    }

    @Test fun wednesdayOverrideAlwaysEarlier() {
        assertEquals(t(6, 0), PlanCalendar.wakeTime(d(11, 18))) // Day 1
        assertEquals(t(6, 0), PlanCalendar.wakeTime(d(12, 9)))  // phase 4 still 06:00 < 06:30
    }

    @Test fun weekendOverrideOnlyIfEarlier() {
        assertEquals(t(7, 30), PlanCalendar.wakeTime(d(11, 21))) // Sat phase 1: 07:30 < 08:00
        assertEquals(t(7, 30), PlanCalendar.wakeTime(d(11, 22))) // Sun phase 1
        assertEquals(t(7, 30), PlanCalendar.wakeTime(d(11, 28))) // Sat phase 2: equal
        assertEquals(t(7, 0), PlanCalendar.wakeTime(d(12, 5)))   // Sat phase 3: phase is earlier
        assertEquals(t(6, 30), PlanCalendar.wakeTime(d(12, 13))) // Sun phase 4
    }

    @Test fun holidaySkipsOverride() {
        assertTrue(PlanCalendar.isHoliday(d(12, 16)))
        assertEquals("Victory Day", PlanCalendar.holidayName(d(12, 16)))
        assertEquals(t(6, 30), PlanCalendar.wakeTime(d(12, 16))) // Wed, but no 06:00 override
    }

    // ---- bed ----

    @Test fun bedtimeAfterMidnightInPhase1() {
        // Thu night → Fri wake 08:00: min(00:30, 01:00) = 00:30 Fri
        assertEquals(dt(11, 20, 0, 30), PlanCalendar.bedDateTime(d(11, 19)))
        // Fri night → Sat wake 07:30: min(00:30, 00:30)
        assertEquals(dt(11, 21, 0, 30), PlanCalendar.bedDateTime(d(11, 20)))
    }

    @Test fun bedtimeCappedBySevenHoursBeforeNextWake() {
        // Tonight (day 0) → Day 1 Wed 06:00 → bed 23:00
        assertEquals(dt(11, 17, 23, 0), PlanCalendar.bedDateTime(d(11, 17)))
        // Day 7 Tue → Day 8 Wed 06:00 → 23:00 instead of 00:30
        assertEquals(dt(11, 24, 23, 0), PlanCalendar.bedDateTime(d(11, 24)))
        // Day 21 Tue (phase 3 bed 23:30) → Wed 06:00 → 23:00
        assertEquals(dt(12, 8, 23, 0), PlanCalendar.bedDateTime(d(12, 8)))
    }

    @Test fun bedtimeBeforeHolidayUsesPhaseBed() {
        // Day 28 Tue → holiday Wed 06:30 → cap 23:30, phase 23:00 wins
        assertEquals(dt(12, 15, 23, 0), PlanCalendar.bedDateTime(d(12, 15)))
    }

    @Test fun phase2MidnightBedtime() {
        // Day 9 Thu → Fri 07:30 → min(00:00 Fri, 00:30 Fri) = 00:00 Fri
        assertEquals(dt(11, 27, 0, 0), PlanCalendar.bedDateTime(d(11, 26)))
    }

    @Test fun screensOffIsAnHourBeforeBed() {
        assertEquals(dt(11, 17, 22, 0), PlanCalendar.screensOffDateTime(d(11, 17)))
    }

    @Test fun quietTime() {
        assertTrue(PlanCalendar.isQuietTime(dt(11, 18, 2, 0)))
        assertTrue(PlanCalendar.isQuietTime(dt(11, 17, 23, 30)))
        assertFalse(PlanCalendar.isQuietTime(dt(11, 17, 23, 0)))  // exactly bedtime is allowed
        assertFalse(PlanCalendar.isQuietTime(dt(11, 18, 6, 0)))   // exactly wake time
        assertFalse(PlanCalendar.isQuietTime(dt(11, 18, 12, 0)))
    }

    // ---- focus blocks & checklist ----

    @Test fun oneFocusBlockExpectedInFirstWeek() {
        val blocks = PlanCalendar.focusBlocks(d(11, 18)) // Wed: study + research
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].expected)  // study
        assertFalse(blocks[1].expected) // research is optional
    }

    @Test fun fullPlanFromDay15() {
        val blocks = PlanCalendar.focusBlocks(d(12, 8)) // Tue day 21: study, research, code
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it.expected })
    }

    @Test fun checklistPrayersFollowGoal() {
        val day1 = PlanCalendar.checklist(d(11, 18)).filter { it.expected }.map { it.key }
        assertTrue(Habits.prayerKey(Prayer.MAGHRIB) in day1)
        assertFalse(Habits.prayerKey(Prayer.ISHA) in day1)
        val day22 = PlanCalendar.checklist(d(12, 9)).filter { it.expected }.map { it.key }
        assertTrue(Prayer.entries.all { Habits.prayerKey(it) in day22 })
    }

    @Test fun completionCountsOnlyExpected() {
        // Thu day 2: woke, morning, study, walk, nap, screens, maghrib + attended Statistics = 8
        val date = d(11, 19)
        val expected = PlanCalendar.checklist(date).filter { it.expected }
        assertEquals(8, expected.size)
        val done = setOf(Habits.WOKE_ON_TIME, Habits.CODE_PROJECT /* optional, ignored */)
        assertEquals(1f / 8f, PlanCalendar.completion(date, done), 0.0001f)
    }

    @Test fun holidayTimelineHasNoClassesOrBuses() {
        val types = PlanCalendar.timeline(d(12, 16), emptyMap()).map { it.type }.toSet()
        assertFalse(PlanCalendar.EventType.CLASS in types)
        assertFalse(PlanCalendar.EventType.BUS_TO_CAMPUS in types)
        val normalWed = PlanCalendar.timeline(d(12, 9), emptyMap()).map { it.type }.toSet()
        assertTrue(PlanCalendar.EventType.CLASS in normalWed)
    }

    @Test fun timelineIsSorted() {
        val tl = PlanCalendar.timeline(d(11, 21), emptyMap())
        assertEquals(tl.sortedBy { it.start }, tl)
        // Fajr (05:05) comes before the 07:30 Saturday wake-up.
        assertEquals(PlanCalendar.EventType.PRAYER, tl.first().type)
        assertEquals(1, tl.count { it.type == PlanCalendar.EventType.WAKE })
        assertNull(tl.first { it.type == PlanCalendar.EventType.WAKE }.end)
        assertEquals(PlanCalendar.EventType.BEDTIME, tl.last().type)
    }

    // ---- v3: classes & routines on the checklist ----

    @Test fun classesAreTickableOnClassDaysOnly() {
        val sat = PlanCalendar.checklist(d(11, 21)).map { it.key }
        assertTrue(Habits.classKey(LocalTime.of(10, 0)) in sat)
        assertTrue(Habits.classKey(LocalTime.of(14, 30)) in sat)
        assertTrue(PlanCalendar.checklist(d(11, 20)).none { it.key.startsWith("class_") }) // Friday
        assertTrue(PlanCalendar.checklist(d(12, 16)).none { it.key.startsWith("class_") }) // holiday
    }

    @Test fun routinesRepeatDailyWeeklyMonthly() {
        val daily = Schedule.Routine(id = 1, title = "Quran", repeat = Schedule.Repeat.DAILY)
        val weekly = Schedule.Routine(id = 2, title = "Laundry", repeat = Schedule.Repeat.WEEKLY, days = setOf(java.time.DayOfWeek.FRIDAY))
        val monthly = Schedule.Routine(id = 3, title = "Bill", repeat = Schedule.Repeat.MONTHLY, dayOfMonth = 31)
        val off = Schedule.Routine(id = 4, title = "Paused", active = false)
        PlanCalendar.config = ScheduleConfig.DEFAULT.copy(routines = listOf(daily, weekly, monthly, off))
        val fri = PlanCalendar.checklist(d(11, 20)).map { it.key }
        assertTrue("routine_1" in fri && "routine_2" in fri && "routine_3" !in fri && "routine_4" !in fri)
        assertTrue("routine_3" in PlanCalendar.checklist(d(11, 30)).map { it.key }) // 31 → 30 Nov
        assertTrue("routine_2" !in PlanCalendar.checklist(d(11, 21)).map { it.key })
    }

    @Test fun editedPlanStartAndPhases() {
        val base = ScheduleConfig.DEFAULT
        PlanCalendar.config = base.copy(planStart = d(12, 1), planDays = 10,
            wakeOverrides = emptyMap())
        assertEquals(1, PlanCalendar.dayNumber(d(12, 1)))
        assertEquals(d(12, 10), PlanCalendar.planEnd)
        assertEquals(t(8, 0), PlanCalendar.wakeTime(d(12, 2))) // Wed without override → phase 1 time
    }
}
