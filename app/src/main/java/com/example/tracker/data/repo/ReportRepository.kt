package com.example.tracker.data.repo

import android.content.Context
import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.HabitCheck
import com.example.tracker.data.db.ReportCacheEntity
import com.example.tracker.data.db.Setting
import com.example.tracker.data.report.Badge
import com.example.tracker.data.report.DailyReport
import com.example.tracker.data.report.ScoreWeights
import com.example.tracker.data.report.ThirtyDayReport
import com.example.tracker.data.report.WeeklyReport
import com.example.tracker.data.schedule.Habits
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class ReportRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduleRepo: ScheduleRepository,
) {
    private val reportDao = db.reportCacheDao()
    private val habitDao = db.habitCheckDao()
    private val dayLogDao = db.dayLogDao()
    private val focusDao = db.focusDao()
    private val blockerDao = db.blockerDao()
    private val classNoteDao = db.classNoteDao()
    private val calendarDao = db.calendarDao()
    private val settingsDao = db.settingsDao()

    companion object {
        const val PREF_WEIGHT_WAKE = "score_weight_wake"
        const val PREF_WEIGHT_SLEEP = "score_weight_sleep"
        const val PREF_WEIGHT_FOCUS = "score_weight_focus"
        const val PREF_WEIGHT_HABITS = "score_weight_habits"
        const val PREF_WEIGHT_PRAYERS = "score_weight_prayers"
        const val PREF_WEIGHT_SCREEN = "score_weight_screen"
        const val PREF_FB_APP_ID = "fb_app_id"
        const val PREF_USER_CARD_NAME = "user_card_name"
    }

    // ==================== Score Weights & Settings ====================

    suspend fun getScoreWeights(): ScoreWeights = withContext(Dispatchers.IO) {
        val s = settingsDao.all().associate { it.key to it.value }
        ScoreWeights(
            wake = s[PREF_WEIGHT_WAKE]?.toIntOrNull() ?: 20,
            sleep = s[PREF_WEIGHT_SLEEP]?.toIntOrNull() ?: 15,
            focus = s[PREF_WEIGHT_FOCUS]?.toIntOrNull() ?: 25,
            habits = s[PREF_WEIGHT_HABITS]?.toIntOrNull() ?: 20,
            prayers = s[PREF_WEIGHT_PRAYERS]?.toIntOrNull() ?: 15,
            screenTime = s[PREF_WEIGHT_SCREEN]?.toIntOrNull() ?: 5,
        )
    }

    suspend fun setScoreWeights(weights: ScoreWeights) = withContext(Dispatchers.IO) {
        settingsDao.put(Setting(PREF_WEIGHT_WAKE, weights.wake.toString()))
        settingsDao.put(Setting(PREF_WEIGHT_SLEEP, weights.sleep.toString()))
        settingsDao.put(Setting(PREF_WEIGHT_FOCUS, weights.focus.toString()))
        settingsDao.put(Setting(PREF_WEIGHT_HABITS, weights.habits.toString()))
        settingsDao.put(Setting(PREF_WEIGHT_PRAYERS, weights.prayers.toString()))
        settingsDao.put(Setting(PREF_WEIGHT_SCREEN, weights.screenTime.toString()))
    }

    suspend fun getFacebookAppId(): String = withContext(Dispatchers.IO) {
        settingsDao.get(PREF_FB_APP_ID) ?: ""
    }

    suspend fun setFacebookAppId(id: String) = withContext(Dispatchers.IO) {
        settingsDao.put(Setting(PREF_FB_APP_ID, id.trim()))
    }

    suspend fun getUserCardName(): String = withContext(Dispatchers.IO) {
        settingsDao.get(PREF_USER_CARD_NAME) ?: ""
    }

    suspend fun setUserCardName(name: String) = withContext(Dispatchers.IO) {
        settingsDao.put(Setting(PREF_USER_CARD_NAME, name.trim()))
    }

    // ==================== Daily Report Computation ====================

    fun observeDailyReport(date: LocalDate): Flow<DailyReport> = flow {
        emit(computeDailyReport(date))
    }.flowOn(Dispatchers.IO)

    suspend fun computeDailyReport(date: LocalDate): DailyReport = withContext(Dispatchers.IO) {
        val weights = getScoreWeights()

        // 1. Habit checks & targets
        val checks = habitDao.getForDate(date)
        val doneKeys = checks.filter { it.done }.map { it.habitKey }.toSet()

        val wakeCheck = checks.find { it.habitKey == Habits.WOKE_ON_TIME && it.done }
        val wakeTarget = PlanCalendar.wakeTime(date)
        val actualWake = wakeCheck?.doneAt
        val wokeOnTime = wakeCheck != null && (actualWake == null || !actualWake.isAfter(wakeTarget))
        val wakeLateMinutes = if (actualWake != null && actualWake.isAfter(wakeTarget)) {
            ChronoUnit.MINUTES.between(wakeTarget, actualWake).toInt()
        } else 0

        val sleepCheck = checks.find { it.habitKey == Habits.SCREENS_OFF && it.done }
        val bedTarget = PlanCalendar.phaseFor(date).bed
        val actualBed = sleepCheck?.doneAt
        val sleptBeforeTarget = sleepCheck != null && (actualBed == null || !actualBed.isAfter(bedTarget))
        val sleepLateMinutes = if (actualBed != null && actualBed.isAfter(bedTarget)) {
            ChronoUnit.MINUTES.between(bedTarget, actualBed).toInt()
        } else 0

        // Estimate hours slept (difference between actual or target bed and next wake)
        val nextWakeDt = PlanCalendar.wakeDateTime(date.plusDays(1))
        val bedDt = if (actualBed != null) {
            if (actualBed.hour < 12) date.plusDays(1).atTime(actualBed) else date.atTime(actualBed)
        } else {
            PlanCalendar.bedDateTime(date)
        }
        // A logged night (Sleep & mood screen) beats the estimate from the "screens off" tick.
        val logged = db.sleepDao().get(date)
        val sleepDurationMin = logged?.minutes ?: ChronoUnit.MINUTES.between(bedDt, nextWakeDt).coerceIn(0, 720)
        val hoursSlept = sleepDurationMin / 60.0

        // 2. Focus Sessions
        val focusSessions = focusDao.all().filter { it.start.toLocalDate() == date }
        val totalFocusMin = focusSessions.sumOf { it.minutes }
        val studyMin = focusSessions.filter { it.type.equals("STUDY", ignoreCase = true) }.sumOf { it.minutes }
        val researchMin = focusSessions.filter { it.type.equals("RESEARCH", ignoreCase = true) }.sumOf { it.minutes }
        val codeMin = focusSessions.filter { it.type.equals("CODE", ignoreCase = true) }.sumOf { it.minutes }
        val otherFocusMin = focusSessions.filter { it.type.equals("OTHER", ignoreCase = true) }.sumOf { it.minutes }

        // 3. Checklist & Habits
        val checklist = PlanCalendar.checklist(date)
        val expected = checklist.filter { it.expected }
        val habitsCompleted = expected.count { it.key in doneKeys }
        val habitsTotal = expected.size
        val habitPercent = if (habitsTotal > 0) (habitsCompleted * 100) / habitsTotal else 0
        val habitFraction = if (habitsTotal > 0) habitsCompleted.toFloat() / habitsTotal.toFloat() else 0f

        // Prayers
        val prayersCompleted = Schedule.Prayer.entries.count { Habits.prayerKey(it) in doneKeys }

        // Wellness / Walk / Routines
        val walkCompleted = Habits.WALK in doneKeys
        val routines = checklist.filter { it.key.startsWith("routine_") }
        val routinesCompleted = routines.count { it.key in doneKeys }
        val routinesTotal = routines.size

        // 4. Screen Time & App Usage
        val usageList = blockerDao.getAppUsageForDate(date)
        val screenTimeMinutes = (usageList.sumOf { it.foregroundMs } / 60000L).toInt()

        // 7-day average screen time
        var past7DaysTotalMs = 0L
        for (i in 1..7) {
            val pastDate = date.minusDays(i.toLong())
            val pastUsage = blockerDao.getAppUsageForDate(pastDate)
            past7DaysTotalMs += pastUsage.sumOf { it.foregroundMs }
        }
        val screenTime7DayAvgMinutes = ((past7DaysTotalMs / 7L) / 60000L).toInt()
        val screenTimeDiffMinutes = screenTimeMinutes - screenTime7DayAvgMinutes

        val fbMs = usageList.filter { it.packageName.startsWith("com.facebook") }.sumOf { it.foregroundMs }
        val fbMinutes = (fbMs / 60000L).toInt()
        val ytMs = usageList.filter { it.packageName.startsWith("com.google.android.youtube") }.sumOf { it.foregroundMs }
        val ytMinutes = (ytMs / 60000L).toInt()
        val instaMs = usageList.filter { it.packageName.startsWith("com.instagram") }.sumOf { it.foregroundMs }
        val instaMinutes = (instaMs / 60000L).toInt()

        val reelsCount = blockerDao.getTotalReelsCountForDate(date) ?: 0

        // 5. Deadlines & Class Notes
        val allEvents = calendarDao.events()
        val deadlinesDone = allEvents.count { it.start.toLocalDate() == date }

        val allCourses = classNoteDao.allCourses()
        var classNotesCaptured = 0
        allCourses.forEach { course ->
            val notes = classNoteDao.getNotesForCourse(course.id)
            classNotesCaptured += notes.count { it.note.date == date }
        }

        // 6. Journal & Mood
        val dayLog = dayLogDao.get(date)
        val mood = dayLog?.mood
        val journalNote = dayLog?.note.orEmpty()

        // 7. Improvement Score
        val improvementScore = weights.calculateScore(
            wokeOnTime = wokeOnTime,
            wakeLateMinutes = wakeLateMinutes,
            sleptOnTarget = sleptBeforeTarget,
            sleepLateMinutes = sleepLateMinutes,
            focusMinutes = totalFocusMin,
            targetFocusMinutes = 90,
            habitFraction = habitFraction,
            prayersCompleted = prayersCompleted,
            prayersTotal = 5,
            screenTimeMinutes = screenTimeMinutes,
            screenTimeLimitMinutes = 180,
        )

        // 8. Daily Badges
        val dailyBadges = mutableListOf<Badge>()
        if (improvementScore >= 90) dailyBadges.add(Badge.CENTURION)
        if (focusSessions.any { it.minutes >= 90 }) dailyBadges.add(Badge.DEEP_DIVER)
        if (classNotesCaptured >= 10) dailyBadges.add(Badge.CLASS_SCHOLAR)

        val report = DailyReport(
            date = date,
            wakeTarget = wakeTarget,
            actualWake = actualWake,
            wokeOnTime = wokeOnTime,
            wakeLateMinutes = wakeLateMinutes,
            bedTarget = bedTarget,
            actualBed = actualBed,
            sleptBeforeTarget = sleptBeforeTarget,
            sleepLateMinutes = sleepLateMinutes,
            hoursSlept = hoursSlept,
            focusMinutes = totalFocusMin,
            studyMinutes = studyMin,
            researchMinutes = researchMin,
            codeMinutes = codeMin,
            otherFocusMinutes = otherFocusMin,
            habitsCompleted = habitsCompleted,
            habitsTotal = habitsTotal,
            habitPercent = habitPercent,
            prayersCompleted = prayersCompleted,
            prayersTotal = 5,
            walkCompleted = walkCompleted,
            routinesCompleted = routinesCompleted,
            routinesTotal = routinesTotal,
            screenTimeMinutes = screenTimeMinutes,
            screenTime7DayAvgMinutes = screenTime7DayAvgMinutes,
            screenTimeDiffMinutes = screenTimeDiffMinutes,
            reelsCount = reelsCount,
            facebookMinutes = fbMinutes,
            youtubeMinutes = ytMinutes,
            instagramMinutes = instaMinutes,
            deadlinesDone = deadlinesDone,
            classNotesCaptured = classNotesCaptured,
            journalNote = journalNote,
            mood = mood,
            improvementScore = improvementScore,
            earnedBadges = dailyBadges,
        )

        cacheDailyReport(report)
        report
    }

    // ==================== Weekly Report Computation ====================

    fun observeWeeklyReport(week: Int): Flow<WeeklyReport> = flow {
        emit(computeWeeklyReport(week))
    }.flowOn(Dispatchers.IO)

    suspend fun computeWeeklyReport(week: Int): WeeklyReport = withContext(Dispatchers.IO) {
        val dates = PlanCalendar.weekDates(week)
        val dailyReports = dates.map { computeDailyReport(it) }

        // Last week daily reports for comparison
        val lastWeekDates = if (week > 1) PlanCalendar.weekDates(week - 1) else emptyList()
        val lastWeekDailyReports = lastWeekDates.map { computeDailyReport(it) }

        // This week totals
        val thisWeekFocusMin = dailyReports.sumOf { it.focusMinutes }
        val lastWeekFocusMin = lastWeekDailyReports.sumOf { it.focusMinutes }
        val focusDiffPercent = if (lastWeekFocusMin > 0) {
            (((thisWeekFocusMin - lastWeekFocusMin).toFloat() / lastWeekFocusMin.toFloat()) * 100f).toInt()
        } else if (thisWeekFocusMin > 0) 100 else 0

        val thisWeekHabitPercent = if (dailyReports.isNotEmpty()) dailyReports.map { it.habitPercent }.average().toInt() else 0
        val lastWeekHabitPercent = if (lastWeekDailyReports.isNotEmpty()) lastWeekDailyReports.map { it.habitPercent }.average().toInt() else 0
        val habitDiffPercent = thisWeekHabitPercent - lastWeekHabitPercent

        val thisWeekPrayers = dailyReports.sumOf { it.prayersCompleted }
        val lastWeekPrayers = lastWeekDailyReports.sumOf { it.prayersCompleted }

        val thisWeekScreenTime = dailyReports.sumOf { it.screenTimeMinutes }
        val lastWeekScreenTime = lastWeekDailyReports.sumOf { it.screenTimeMinutes }
        val screenTimeDiffPercent = if (lastWeekScreenTime > 0) {
            (((thisWeekScreenTime - lastWeekScreenTime).toFloat() / lastWeekScreenTime.toFloat()) * 100f).toInt()
        } else 0

        val thisWeekWakeOnTimeDays = dailyReports.count { it.wokeOnTime }
        val lastWeekWakeOnTimeDays = lastWeekDailyReports.count { it.wokeOnTime }

        val thisWeekAvgSleep = if (dailyReports.isNotEmpty()) dailyReports.map { it.hoursSlept }.average() else 7.0
        val lastWeekAvgSleep = if (lastWeekDailyReports.isNotEmpty()) lastWeekDailyReports.map { it.hoursSlept }.average() else 7.0

        // Best Day
        val bestDayReport = dailyReports.maxByOrNull { it.improvementScore }
        val bestDay = bestDayReport?.date
        val bestDayScore = bestDayReport?.improvementScore ?: 0

        // Streaks
        val wakeStreak = calculateStreak(dailyReports.map { it.wokeOnTime })
        val prayerStreak = calculateStreak(dailyReports.map { it.prayersCompleted == 5 })
        val studyStreak = calculateStreak(dailyReports.map { it.focusMinutes > 0 })

        // Weekly Badges
        val weeklyBadges = mutableListOf<Badge>()
        if (thisWeekWakeOnTimeDays >= 7) weeklyBadges.add(Badge.SEVEN_DAYS_ON_TIME)
        if (thisWeekPrayers >= 35) weeklyBadges.add(Badge.ALL_PRAYERS_WEEK)
        if (thisWeekFocusMin >= 600) weeklyBadges.add(Badge.TEN_HOURS_FOCUS)
        if (dailyReports.all { it.facebookMinutes <= 30 }) weeklyBadges.add(Badge.FB_UNDER_LIMIT)
        val sleptBeforeMidnightDays = dailyReports.count { (it.actualBed?.hour ?: 23) < 24 && (it.actualBed?.hour ?: 23) >= 18 }
        if (sleptBeforeMidnightDays >= 5) weeklyBadges.add(Badge.SLEPT_BEFORE_MIDNIGHT)
        if (dailyReports.any { it.improvementScore >= 90 }) weeklyBadges.add(Badge.CENTURION)

        // Non-guilt auto insights
        val insights = generateConstructiveInsights(
            focusDiffPercent = focusDiffPercent,
            thisWeekFocusMin = thisWeekFocusMin,
            habitDiffPercent = habitDiffPercent,
            thisWeekHabitPercent = thisWeekHabitPercent,
            thisWeekPrayers = thisWeekPrayers,
            thisWeekWakeOnTimeDays = thisWeekWakeOnTimeDays,
            thisWeekAvgSleep = thisWeekAvgSleep,
            lastWeekAvgSleep = lastWeekAvgSleep,
            screenTimeDiffPercent = screenTimeDiffPercent,
            dailyReports = dailyReports,
        )

        val avgScore = if (dailyReports.isNotEmpty()) dailyReports.map { it.improvementScore }.average().toInt() else 0

        val report = WeeklyReport(
            weekNumber = week,
            startDate = dates.first(),
            endDate = dates.last(),
            dailyReports = dailyReports,
            thisWeekFocusMinutes = thisWeekFocusMin,
            lastWeekFocusMinutes = lastWeekFocusMin,
            focusDiffPercent = focusDiffPercent,
            thisWeekHabitPercent = thisWeekHabitPercent,
            lastWeekHabitPercent = lastWeekHabitPercent,
            habitDiffPercent = habitDiffPercent,
            thisWeekPrayersCount = thisWeekPrayers,
            lastWeekPrayersCount = lastWeekPrayers,
            thisWeekScreenTimeMinutes = thisWeekScreenTime,
            lastWeekScreenTimeMinutes = lastWeekScreenTime,
            screenTimeDiffPercent = screenTimeDiffPercent,
            thisWeekWakeOnTimeDays = thisWeekWakeOnTimeDays,
            lastWeekWakeOnTimeDays = lastWeekWakeOnTimeDays,
            thisWeekAvgSleepHours = thisWeekAvgSleep,
            lastWeekAvgSleepHours = lastWeekAvgSleep,
            bestDay = bestDay,
            bestDayScore = bestDayScore,
            wakeStreak = wakeStreak,
            prayerStreak = prayerStreak,
            studyStreak = studyStreak,
            earnedBadges = weeklyBadges,
            insights = insights,
            avgImprovementScore = avgScore,
        )

        cacheWeeklyReport(report)
        report
    }

    // ==================== 30-Day Plan Comparison ====================

    suspend fun computeThirtyDayReport(): ThirtyDayReport = withContext(Dispatchers.IO) {
        val planStart = PlanCalendar.planStart
        val planEnd = PlanCalendar.planEnd

        val day1Report = computeDailyReport(planStart)
        val day30Report = computeDailyReport(planEnd)

        val allSessions = focusDao.all()
        val totalFocusHours = (allSessions.sumOf { it.minutes } / 60.0)

        val allChecks = habitDao.all()
        val totalPrayers = allChecks.count { it.done && it.habitKey.startsWith("prayer_") }

        val allCourses = classNoteDao.allCourses()
        var totalNotes = 0
        allCourses.forEach { totalNotes += classNoteDao.getNotesForCourse(it.id).size }

        val scoreGrowth = day30Report.improvementScore - day1Report.improvementScore

        val milestones = mutableListOf<String>()
        if (totalFocusHours >= 20) milestones.add("Completed over ${totalFocusHours.toInt()} hours of deep work")
        if (totalPrayers >= 100) milestones.add("Offered $totalPrayers prayers in congregation/on-time")
        if (totalNotes >= 15) milestones.add("Built a comprehensive library of $totalNotes class notes")
        milestones.add("Transformed bedtime rhythm and daily consistency")

        val allBadges = Badge.entries.filter { b ->
            when (b) {
                Badge.TEN_HOURS_FOCUS -> totalFocusHours >= 10
                Badge.CLASS_SCHOLAR -> totalNotes >= 10
                Badge.CENTURION -> day30Report.improvementScore >= 90 || day1Report.improvementScore >= 90
                else -> true
            }
        }

        ThirtyDayReport(
            planStart = planStart,
            planEnd = planEnd,
            day1Score = day1Report.improvementScore,
            day30Score = day30Report.improvementScore,
            scoreGrowthPercent = scoreGrowth,
            day1FocusMinutes = day1Report.focusMinutes,
            day30FocusMinutes = day30Report.focusMinutes,
            day1HabitPercent = day1Report.habitPercent,
            day30HabitPercent = day30Report.habitPercent,
            day1ScreenTimeMinutes = day1Report.screenTimeMinutes,
            day30ScreenTimeMinutes = day30Report.screenTimeMinutes,
            totalFocusHours = totalFocusHours,
            totalPrayersCompleted = totalPrayers,
            totalNotesCaptured = totalNotes,
            milestones = milestones,
            allEarnedBadges = allBadges,
        )
    }

    // ==================== Helpers & Neutral Insights ====================

    private fun calculateStreak(conditions: List<Boolean>): Int {
        var maxStreak = 0
        var current = 0
        for (met in conditions) {
            if (met) {
                current++
                if (current > maxStreak) maxStreak = current
            } else {
                current = 0
            }
        }
        return maxStreak
    }

    private fun generateConstructiveInsights(
        focusDiffPercent: Int,
        thisWeekFocusMin: Int,
        habitDiffPercent: Int,
        thisWeekHabitPercent: Int,
        thisWeekPrayers: Int,
        thisWeekWakeOnTimeDays: Int,
        thisWeekAvgSleep: Double,
        lastWeekAvgSleep: Double,
        screenTimeDiffPercent: Int,
        dailyReports: List<DailyReport>,
    ): List<String> {
        val list = mutableListOf<String>()

        // 1. Focus Insight
        if (focusDiffPercent > 0) {
            val hours = thisWeekFocusMin / 60
            val mins = thisWeekFocusMin % 60
            list.add("Focus time grew by $focusDiffPercent% this week (${hours}h ${mins}m total). Solid deep work momentum!")
        } else if (thisWeekFocusMin >= 300) {
            val hours = thisWeekFocusMin / 60
            list.add("Sustained strong deep work with over ${hours}h dedicated to research and coding.")
        } else {
            list.add("Next week: try scheduling one dedicated 45-minute focus block in the morning to rebuild flow.")
        }

        // 2. Sleep Insight
        val sleepDiffHours = thisWeekAvgSleep - lastWeekAvgSleep
        if (sleepDiffHours > 0.4) {
            val mins = (sleepDiffHours * 60).toInt()
            list.add("You slept ~$mins min longer on average per night than last week, boosting daytime recovery.")
        } else if (thisWeekAvgSleep >= 7.0) {
            list.add("Maintained a healthy 7+ hour sleep rhythm across the week.")
        } else {
            list.add("Next week: wind down screens 15 minutes before bedtime to support restorative sleep.")
        }

        // 3. Screen Time & Distractions
        val avgFbMin = if (dailyReports.isNotEmpty()) dailyReports.map { it.facebookMinutes }.average().toInt() else 0
        if (screenTimeDiffPercent < 0) {
            list.add("Screen time reduced by ${-screenTimeDiffPercent}% compared to last week.")
        } else if (avgFbMin <= 30) {
            list.add("Social media kept firmly within limits, averaging just $avgFbMin min on Facebook daily.")
        } else {
            list.add("Next week: use app limits to trim social feeds and reclaim extra evening calm.")
        }

        // 4. Faith & Habits
        if (thisWeekPrayers >= 30) {
            list.add("Exceptional prayer consistency: $thisWeekPrayers of 35 daily prayers offered.")
        } else if (thisWeekHabitPercent >= 70) {
            list.add("Strong overall consistency with $thisWeekHabitPercent% of daily routines completed.")
        }

        return list.take(3)
    }

    // ==================== Caching Serializers ====================

    private suspend fun cacheDailyReport(report: DailyReport) {
        val json = JSONObject().apply {
            put("date", report.date.toString())
            put("wakeTarget", report.wakeTarget.toString())
            put("actualWake", report.actualWake?.toString())
            put("wokeOnTime", report.wokeOnTime)
            put("wakeLateMinutes", report.wakeLateMinutes)
            put("bedTarget", report.bedTarget.toString())
            put("actualBed", report.actualBed?.toString())
            put("sleptBeforeTarget", report.sleptBeforeTarget)
            put("hoursSlept", report.hoursSlept)
            put("focusMinutes", report.focusMinutes)
            put("studyMinutes", report.studyMinutes)
            put("researchMinutes", report.researchMinutes)
            put("codeMinutes", report.codeMinutes)
            put("habitsCompleted", report.habitsCompleted)
            put("habitsTotal", report.habitsTotal)
            put("habitPercent", report.habitPercent)
            put("prayersCompleted", report.prayersCompleted)
            put("screenTimeMinutes", report.screenTimeMinutes)
            put("screenTime7DayAvgMinutes", report.screenTime7DayAvgMinutes)
            put("reelsCount", report.reelsCount)
            put("facebookMinutes", report.facebookMinutes)
            put("deadlinesDone", report.deadlinesDone)
            put("classNotesCaptured", report.classNotesCaptured)
            put("improvementScore", report.improvementScore)
            put("badges", JSONArray(report.earnedBadges.map { it.id }))
        }
        reportDao.upsert(
            ReportCacheEntity(
                periodType = "DAILY",
                periodStart = report.date.toString(),
                json = json.toString(),
            )
        )
    }

    private suspend fun cacheWeeklyReport(report: WeeklyReport) {
        val json = JSONObject().apply {
            put("weekNumber", report.weekNumber)
            put("startDate", report.startDate.toString())
            put("endDate", report.endDate.toString())
            put("thisWeekFocusMinutes", report.thisWeekFocusMinutes)
            put("focusDiffPercent", report.focusDiffPercent)
            put("thisWeekHabitPercent", report.thisWeekHabitPercent)
            put("thisWeekPrayersCount", report.thisWeekPrayersCount)
            put("thisWeekScreenTimeMinutes", report.thisWeekScreenTimeMinutes)
            put("screenTimeDiffPercent", report.screenTimeDiffPercent)
            put("bestDayScore", report.bestDayScore)
            put("avgImprovementScore", report.avgImprovementScore)
            put("insights", JSONArray(report.insights))
            put("badges", JSONArray(report.earnedBadges.map { it.id }))
        }
        reportDao.upsert(
            ReportCacheEntity(
                periodType = "WEEKLY",
                periodStart = "week_${report.weekNumber}",
                json = json.toString(),
            )
        )
    }
}
