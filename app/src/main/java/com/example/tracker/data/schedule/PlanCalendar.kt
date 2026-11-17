package com.example.tracker.data.schedule

import com.example.tracker.data.schedule.Schedule.Block
import com.example.tracker.data.schedule.Schedule.Phase
import com.example.tracker.data.schedule.Schedule.Prayer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Pure, Android-free plan logic. Everything here is unit-tested. */
object PlanCalendar {

    /**
     * The live, editable plan. Updated by ScheduleRepository whenever the DB changes
     * (and loaded explicitly before every reschedule). Tests can set it directly.
     */
    @Volatile var config: ScheduleConfig = ScheduleConfig.DEFAULT

    val planStart: LocalDate get() = config.planStart
    val planDays: Int get() = config.planDays
    val planEnd: LocalDate get() = config.planEnd

    // ---- Days & phases ---------------------------------------------------

    /** Day number in the plan: 1 = Wed 18 Nov 2026. Can be ≤ 0 (before) or > 30 (after). */
    fun dayNumber(date: LocalDate): Int =
        ChronoUnit.DAYS.between(config.planStart, date).toInt() + 1

    fun dateOfDay(day: Int): LocalDate = config.planStart.plusDays((day - 1).toLong())

    fun isInPlan(date: LocalDate) = dayNumber(date) in 1..config.planDays

    /** Phase for a date. Before the plan → phase 1, after the plan → keep the last phase. */
    fun phaseFor(date: LocalDate): Phase {
        val day = dayNumber(date).coerceIn(1, config.planDays)
        return config.phaseForDay(day)
    }

    fun isHoliday(date: LocalDate) = date in config.holidays
    fun holidayName(date: LocalDate): String? = config.holidays[date]

    /** Plan week 1..5 (days 1–7, 8–14, 15–21, 22–28, 29–30). Clamped. */
    fun weekNumber(date: LocalDate): Int = (dayNumber(date).coerceIn(1, config.planDays) - 1) / 7 + 1

    val WEEK_COUNT: Int get() = (config.planDays + 6) / 7

    fun weekDates(week: Int): List<LocalDate> {
        val first = (week - 1) * 7 + 1
        val last = minOf(week * 7, config.planDays)
        return (first..last).map(::dateOfDay)
    }

    /** Every 7th plan day is a review day. */
    fun isReviewDay(date: LocalDate): Boolean = isInPlan(date) && dayNumber(date) % 7 == 0

    // ---- Sleep -----------------------------------------------------------

    /** Wake target for the morning of [date]. */
    fun wakeTime(date: LocalDate): LocalTime {
        val phaseWake = phaseFor(date).wake
        if (isHoliday(date)) return phaseWake
        val override = config.wakeOverrides[date.dayOfWeek] ?: return phaseWake
        return if (override.isBefore(phaseWake)) override else phaseWake
    }

    fun wakeDateTime(date: LocalDate): LocalDateTime = date.atTime(wakeTime(date))

    /**
     * Bedtime for the night that starts on the evening of [date]:
     * min(phase bedtime, next morning's wake − 7 h). May fall after midnight.
     */
    fun bedDateTime(date: LocalDate): LocalDateTime {
        val phaseBed = phaseFor(date).bed
        val phaseBedDt = if (phaseBed.hour < 12) date.plusDays(1).atTime(phaseBed) else date.atTime(phaseBed)
        val capped = wakeDateTime(date.plusDays(1)).minusHours(Schedule.MIN_SLEEP_HOURS)
        return minOf(phaseBedDt, capped)
    }

    fun screensOffDateTime(date: LocalDate): LocalDateTime =
        bedDateTime(date).minusMinutes(config.screensOffLeadMin)

    /** True if [at] lies strictly inside a sleep window (bedtime, next wake). */
    fun isQuietTime(at: LocalDateTime): Boolean {
        val d = at.toLocalDate()
        return listOf(d.minusDays(1), d).any { night ->
            at.isAfter(bedDateTime(night)) && at.isBefore(wakeDateTime(night.plusDays(1)))
        }
    }

    // ---- Focus blocks ----------------------------------------------------

    data class FocusBlock(val block: Block, val expected: Boolean)

    /**
     * Focus blocks scheduled on [date], in priority order study → research → code.
     * Only the first N are "expected" (N from the phase); the rest are optional.
     */
    fun focusBlocks(date: LocalDate): List<FocusBlock> {
        val dow = date.dayOfWeek
        val blocks = Schedule.BlockType.entries.mapNotNull { config.blockOn(dow, it) }
        val n = phaseFor(date).expectedFocusBlocks
        return blocks.mapIndexed { i, b -> FocusBlock(b, expected = i < n) }
    }

    // ---- Checklist -------------------------------------------------------

