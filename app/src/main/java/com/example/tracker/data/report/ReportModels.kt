package com.example.tracker.data.report

import java.time.LocalDate
import java.time.LocalTime

// ---- Report Models, Score Formulas & Badge Definitions ----

enum class Badge(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val category: String,
) {
    SEVEN_DAYS_ON_TIME(
        "7_days_on_time",
        "7 Days on Time",
        "Woke on time for 7 consecutive days",
        "⏰",
        "Wake-up",
    ),
    ALL_PRAYERS_WEEK(
        "all_prayers_week",
        "First Week All 5 Prayers",
        "Completed all 5 daily prayers every day this week",
        "🕌",
        "Faith",
    ),
    TEN_HOURS_FOCUS(
        "10_hours_focus",
        "10 Hours Deep Work",
        "Logged over 10 hours of focused study & coding in a week",
        "⚡",
        "Focus",
    ),
    FB_UNDER_LIMIT(
        "fb_under_limit",
        "Social Discipline",
        "Facebook kept strictly under daily limits for 7 days",
        "🛡️",
        "Limits",
    ),
    SLEPT_BEFORE_MIDNIGHT(
        "slept_before_midnight",
        "Night Owl Reformed",
        "Slept before midnight 5 nights in a week",
        "🌙",
        "Sleep",
    ),
    CENTURION(
        "centurion",
        "Centurion Day",
        "Achieved a daily improvement score of 90 or higher",
        "🏆",
        "Mastery",
    ),
    CLASS_SCHOLAR(
        "class_scholar",
        "Class Scholar",
        "Captured and organized 10+ lecture notes and board photos",
        "📚",
        "Academics",
    ),
    DEEP_DIVER(
        "deep_diver",
        "Deep Diver",
        "Finished a single intense focus block of 90+ minutes",
        "🎯",
        "Focus",
    );

    companion object {
        fun fromId(id: String): Badge? = entries.find { it.id == id }
    }
}

data class ScoreWeights(
    val wake: Int = 20,
    val sleep: Int = 15,
    val focus: Int = 25,
    val habits: Int = 20,
    val prayers: Int = 15,
    val screenTime: Int = 5,
) {
    val total: Int get() = wake + sleep + focus + habits + prayers + screenTime

    fun calculateScore(
        wokeOnTime: Boolean,
        wakeLateMinutes: Int,
        sleptOnTarget: Boolean,
        sleepLateMinutes: Int,
        focusMinutes: Int,
        targetFocusMinutes: Int = 90,
        habitFraction: Float,
        prayersCompleted: Int,
        prayersTotal: Int = 5,
        screenTimeMinutes: Int,
        screenTimeLimitMinutes: Int = 180,
    ): Int {
        // 1. Wake score
        val wakeRatio = when {
            wokeOnTime -> 1.0f
            wakeLateMinutes <= 15 -> 0.75f
            wakeLateMinutes <= 30 -> 0.50f
            wakeLateMinutes <= 60 -> 0.25f
            else -> 0.0f
        }
        val wakeScore = (wakeRatio * wake)

        // 2. Sleep score
        val sleepRatio = when {
            sleptOnTarget -> 1.0f
            sleepLateMinutes <= 20 -> 0.80f
            sleepLateMinutes <= 45 -> 0.50f
            sleepLateMinutes <= 90 -> 0.25f
            else -> 0.0f
        }
        val sleepScore = (sleepRatio * sleep)

        // 3. Focus score
        val focusRatio = if (targetFocusMinutes > 0) {
            (focusMinutes.toFloat() / targetFocusMinutes.toFloat()).coerceIn(0f, 1f)
        } else 1.0f
        val focusScore = (focusRatio * focus)

        // 4. Habits score
        val habitScore = (habitFraction.coerceIn(0f, 1f) * habits)

        // 5. Prayers score
        val prayerRatio = if (prayersTotal > 0) {
            (prayersCompleted.toFloat() / prayersTotal.toFloat()).coerceIn(0f, 1f)
        } else 1.0f
        val prayerScore = (prayerRatio * prayers)

        // 6. Screen time score
        val screenRatio = when {
            screenTimeMinutes <= screenTimeLimitMinutes -> 1.0f
            screenTimeMinutes <= (screenTimeLimitMinutes * 1.3f) -> 0.6f
            screenTimeMinutes <= (screenTimeLimitMinutes * 1.6f) -> 0.3f
            else -> 0.0f
        }
        val screenScore = (screenRatio * screenTime)

        val rawTotal = wakeScore + sleepScore + focusScore + habitScore + prayerScore + screenScore
        val normalized = if (total > 0) (rawTotal / total.toFloat()) * 100f else rawTotal
        return normalized.toInt().coerceIn(0, 100)
    }
}

