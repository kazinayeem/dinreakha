package com.example.tracker.data.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parse() {
        assertEquals(125050L, Money.parse("1,250.50"))
        assertEquals(125050L, Money.parse("৳ 1250.5"))
        assertEquals(100L, Money.parse("1"))
        assertEquals(5L, Money.parse(".05"))
        assertEquals(-5000L, Money.parse("-50"))
        // 0.1 + 0.2 style float errors can't happen
        assertEquals(30L, Money.parse("0.30"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("1.2.3"))
    }

    @Test fun format() {
        assertEquals("৳1,250.50", Money.format(125050))
        assertEquals("৳12,34,567", Money.format(123456700))
        assertEquals("−৳50", Money.format(-5000))
        assertEquals("+৳0.05", Money.format(5, showSign = true))
        assertEquals("৳0", Money.format(0))
    }

    @Test fun inputRoundTrip() {
        listOf(0L, 5L, 50L, 125050L, 120000L, -1999L).forEach {
            assertEquals(it, Money.parse(Money.toInput(it)))
        }
    }
}