    data class ChecklistItem(val key: String, val label: String, val expected: Boolean)

    fun checklist(date: LocalDate): List<ChecklistItem> {
        val blocks = focusBlocks(date).associateBy { it.block.type.habitKey }
        fun block(key: String) = ChecklistItem(key, Habits.label(key), blocks[key]?.expected == true)
        fun item(key: String) = ChecklistItem(key, Habits.label(key), true)
        val goal = phaseFor(date).prayers
        return buildList {
            add(item(Habits.WOKE_ON_TIME))
            add(item(Habits.MORNING_ROUTINE))
            add(block(Habits.COURSE_STUDY))
            add(block(Habits.CODE_PROJECT))
            add(block(Habits.RESEARCH))
            add(item(Habits.WALK))
            add(item(Habits.NAP))
            add(item(Habits.SCREENS_OFF))
            Prayer.entries.forEach { p ->
                add(ChecklistItem(Habits.prayerKey(p), Habits.label(Habits.prayerKey(p)), p in goal))
            }
            // Classes you attend count on class days (not on holidays).
            if (!isHoliday(date)) config.classesOn(date.dayOfWeek).forEach { c ->
                add(ChecklistItem(Habits.classKey(c.start), c.title.substringBefore(" (").trim(), true)) // short course name
            }
            // Your own routines.
            config.routines.filter { it.occursOn(date) }.forEach { r ->
                add(ChecklistItem(r.habitKey, "${r.icon} ${r.title}", true))
            }
        }
    }

    /** Completion 0..1 of expected items given the set of done habit keys. */
    fun completion(date: LocalDate, doneKeys: Set<String>): Float {
        val expected = checklist(date).filter { it.expected }
        if (expected.isEmpty()) return 0f
        return expected.count { it.key in doneKeys }.toFloat() / expected.size
    }

    // ---- Timeline --------------------------------------------------------

    enum class EventType { WAKE, CLASS, BUS_TO_CAMPUS, BUS_HOME, PRAYER, FOCUS, ROUTINE, SCREENS_OFF, BEDTIME }

    data class PlanEvent(
        val type: EventType,
        val title: String,
        val detail: String,
        val start: LocalDateTime,
        val end: LocalDateTime? = null,
        val optional: Boolean = false,
        val habitKey: String? = null,
    )

    fun timeline(date: LocalDate, prayerTimes: Map<Prayer, LocalTime>): List<PlanEvent> {
        val dow = date.dayOfWeek
        val holiday = isHoliday(date)
        val goal = phaseFor(date).prayers
        val events = mutableListOf<PlanEvent>()

        events += PlanEvent(EventType.WAKE, "Wake up", "Light + no phone for 30 min", wakeDateTime(date),
            habitKey = Habits.WOKE_ON_TIME)

        if (!holiday) {
            config.classesOn(dow).forEach { c ->
                events += PlanEvent(EventType.CLASS, c.title, c.room, date.atTime(c.start), date.atTime(c.end),
                    habitKey = Habits.classKey(c.start))
            }
            config.busOn(dow, Schedule.BusDirection.TO_CAMPUS)?.let {
                events += PlanEvent(EventType.BUS_TO_CAMPUS, "Bus to campus", Schedule.BUS_ROUTE, date.atTime(it))
            }
            config.busOn(dow, Schedule.BusDirection.HOME)?.let {
                events += PlanEvent(EventType.BUS_HOME, "Bus home", Schedule.BUS_ROUTE, date.atTime(it))
            }
        }

        Prayer.entries.forEach { p ->
            val time = prayerTimes[p] ?: p.defaultTime
            events += PlanEvent(EventType.PRAYER, p.label, if (p in goal) "In this week's goal" else "Optional",
                date.atTime(time), optional = p !in goal, habitKey = Habits.prayerKey(p))
        }

        focusBlocks(date).forEach { fb ->
            val b = fb.block
            events += PlanEvent(EventType.FOCUS, b.type.label, b.place, date.atTime(b.start), date.atTime(b.end),
                optional = !fb.expected, habitKey = b.type.habitKey)
        }

        config.routines.filter { it.occursOn(date) && it.time != null }.forEach { r ->
            events += PlanEvent(EventType.ROUTINE, "${r.icon} ${r.title}", "My routine", date.atTime(r.time!!), habitKey = r.habitKey)
        }

        events += PlanEvent(EventType.SCREENS_OFF, "Screens off", "Wind down", screensOffDateTime(date),
            habitKey = Habits.SCREENS_OFF)
        events += PlanEvent(EventType.BEDTIME, "Go to sleep", "Next wake ${TimeFormat.format(wakeTime(date.plusDays(1)))}",
            bedDateTime(date))

        return events.sortedBy { it.start }
    }
}