data class DailyReport(
    val date: LocalDate,
    val wakeTarget: LocalTime,
    val actualWake: LocalTime?,
    val wokeOnTime: Boolean,
    val wakeLateMinutes: Int,
    val bedTarget: LocalTime,
    val actualBed: LocalTime?,
    val sleptBeforeTarget: Boolean,
    val sleepLateMinutes: Int,
    val hoursSlept: Double,
    val focusMinutes: Int,
    val studyMinutes: Int,
    val researchMinutes: Int,
    val codeMinutes: Int,
    val otherFocusMinutes: Int,
    val habitsCompleted: Int,
    val habitsTotal: Int,
    val habitPercent: Int,
    val prayersCompleted: Int,
    val prayersTotal: Int = 5,
    val walkCompleted: Boolean,
    val routinesCompleted: Int,
    val routinesTotal: Int,
    val screenTimeMinutes: Int,
    val screenTime7DayAvgMinutes: Int,
    val screenTimeDiffMinutes: Int,
    val reelsCount: Int,
    val facebookMinutes: Int,
    val youtubeMinutes: Int,
    val instagramMinutes: Int,
    val deadlinesDone: Int,
    val classNotesCaptured: Int,
    val journalNote: String,
    val mood: Int?,
    val improvementScore: Int,
    val earnedBadges: List<Badge> = emptyList(),
)

data class WeeklyReport(
    val weekNumber: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dailyReports: List<DailyReport>,
    val thisWeekFocusMinutes: Int,
    val lastWeekFocusMinutes: Int,
    val focusDiffPercent: Int,
    val thisWeekHabitPercent: Int,
    val lastWeekHabitPercent: Int,
    val habitDiffPercent: Int,
    val thisWeekPrayersCount: Int,
    val lastWeekPrayersCount: Int,
    val thisWeekScreenTimeMinutes: Int,
    val lastWeekScreenTimeMinutes: Int,
    val screenTimeDiffPercent: Int,
    val thisWeekWakeOnTimeDays: Int,
    val lastWeekWakeOnTimeDays: Int,
    val thisWeekAvgSleepHours: Double,
    val lastWeekAvgSleepHours: Double,
    val bestDay: LocalDate?,
    val bestDayScore: Int,
    val wakeStreak: Int,
    val prayerStreak: Int,
    val studyStreak: Int,
    val earnedBadges: List<Badge>,
    val insights: List<String>,
    val avgImprovementScore: Int,
)

data class ThirtyDayReport(
    val planStart: LocalDate,
    val planEnd: LocalDate,
    val day1Score: Int,
    val day30Score: Int,
    val scoreGrowthPercent: Int,
    val day1FocusMinutes: Int,
    val day30FocusMinutes: Int,
    val day1HabitPercent: Int,
    val day30HabitPercent: Int,
    val day1ScreenTimeMinutes: Int,
    val day30ScreenTimeMinutes: Int,
    val totalFocusHours: Double,
    val totalPrayersCompleted: Int,
    val totalNotesCaptured: Int,
    val milestones: List<String>,
    val allEarnedBadges: List<Badge>,
)

enum class CardFormat(val label: String, val width: Int, val height: Int, val aspectRatio: Float) {
    STORY("Story 9:16", 1080, 1920, 9f / 16f),
    SQUARE("Square 1:1", 1080, 1080, 1f),
}

enum class CardTheme(val label: String, val previewColor: Long) {
    CLEAN_LIGHT("Clean Light", 0xFFFFFFFF),
    DARK_GRADIENT("Dark Gradient", 0xFF0F172A),
    MINIMALIST("Minimalist", 0xFF18181B),
    WARM_SUNSET("Warm Sunset", 0xFF7C2D12),
}

enum class CardType(val label: String) {
    DAILY("Daily Summary"),
    WEEKLY("Weekly Progress"),
    THIRTY_DAY("30-Day Transformation"),
    ACHIEVEMENT("Achievement Badge"),
}

data class CardPrivacySettings(
    val showMood: Boolean = false,
    val showJournal: Boolean = false,
    val showFinance: Boolean = false,
    val showAppNames: Boolean = false,
    val showExactSleepTimes: Boolean = false,
    val showFocus: Boolean = true,
    val showHabits: Boolean = true,
    val showPrayers: Boolean = true,
    val showScreenTime: Boolean = true,
    val showBadges: Boolean = true,
    val showName: Boolean = true,
    val userName: String = "",
)

