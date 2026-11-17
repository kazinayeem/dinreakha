package com.example.tracker.data.schedule

import com.example.tracker.data.schedule.Schedule.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderPlannerTest {

    @org.junit.Before fun resetPlan() { PlanCalendar.config = ScheduleConfig.DEFAULT }

    private val cfg = ReminderConfig()
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private fun dt(m: Int, day: Int, h: Int, min: Int) = LocalDateTime.of(2026, m, day, h, min)
    private fun slots(date: LocalDate, c: ReminderConfig = cfg) =
        ReminderPlanner.remindersFor(date, c).associateBy { it.slot }

    @Test fun busReminderThirtyMinutesBefore() {
        val r = slots(d(11, 18))[ReminderPlanner.SLOT_BUS]!!
        assertEquals(dt(11, 18, 6, 30), r.at)
        assertEquals("Leave now for the 07:00 bus", r.title)
    }

    @Test fun noBusOnHolidayOrFriday() {
        assertNull(slots(d(12, 16))[ReminderPlanner.SLOT_BUS]) // Victory Day (Wed)
        assertNull(slots(d(11, 20))[ReminderPlanner.SLOT_BUS]) // Friday
    }

    @Test fun onlyGoalPrayersAreReminded() {
        val s = slots(d(11, 19)) // phase 1
        assertTrue(ReminderPlanner.prayerSlot(Prayer.MAGHRIB) in s)
        assertFalse(ReminderPlanner.prayerSlot(Prayer.ISHA) in s)
        assertFalse(ReminderPlanner.prayerSlot(Prayer.ZUHR) in s)
    }

    @Test fun mondayMaghribAfterLab() {
        assertEquals(dt(11, 23, 17, 35), slots(d(11, 23))[ReminderPlanner.prayerSlot(Prayer.MAGHRIB)]!!.at)
        assertEquals(dt(11, 24, 17, 15), slots(d(11, 24))[ReminderPlanner.prayerSlot(Prayer.MAGHRIB)]!!.at)
    }

    @Test fun editedPrayerTimeIsUsed() {
        val c = cfg.copy(prayerTimes = cfg.prayerTimes + (Prayer.MAGHRIB to java.time.LocalTime.of(17, 20)))
        assertEquals(dt(11, 19, 17, 20), slots(d(11, 19), c)[ReminderPlanner.prayerSlot(Prayer.MAGHRIB)]!!.at)
    }

    @Test fun fajrIsSilencedBecauseItIsBeforeWake() {
        // Phase 4 includes Fajr (05:05) but wake is 06:30 → no notifications while asleep.
        assertFalse(ReminderPlanner.prayerSlot(Prayer.FAJR) in slots(d(12, 10)))
    }

    @Test fun wakeAlarmSurvivesMuteToday() {
        val muted = cfg.copy(mutedDate = d(11, 19))
        val s = slots(d(11, 19), muted)
        assertEquals(setOf(ReminderPlanner.SLOT_WAKE), s.keys)
    }

    @Test fun togglesRemoveCategories() {
        val c = cfg.withEnabled(ReminderCategory.entries.toSet() - ReminderCategory.PRAYERS)
        assertTrue(slots(d(12, 9), c).values.none { it.category == ReminderCategory.PRAYERS })
    }

    @Test fun focusBlockFiveMinutesBeforeAndOnlyExpected() {
        val s = slots(d(11, 18)) // day 1: only course study is expected
        assertEquals(dt(11, 18, 18, 55), s[ReminderPlanner.blockSlot(Schedule.BlockType.STUDY)]!!.at)
        assertFalse(ReminderPlanner.blockSlot(Schedule.BlockType.RESEARCH) in s)
        val day15 = slots(d(12, 2)) // Wed day 15
        assertEquals(dt(12, 2, 10, 25), day15[ReminderPlanner.blockSlot(Schedule.BlockType.RESEARCH)]!!.at)
    }

    @Test fun weeklyReviewOnFriday() {
        assertEquals(dt(11, 20, 20, 0), slots(d(11, 20))[ReminderPlanner.SLOT_WEEKLY]!!.at)
        assertNull(slots(d(11, 19))[ReminderPlanner.SLOT_WEEKLY])
    }

    @Test fun nextPerSlotFromTonight() {
        val next = ReminderPlanner.nextPerSlot(dt(11, 17, 20, 0), cfg)
        assertEquals(dt(11, 17, 22, 0), next[ReminderPlanner.SLOT_SCREENS_OFF]!!.at)
        assertEquals(dt(11, 17, 23, 0), next[ReminderPlanner.SLOT_BEDTIME]!!.at)
        assertEquals(dt(11, 18, 6, 0), next[ReminderPlanner.SLOT_WAKE]!!.at)
        assertEquals(dt(11, 18, 6, 30), next[ReminderPlanner.SLOT_BUS]!!.at)
        // the 21:30 "log today's expenses" reminder is the very next one
        assertEquals(dt(11, 17, 21, 30), ReminderPlanner.nextOverall(dt(11, 17, 20, 0), cfg)!!.at)
    }

    @Test fun nextPerSlotFindsAfterMidnightBedtime() {
        // Thu 19 Nov 23:50 → bedtime belongs to Thu but fires 00:30 Fri
        val next = ReminderPlanner.nextPerSlot(dt(11, 19, 23, 50), cfg)
        val bed = next[ReminderPlanner.SLOT_BEDTIME]!!
        assertEquals(dt(11, 20, 0, 30), bed.at)
        assertEquals(d(11, 19), bed.date)
    }

    @Test fun nothingButWakeDuringSleepWindow() {
        val all = (0..30L).flatMap { ReminderPlanner.remindersFor(d(11, 17).plusDays(it), cfg) }
        all.filter { it.category != ReminderCategory.WAKE }
            .forEach { assertFalse("$it is during sleep", PlanCalendar.isQuietTime(it.at)) }
    }

    @Test fun allSlotsAreKnown() {
        val all = (0..30L).flatMap { ReminderPlanner.candidatesFor(d(11, 17).plusDays(it), cfg) }
        assertTrue(all.all { it.slot in ReminderPlanner.ALL_SLOTS })
    }

    // ---- finance reminders ----

    @Test fun dailyExpenseLogAt2130() {
        assertEquals(dt(11, 19, 21, 30), slots(d(11, 19))[ReminderPlanner.SLOT_EXPENSE_LOG]!!.at)
    }

    @Test fun recurringOnDueDateUnlessConfirmed() {
        val rent = RecurringDue(7, "Rent", "৳8,000", 31, lastConfirmedMonth = null)
        val c = cfg.copy(recurring = listOf(rent))
        // 31 → clamped to 30 Nov
        assertEquals(dt(11, 30, 10, 0), slots(d(11, 30), c)[ReminderPlanner.recurringSlot(7)]!!.at)
        assertNull(slots(d(11, 29), c)[ReminderPlanner.recurringSlot(7)])
        val paid = cfg.copy(recurring = listOf(rent.copy(lastConfirmedMonth = "2026-11")))
        assertNull(slots(d(11, 30), paid)[ReminderPlanner.recurringSlot(7)])
        assertEquals(7L, ReminderPlanner.recurringIdOf(ReminderPlanner.recurringSlot(7)))
    }

    // ---- v3: editable schedule & notification control ----

    @Test fun classReminderTenMinutesBefore() {
        // Wed 18 Nov: AI at 08:30 → 08:20, ISS at 14:30 → 14:20 (one slot, fired in order)
        val classes = ReminderPlanner.remindersFor(d(11, 18), cfg).filter { it.slot == ReminderPlanner.SLOT_CLASS }.map { it.at }
        assertEquals(listOf(dt(11, 18, 8, 20), dt(11, 18, 14, 20)), classes)
        assertEquals(dt(11, 18, 8, 20), ReminderPlanner.nextPerSlot(dt(11, 18, 7, 0), cfg)[ReminderPlanner.SLOT_CLASS]!!.at)
        assertEquals(dt(11, 18, 14, 20), ReminderPlanner.nextPerSlot(dt(11, 18, 9, 0), cfg)[ReminderPlanner.SLOT_CLASS]!!.at)
        assertNull(slots(d(12, 16))[ReminderPlanner.SLOT_CLASS]) // holiday
    }

    @Test fun customOffsetsAndFixedTimes() {
        val c = cfg.copy(prefs = mapOf(
            ReminderCategory.BUS to ReminderPref(offsetMin = 45),
            ReminderCategory.EXPENSE_LOG to ReminderPref(time = java.time.LocalTime.of(22, 0)),
        ))
        assertEquals(dt(11, 18, 6, 15), slots(d(11, 18), c)[ReminderPlanner.SLOT_BUS]!!.at)
        assertEquals(dt(11, 19, 22, 0), slots(d(11, 19), c)[ReminderPlanner.SLOT_EXPENSE_LOG]!!.at)
    }

    @Test fun masterSwitchOffMeansNothing() {
        assertTrue(ReminderPlanner.nextPerSlot(dt(11, 17, 20, 0), cfg.copy(masterEnabled = false)).isEmpty())
    }

    @Test fun holidayModePausesClassAndBus() {
        val c = cfg.copy(holidayFrom = d(11, 21), holidayTo = d(11, 23))
        assertNull(slots(d(11, 22), c)[ReminderPlanner.SLOT_BUS])
        assertNull(slots(d(11, 22), c)[ReminderPlanner.SLOT_CLASS])
        assertTrue(ReminderPlanner.SLOT_WAKE in slots(d(11, 22), c))
        assertTrue(ReminderPlanner.SLOT_BUS in slots(d(11, 25), c)) // after the range
    }

    @Test fun customQuietHours() {
        val q = QuietHours(QuietMode.CUSTOM, java.time.LocalTime.of(21, 0), java.time.LocalTime.of(7, 0))
        val c = cfg.copy(quiet = q)
        assertNull(slots(d(11, 19), c)[ReminderPlanner.SLOT_EXPENSE_LOG]) // 21:30 is inside 21:00–07:00
        assertTrue(q.isQuiet(dt(11, 19, 2, 0)))
        assertFalse(q.isQuiet(dt(11, 19, 12, 0)))
        // quiet OFF lets Fajr through
        assertTrue(ReminderPlanner.prayerSlot(Schedule.Prayer.FAJR) in slots(d(12, 10), cfg.copy(quiet = QuietHours(QuietMode.OFF))))
    }

    @Test fun editedScheduleIsUsed() {
        val base = ScheduleConfig.DEFAULT
        PlanCalendar.config = base.copy(
            buses = base.buses.map { if (it.day == java.time.DayOfWeek.WEDNESDAY && it.direction == Schedule.BusDirection.TO_CAMPUS) it.copy(time = java.time.LocalTime.of(7, 30)) else it },
            classes = base.classes.filterNot { it.day == java.time.DayOfWeek.THURSDAY },
        )
        assertEquals(dt(11, 18, 7, 0), slots(d(11, 18))[ReminderPlanner.SLOT_BUS]!!.at)
        assertNull(slots(d(11, 19))[ReminderPlanner.SLOT_CLASS])
    }

    @Test fun routineReminderOnlyOnItsDays() {
        val r = Schedule.Routine(id = 3, title = "Gym", repeat = Schedule.Repeat.WEEKLY,
            days = setOf(java.time.DayOfWeek.THURSDAY), time = java.time.LocalTime.of(18, 0), remind = true)
        PlanCalendar.config = ScheduleConfig.DEFAULT.copy(routines = listOf(r))
        assertEquals(dt(11, 19, 18, 0), slots(d(11, 19))[ReminderPlanner.routineSlot(3)]!!.at)
        assertNull(slots(d(11, 20))[ReminderPlanner.routineSlot(3)])
        assertEquals(3L, ReminderPlanner.routineIdOf(ReminderPlanner.routineSlot(3)))
    }

    @Test fun calendarRemindersRespectQuietHours() {
        val ok = Reminder("event_5_30", ReminderCategory.CALENDAR, dt(11, 19, 14, 30), "Study group", "In 30 min", d(11, 19))
        val asleep = Reminder("event_6_0", ReminderCategory.CALENDAR, dt(11, 20, 3, 0), "Late call", "Now", d(11, 20))
        val c = cfg.copy(extra = listOf(ok, asleep))
        assertEquals(dt(11, 19, 14, 30), slots(d(11, 19), c)["event_5_30"]!!.at)
        assertNull(slots(d(11, 20), c)["event_6_0"]) // 03:00 is inside the sleep window
        assertNull(slots(d(11, 19), c.withEnabled(ReminderCategory.entries.toSet() - ReminderCategory.CALENDAR))["event_5_30"])
    }
}
