package com.example.tracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val lightCard = Color(0xFFECEEF3)
    private val darkCard = Color(0xFF20232B)
    private val accents = listOf(IncomeBase, ExpenseBase, WarnBase, Color(0xFF10B981), Color(0xFFE1306C),
        Color(0xFF1877F2), Color(0xFF818CF8), Color(0xFFFFB300), Color(0xFF6366F1))

    @Test fun everyAccentReadableOnLightAndDark() {
        accents.forEach { c ->
            assertTrue("light $c", contrast(c.readableOn(lightCard), lightCard) >= 4.5f)
            assertTrue("dark $c", contrast(c.readableOn(darkCard), darkCard) >= 4.5f)
        }
    }

    @Test fun passingColoursAreUntouched() {
        val deep = Color(0xFF0E5A3A)
        assertEquals(deep, deep.readableOn(lightCard))
    }

    @Test fun warnStaysWarm() {
        val w = WarnBase.readableOn(lightCard)
        assertTrue("still more red than blue", w.red > w.blue)
    }
}
