package com.example.tracker.data.quotes

import com.example.tracker.data.repo.QuoteCategory
import com.example.tracker.data.repo.QuoteLanguage
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.ReminderCategory
import com.example.tracker.data.schedule.ReminderConfig
import com.example.tracker.data.schedule.ReminderPlanner
import com.example.tracker.data.schedule.ScheduleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuoteLibraryTest {
    @Before fun resetPlan() { PlanCalendar.config = ScheduleConfig.DEFAULT }

    @Test fun libraryHasAtLeast500Quotes() =
        assertTrue("only ${MotivationQuotes.ALL_QUOTES.size}", MotivationQuotes.ALL_QUOTES.size >= 500)

    @Test fun idsAreUnique() =
        assertEquals(MotivationQuotes.ALL_QUOTES.size, MotivationQuotes.ALL_QUOTES.map { it.id }.toSet().size)

    @Test fun everyQuoteHasTextAndSource() =
        assertTrue(MotivationQuotes.ALL_QUOTES.all { it.text.isNotBlank() && it.source.isNotBlank() })

    @Test fun bothLanguagesAndCategoriesPresent() {
        val all = MotivationQuotes.ALL_QUOTES
        assertTrue(all.count { it.category == QuoteCategory.ISLAMIC && it.language == QuoteLanguage.BANGLA } >= 100)
        assertTrue(all.count { it.category == QuoteCategory.PRODUCTIVITY && it.language == QuoteLanguage.ENGLISH } >= 100)
    }

    @Test fun pickIsStableAndFiltered() {
        val a = MotivationQuotes.pick(20_000, 1, QuoteCategory.ISLAMIC, QuoteLanguage.BANGLA)
        assertEquals(a, MotivationQuotes.pick(20_000, 1, QuoteCategory.ISLAMIC, QuoteLanguage.BANGLA))
        assertEquals(QuoteCategory.ISLAMIC, a.category)
        assertEquals(QuoteLanguage.BANGLA, a.language)
    }

    @Test fun noRepeatUntilTheListWraps() {
        val n = MotivationQuotes.matching(QuoteCategory.ALL, QuoteLanguage.ENGLISH).size
        val seen = (0 until n).map { k -> MotivationQuotes.pick(k / 3L, k % 3, QuoteCategory.ALL, QuoteLanguage.ENGLISH).id }
        assertEquals(n, seen.toSet().size)
    }

    @Test fun threeMotivationRemindersADay_notInCountdown() {
        val day = LocalDate.of(2026, 11, 20)
        val cfg = ReminderConfig()
        val m = ReminderPlanner.candidatesFor(day, cfg).filter { it.category == ReminderCategory.MOTIVATION }
        assertEquals(3, m.size)
        assertEquals(PlanCalendar.wakeDateTime(day).plusMinutes(15), m[0].at)
        assertEquals(LocalTime.of(13, 30), m[1].at.toLocalTime())
        assertNotEquals(m[1].text, m[2].text)
        val next = ReminderPlanner.nextOverall(day.atTime(13, 0), cfg)
        assertNotEquals(ReminderCategory.MOTIVATION, next?.category)
    }
}
