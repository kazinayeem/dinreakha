package com.example.tracker.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class I18nTest {
    private fun bn(s: String, digits: Boolean = true) = I18n.translate(s, digits)

    @Test fun exactStrings() {
        assertEquals("আজ", bn("Today"))
        assertEquals("সেভ", bn("Save"))
        assertEquals("সময়মতো ওঠা", bn("WOKE ON TIME"))      // case-insensitive fallback
    }

    @Test fun patternsWithNumbers() {
        assertEquals("দিন ৩ / ৩০", bn("Day 3 of 30"))
        assertEquals("৭ দিনের ধারা", bn("7-day streak"))
        assertEquals("খরচ সেভ করুন", bn("Save Expense"))          // captured word is translated too
        assertEquals("১০ মিনিট পরে কোর্স পড়া", bn("Course study in 10 min"))
    }

    @Test fun datesAndDigits() {
        assertEquals("সোম ১৭ নভে", bn("Mon 17 Nov"))
        assertEquals("Mon 17 Nov".let { bn(it, digits = false) }, "সোম 17 নভে")
        assertEquals("৳১,২০০", I18n.bnDigits("৳1,200"))
    }

    @Test fun unknownTextStaysEnglish() {
        assertEquals("Quantum flux capacitor", bn("Quantum flux capacitor"))
        assertEquals("CSE ৪১১", bn("CSE 411"))  // user content keeps its words; only digits change
    }

    @Test fun multiLineReset() {
        val en = "This permanently erases ALL data from Dinrekha:\n\n" +
            "• All habit checks & daily logs\n" +
            "• All wallet transactions, recurring & budgets\n" +
            "• All calendar events & notes\n" +
            "• All custom routines & schedules\n" +
            "• All settings, theme & inspiration preferences\n\n" +
            "The app will return to the initial onboarding screen as a fresh, blank install. This action cannot be undone."
        assert(bn(en).startsWith("এটি দিনরেখার সব তথ্য"))
    }
}
