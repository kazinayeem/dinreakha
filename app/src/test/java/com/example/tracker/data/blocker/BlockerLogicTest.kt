package com.example.tracker.data.blocker

import com.example.tracker.data.db.BlockRuleEntity
import com.example.tracker.data.db.BlockRuleType
import com.example.tracker.data.db.ReelsUsageEntity
import com.example.tracker.data.repo.BlockerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BlockerLogicTest {

    @Test
    fun systemAllowList_neverBlocksDialerOrSettingsOrSelf() {
        assertTrue(BlockerRepository.SYSTEM_ALLOW_LIST.contains("com.example.tracker"))
        assertTrue(BlockerRepository.SYSTEM_ALLOW_LIST.contains("com.google.android.dialer"))
        assertTrue(BlockerRepository.SYSTEM_ALLOW_LIST.contains("com.android.settings"))
        assertTrue(BlockerRepository.SYSTEM_ALLOW_LIST.contains("com.bKash.customerapp"))
        assertTrue(BlockerRepository.SYSTEM_ALLOW_LIST.contains("com.konasl.nagad"))
    }

    @Test
    fun isWithinSchedule_normalDayWindow() {
        // 09:00 to 17:00
        val rule = BlockRuleEntity(
            type = BlockRuleType.SCHEDULE,
            name = "Work Focus",
            targetPackages = "com.facebook.katana",
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            days = "1,2,3,4,5", // Mon-Fri
        )

        // Tuesday 10:30 AM -> inside
        val tues10am = LocalDateTime.of(2026, 10, 27, 10, 30) // Tuesday
        assertEquals(DayOfWeek.TUESDAY, tues10am.dayOfWeek)
        assertTrue(isWithinScheduleTest(rule, tues10am))

        // Tuesday 18:00 -> outside
        val tues6pm = LocalDateTime.of(2026, 10, 27, 18, 0)
        assertFalse(isWithinScheduleTest(rule, tues6pm))

        // Sunday 10:30 AM -> not in days (1..5)
        val sun10am = LocalDateTime.of(2026, 10, 25, 10, 30) // Sunday
        assertEquals(DayOfWeek.SUNDAY, sun10am.dayOfWeek)
        assertFalse(isWithinScheduleTest(rule, sun10am))
    }

    @Test
    fun isWithinSchedule_spansMidnightSleepMode() {
        // Sleep mode: 23:00 to 05:05
        val sleepRule = BlockRuleEntity(
            type = BlockRuleType.SCHEDULE,
            name = "Sleep Mode",
            targetPackages = "com.facebook.katana",
            startTime = LocalTime.of(23, 0),
            endTime = LocalTime.of(5, 5),
            days = "", // Everyday
        )

        // 23:30 PM -> inside
        val night2330 = LocalDateTime.of(2026, 10, 24, 23, 30)
        assertTrue(isWithinScheduleTest(sleepRule, night2330))

        // 03:00 AM -> inside
        val morning0300 = LocalDateTime.of(2026, 10, 25, 3, 0)
        assertTrue(isWithinScheduleTest(sleepRule, morning0300))

        // 05:04 AM -> inside
        val morning0504 = LocalDateTime.of(2026, 10, 25, 5, 4)
        assertTrue(isWithinScheduleTest(sleepRule, morning0504))

        // 05:06 AM -> outside (unlocked)
        val morning0506 = LocalDateTime.of(2026, 10, 25, 5, 6)
        assertFalse(isWithinScheduleTest(sleepRule, morning0506))

        // 14:00 PM -> outside
        val afternoon = LocalDateTime.of(2026, 10, 25, 14, 0)
        assertFalse(isWithinScheduleTest(sleepRule, afternoon))
    }

    @Test
    fun dailyLimit_calculation() {
        val limitMinutes = 30

        // 25 minutes used -> allowed
        val used25 = 25
        assertFalse(used25 >= limitMinutes)

        // 30 minutes used -> reached limit, must block
        val used30 = 30
        assertTrue(used30 >= limitMinutes)

        // 45 minutes used -> over limit, must block
        val used45 = 45
        assertTrue(used45 >= limitMinutes)
    }

    @Test
    fun emergencyUnlock_maxTwoPerDay() {
        var count = 0
        fun tryUnlock(): Boolean {
            if (count >= BlockerRepository.MAX_EMERGENCY_UNLOCKS_PER_DAY) return false
            count++
            return true
        }

        assertTrue(tryUnlock()) // 1st
        assertEquals(1, count)
        assertTrue(tryUnlock()) // 2nd
        assertEquals(2, count)
        assertFalse(tryUnlock()) // 3rd must fail
        assertEquals(2, count)
    }

    @Test
    fun emergencyUnlock_windowDuration() {
        val lastUnlock = 1_000_000L
        val activeAt = 1_100_000L // 100s later (under 300s = 5m)
        val expiredAt = 1_400_000L // 400s later (over 300s)

        val isActive = (activeAt - lastUnlock) in 0L..BlockerRepository.EMERGENCY_UNLOCK_DURATION_MS
        val isExpired = (expiredAt - lastUnlock) in 0L..BlockerRepository.EMERGENCY_UNLOCK_DURATION_MS

        assertTrue(isActive)
        assertFalse(isExpired)
    }

    @Test
    fun reelsUsage_platformCategorizationAndCounting() {
        val today = LocalDate.of(2026, 11, 17)
        val usageList = listOf(
            ReelsUsageEntity(date = today, platform = "instagram", count = 25, lastIncrementAt = 1000L),
            ReelsUsageEntity(date = today, platform = "youtube", count = 15, lastIncrementAt = 2000L),
            ReelsUsageEntity(date = today, platform = "facebook", count = 10, lastIncrementAt = 3000L),
        )

        val total = usageList.sumOf { it.count }
        assertEquals(50, total)

        val insta = usageList.find { it.platform == "instagram" }?.count ?: 0
        val yt = usageList.find { it.platform == "youtube" }?.count ?: 0
        val fb = usageList.find { it.platform == "facebook" }?.count ?: 0

        assertEquals(25, insta)
        assertEquals(15, yt)
        assertEquals(10, fb)
    }

    @Test
    fun reelsUsage_limitAndDoomscrollCalculation() {
        val limit = 50

        // 30 reels -> below limit, not approaching (30 < 40)
        val count30 = 30
        assertFalse(count30 >= limit)
        assertFalse(count30 >= (limit * 0.8f))
        assertEquals(15, (count30 * 30) / 60) // 15 mins at 30s/reel

        // 42 reels -> approaching 80% limit (42 >= 40)
        val count42 = 42
        assertFalse(count42 >= limit)
        assertTrue(count42 >= (limit * 0.8f))
        assertEquals(21, (count42 * 30) / 60) // 21 mins

        // 50 reels -> reached limit
        val count50 = 50
        assertTrue(count50 >= limit)
        assertEquals(25, (count50 * 30) / 60) // 25 mins

        // 60 reels -> exceeded limit
        val count60 = 60
        assertTrue(count60 >= limit)
        assertEquals(30, (count60 * 30) / 60) // 30 mins
    }

    @Test
    fun reelsUsage_mindfulIntervalAlertTrigger() {
        fun shouldTriggerMindfulAlert(count: Int): Boolean = count > 0 && count % 25 == 0

        assertFalse(shouldTriggerMindfulAlert(0))
        assertFalse(shouldTriggerMindfulAlert(10))
        assertFalse(shouldTriggerMindfulAlert(24))
        assertTrue(shouldTriggerMindfulAlert(25))
        assertFalse(shouldTriggerMindfulAlert(26))
        assertTrue(shouldTriggerMindfulAlert(50))
        assertTrue(shouldTriggerMindfulAlert(75))
        assertTrue(shouldTriggerMindfulAlert(100))
    }

    @Test
    fun reelsUsage_platformDetectionSignatures() {
        // YouTube Shorts keyword matches
        val ytKeywords = listOf("Sound used in this Short", "Dislike this short", "Remix this video", "Remix", "Shorts sound")
        val sampleYtText = "Original Sound - Remix this video with Sound used in this Short"
        assertTrue(ytKeywords.any { sampleYtText.contains(it, ignoreCase = true) })

        // Facebook Reels keyword matches
        val fbKeywords = listOf("Remix this reel", "Original audio", "Use audio", "Audio is unavailable", "Reel by", "Create Reel")
        val sampleFbText = "Original audio • Reel by TechReviewer"
        assertTrue(fbKeywords.any { sampleFbText.contains(it, ignoreCase = true) })

        // Instagram Reels keyword matches
        val instaKeywords = listOf("Original audio", "Audio is unavailable", "Remix with", "Reel by")
        val sampleInstaText = "Remix with this audio • Reel by Creator"
        assertTrue(instaKeywords.any { sampleInstaText.contains(it, ignoreCase = true) })
    }

    @Test
    fun reelsUsage_scrollDebounceAndTransition() {
        val debounceMs = 800L
        val t0 = 1000L

        // Swipe too fast (300ms < 800ms) -> do not count
        val t1 = 1300L
        assertFalse(t1 - t0 >= debounceMs)

        // Valid swipe (900ms >= 800ms) -> count!
        val t2 = 1900L
        assertTrue(t2 - t0 >= debounceMs)

        // New reel signature transition after 1000ms -> count!
        val sigA = "audio_123"
        val sigB = "audio_456"
        val t3 = 2100L
        val signatureChanged = sigA != sigB && (t3 - t0 >= 1000L)
        assertTrue(signatureChanged)
    }

    private fun isWithinScheduleTest(rule: BlockRuleEntity, now: LocalDateTime): Boolean {
        val days = rule.days.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (days.isNotEmpty() && now.dayOfWeek.value !in days) return false

        val start = rule.startTime ?: return false
        val end = rule.endTime ?: return false
        val current = now.toLocalTime()

        return if (start.isBefore(end)) {
            !current.isBefore(start) && !current.isAfter(end)
        } else {
            !current.isBefore(start) || !current.isAfter(end)
        }
    }
}
