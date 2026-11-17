package com.example.tracker.data.backup

import com.example.tracker.data.calendar.CalEvent
import com.example.tracker.data.calendar.Recurrence
import com.example.tracker.data.calendar.Recurrences
import com.example.tracker.data.repo.CalendarData
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.PlanCalendar.EventType
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.ScheduleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ResetLogicTest {

    @Test
    fun emptyScheduleProducesBlankTimelineAndNoResidualClasses() {
        // Given an empty schedule (as created by resetToBlankApp)
        val blankConfig = ScheduleConfig(
            planStart = LocalDate.of(2026, 11, 18),
            planDays = 30,
            holidays = emptyMap(),
            phases = listOf(
                Schedule.Phase(
                    number = 1,
                    days = 1..30,
                    wake = LocalTime.of(7, 0),
                    bed = LocalTime.of(23, 0),
                    prayers = emptySet(),
                    expectedFocusBlocks = 0,
                )
            ),
            wakeOverrides = emptyMap(),
            classes = emptyList(),
            buses = emptyList(),
            blocks = emptyList(),
            screensOffLeadMin = 30L,
            routines = emptyList(),
        )

        PlanCalendar.config = blankConfig

        val day = LocalDate.of(2026, 11, 18)
        val timeline = PlanCalendar.timeline(day, emptyMap())

        // Classes, buses, routines, and blocks must be completely empty!
        assertTrue(timeline.none { it.type == EventType.CLASS })
        assertTrue(timeline.none { it.type == EventType.BUS_TO_CAMPUS })
        assertTrue(timeline.none { it.type == EventType.BUS_HOME })
        assertTrue(timeline.none { it.type == EventType.ROUTINE })
        assertTrue(timeline.none { it.type == EventType.FOCUS })

        // No holidays
        assertFalse(PlanCalendar.isHoliday(day))
    }

    @Test
    fun emptyPhasesFallbackDoesNotCrash() {
        val configWithEmptyPhases = ScheduleConfig(
            planStart = LocalDate.of(2026, 11, 18),
            planDays = 30,
            holidays = emptyMap(),
            phases = emptyList(),
            wakeOverrides = emptyMap(),
            classes = emptyList(),
            buses = emptyList(),
            blocks = emptyList(),
            screensOffLeadMin = 30L,
            routines = emptyList(),
        )

        // Must not throw NoSuchElementException!
        val phase = configWithEmptyPhases.phaseForDay(1)
        assertNotNull(phase)
        assertEquals(LocalTime.of(7, 0), phase.wake)
        assertEquals(LocalTime.of(23, 0), phase.bed)
        assertTrue(phase.prayers.isEmpty())
    }

    @Test
    fun recurrenceExpansionOnEmptyCalendarReturnsEmpty() {
        val events = emptyList<CalEvent>()
        val exceptions = emptyList<com.example.tracker.data.calendar.EventException>()
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 30)

        val occurrences = Recurrences.expandAll(events, exceptions, from, to)
        assertTrue(occurrences.isEmpty())
    }
}
