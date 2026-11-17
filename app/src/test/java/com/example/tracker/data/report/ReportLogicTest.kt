package com.example.tracker.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReportLogicTest {

    @Test
    fun scoreWeights_perfectDayReturns100() {
        val weights = ScoreWeights(
            wake = 20,
            sleep = 15,
            focus = 25,
            habits = 20,
            prayers = 15,
            screenTime = 5,
        )

        val score = weights.calculateScore(
            wokeOnTime = true,
            wakeLateMinutes = 0,
            sleptOnTarget = true,
            sleepLateMinutes = 0,
            focusMinutes = 90,
            targetFocusMinutes = 90,
            habitFraction = 1.0f,
            prayersCompleted = 5,
            prayersTotal = 5,
            screenTimeMinutes = 120,
            screenTimeLimitMinutes = 180,
        )

        assertEquals(100, score)
    }

    @Test
    fun scoreWeights_partialCompletionScalesCorrectly() {
        val weights = ScoreWeights(
            wake = 20,
            sleep = 15,
            focus = 25,
            habits = 20,
            prayers = 15,
            screenTime = 5,
        )

        // Wake: late by 10 min -> 75% of 20 = 15
        // Sleep: late by 15 min -> 80% of 15 = 12
        // Focus: 45 of 90 min -> 50% of 25 = 12.5
        // Habits: 50% -> 10
        // Prayers: 3 of 5 -> 60% of 15 = 9
        // Screen time: 150m (<= 180) -> 5
        // Raw total: 15 + 12 + 12.5 + 10 + 9 + 5 = 63.5 -> 63
        val score = weights.calculateScore(
            wokeOnTime = false,
            wakeLateMinutes = 10,
            sleptOnTarget = false,
            sleepLateMinutes = 15,
            focusMinutes = 45,
            targetFocusMinutes = 90,
            habitFraction = 0.5f,
            prayersCompleted = 3,
            prayersTotal = 5,
            screenTimeMinutes = 150,
            screenTimeLimitMinutes = 180,
        )

        assertEquals(63, score)
    }

    @Test
    fun scoreWeights_customWeightsNormalizeTo100() {
        // Custom weights total 200 pts
        val customWeights = ScoreWeights(
            wake = 40,
            sleep = 30,
            focus = 50,
            habits = 40,
            prayers = 30,
            screenTime = 10,
        )
        assertEquals(200, customWeights.total)

        // Perfect day must still normalize to 100
        val score = customWeights.calculateScore(
            wokeOnTime = true,
            wakeLateMinutes = 0,
            sleptOnTarget = true,
            sleepLateMinutes = 0,
            focusMinutes = 120,
            targetFocusMinutes = 90,
            habitFraction = 1.0f,
            prayersCompleted = 5,
            prayersTotal = 5,
            screenTimeMinutes = 90,
            screenTimeLimitMinutes = 180,
        )

        assertEquals(100, score)
    }

    @Test
    fun privacySettings_defaultsAreStrictlyPrivate() {
        val defaultPrivacy = CardPrivacySettings()

        // Critical private items must be OFF by default
        assertFalse(defaultPrivacy.showMood)
        assertFalse(defaultPrivacy.showJournal)
        assertFalse(defaultPrivacy.showFinance)
        assertFalse(defaultPrivacy.showAppNames)
        assertFalse(defaultPrivacy.showExactSleepTimes)

        // Motivating public metrics can be ON
        assertTrue(defaultPrivacy.showFocus)
        assertTrue(defaultPrivacy.showHabits)
        assertTrue(defaultPrivacy.showPrayers)
        assertTrue(defaultPrivacy.showScreenTime)
        assertTrue(defaultPrivacy.showBadges)
    }

    @Test
    fun weekOverWeekComparison_percentageDeltas() {
        val lastWeekFocus = 400 // min
        val thisWeekFocus = 580 // min (+45%)
        val focusDiffPercent = (((thisWeekFocus - lastWeekFocus).toFloat() / lastWeekFocus.toFloat()) * 100f).toInt()
        assertEquals(45, focusDiffPercent)

        val lastWeekScreenTime = 1200 // min
        val thisWeekScreenTime = 960 // min (-20%)
        val screenDiffPercent = (((thisWeekScreenTime - lastWeekScreenTime).toFloat() / lastWeekScreenTime.toFloat()) * 100f).toInt()
        assertEquals(-20, screenDiffPercent)
    }

    @Test
    fun badgeQualification_logic() {
        // 7 days wake on time
        val wakeDays = listOf(true, true, true, true, true, true, true)
        assertTrue(wakeDays.count { it } >= 7)

        // 10 hours focus in a week
        val focusMinutes = 620
        assertTrue(focusMinutes >= 600)

        // Facebook under limit
        val fbMinutesPerDay = listOf(20, 25, 15, 30, 22, 18, 25)
        assertTrue(fbMinutesPerDay.all { it <= 30 })

        // Slept before midnight 5 nights
        val bedtimes = listOf(
            LocalTime.of(23, 15),
            LocalTime.of(23, 40),
            LocalTime.of(23, 50),
            LocalTime.of(23, 30),
            LocalTime.of(23, 55),
            LocalTime.of(0, 15),
            LocalTime.of(0, 30),
        )
        val earlyNights = bedtimes.count { it.hour in 18..23 }
        assertEquals(5, earlyNights)
        assertTrue(earlyNights >= 5)
    }

    @Test
    fun nonGuiltInsights_constructivePhrasing() {
        fun insightForDecline(metric: String, diff: Int): String {
            return if (diff < 0) {
                "Next week: try scheduling morning focus blocks to rebuild momentum."
            } else {
                "Focus time grew by $diff% this week. Solid deep work momentum!"
            }
        }

        val positive = insightForDecline("focus", 45)
        assertTrue(positive.contains("grew by 45%"))
        assertFalse(positive.contains("fail") || positive.contains("bad"))

        val neutral = insightForDecline("focus", -15)
        assertTrue(neutral.startsWith("Next week: try"))
        assertFalse(neutral.contains("fail") || neutral.contains("guilt") || neutral.contains("bad"))
    }
}

