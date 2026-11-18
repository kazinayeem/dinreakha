package com.example.tracker.data.schedule

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Types + fixed rules + the DEFAULT plan (your original schedule).
 * Since DB v3 the plan itself is editable in Settings → Schedule and stored in Room;
 * [ScheduleConfig.DEFAULT] is what the database is seeded with and what "Reset to defaults" restores.
 */
object Schedule {

    val ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

    /** Bedtime is never later than next wake − this many hours. */
    const val MIN_SLEEP_HOURS = 7L

    const val BUS_ROUTE = "Mirpur-01 Sony Cinema Hall ⇄ DSC"

    /** Monday Maghrib reminder comes after AI Lab ends (17:30). */
    val MONDAY_MAGHRIB_REMINDER: LocalTime = LocalTime.of(17, 35)

    val WEEKLY_REVIEW_DAY: DayOfWeek = FRIDAY

    // NOTE: uses LocalTime.of directly — calling a helper of this object here would create an init cycle.
    enum class Prayer(val key: String, val label: String, val defaultTime: LocalTime) {
        FAJR("fajr", "Fajr", LocalTime.of(5, 5)),
        ZUHR("zuhr", "Zuhr", LocalTime.of(11, 50)),
        ASR("asr", "Asr", LocalTime.of(15, 35)),
        MAGHRIB("maghrib", "Maghrib", LocalTime.of(17, 15)),
        ISHA("isha", "Isha", LocalTime.of(18, 35)),
    }

    data class Phase(
        val number: Int,
        val days: IntRange,
        val wake: LocalTime,
        /** Bedtime; a value before noon means "after midnight" (next calendar day). */
        val bed: LocalTime,
        val prayers: Set<Prayer>,
        /** How many focus blocks (study → research → code, in that order) are expected per day. */
        val expectedFocusBlocks: Int,
    )

    data class ClassSession(
        val day: DayOfWeek, val start: LocalTime, val end: LocalTime,
        val title: String, val room: String, val id: Long = 0,
    )

    enum class BlockType(val habitKey: String, val label: String) {
        STUDY(Habits.COURSE_STUDY, "Course study"),
        RESEARCH(Habits.RESEARCH, "Research"),
        CODE(Habits.CODE_PROJECT, "Code / project"),
    }

    /** [day] null = every day. */
    data class Block(
        val type: BlockType, val start: LocalTime, val end: LocalTime,
        val place: String = "", val day: DayOfWeek? = null, val id: Long = 0,
    )

    enum class BusDirection(val label: String) { TO_CAMPUS("To campus"), HOME("Home") }

    data class Bus(val day: DayOfWeek, val direction: BusDirection, val time: LocalTime, val id: Long = 0)

    enum class Repeat(val label: String) { DAILY("Every day"), WEEKLY("Weekly"), MONTHLY("Monthly") }

    /** A routine you add yourself (Settings → My routines). Ticked on Today as habit key "routine_<id>". */
    data class Routine(
        val id: Long = 0,
        val title: String,
        val icon: String = "✅",
        val repeat: Repeat = Repeat.DAILY,
        /** For WEEKLY: which days. */
        val days: Set<DayOfWeek> = emptySet(),
        /** For MONTHLY: 1..31, clamped to the month's last day. */
        val dayOfMonth: Int = 1,
        /** Optional time; with [remind] a reminder fires then. */
        val time: LocalTime? = null,
        val remind: Boolean = false,
        val active: Boolean = true,
    ) {
        val habitKey get() = "routine_$id"

        fun occursOn(date: LocalDate): Boolean = active && when (repeat) {
            Repeat.DAILY -> true
            Repeat.WEEKLY -> date.dayOfWeek in days
            Repeat.MONTHLY -> date.dayOfMonth == dayOfMonth.coerceIn(1, date.lengthOfMonth())
        }

        fun describe(fmt: (LocalTime) -> String): String = buildString {
            append(when (repeat) {
                Repeat.DAILY -> "Every day"
                Repeat.WEEKLY -> if (days.isEmpty()) "Weekly (no days chosen)"
                    else days.sortedBy { (it.value + 1) % 7 }.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
                Repeat.MONTHLY -> "Monthly on day $dayOfMonth"
            })
            time?.let { append(" · ").append(fmt(it)) }
            if (remind && time != null) append(" · 🔔")
        }
    }

    private fun t(h: Int, m: Int): LocalTime = LocalTime.of(h, m)

    /** Your original plan. Seeded into the database on first run / migration. */
    val DEFAULT_CONFIG: ScheduleConfig by lazy {
        ScheduleConfig(
            planStart = LocalDate.of(2026, 11, 18),
            planDays = 30,
            holidays = mapOf(LocalDate.of(2026, 12, 16) to "Victory Day"),
            phases = listOf(
                Phase(1, 1..7, t(8, 0), t(0, 30), setOf(Prayer.MAGHRIB), 1),
                Phase(2, 8..14, t(7, 30), t(0, 0), setOf(Prayer.MAGHRIB, Prayer.ISHA), 2),
                Phase(3, 15..21, t(7, 0), t(23, 30), setOf(Prayer.MAGHRIB, Prayer.ISHA, Prayer.ZUHR, Prayer.ASR), 3),
                Phase(4, 22..30, t(6, 30), t(23, 0), Prayer.entries.toSet(), 3),
            ),
            wakeOverrides = mapOf(WEDNESDAY to t(6, 0), SATURDAY to t(7, 30), SUNDAY to t(7, 30)),
            classes = listOf(
                ClassSession(SATURDAY, t(10, 0), t(13, 0), "Intro to Machine Learning (SE544)", "KT-604"),
                ClassSession(SATURDAY, t(14, 30), t(16, 0), "Artificial Intelligence (SE333)", "KT-712B"),
                ClassSession(SUNDAY, t(10, 0), t(13, 0), "SE Design Capstone (SE331)", "ANNEX-410 Lab"),
                ClassSession(MONDAY, t(14, 30), t(16, 0), "Information System Security (SE332)", "KT-701B"),
                ClassSession(MONDAY, t(16, 0), t(17, 30), "AI Lab (SE334)", "KT-710"),
                ClassSession(TUESDAY, t(14, 30), t(16, 0), "SE Design Capstone (SE331)", "ONLINE"),
                ClassSession(WEDNESDAY, t(8, 30), t(10, 0), "Artificial Intelligence (SE333)", "KT-101A"),
                ClassSession(WEDNESDAY, t(14, 30), t(16, 0), "Information System Security (SE332)", "KT-611"),
                ClassSession(THURSDAY, t(11, 30), t(13, 0), "Statistics", ""),
            ),
            buses = listOf(
                Bus(SATURDAY, BusDirection.TO_CAMPUS, t(8, 30)), Bus(SUNDAY, BusDirection.TO_CAMPUS, t(8, 30)),
                Bus(MONDAY, BusDirection.TO_CAMPUS, t(12, 0)), Bus(WEDNESDAY, BusDirection.TO_CAMPUS, t(7, 0)),
                Bus(THURSDAY, BusDirection.TO_CAMPUS, t(10, 0)),
                Bus(SATURDAY, BusDirection.HOME, t(16, 20)), Bus(SUNDAY, BusDirection.HOME, t(13, 30)),
                Bus(MONDAY, BusDirection.HOME, t(18, 10)), Bus(WEDNESDAY, BusDirection.HOME, t(16, 20)),
                Bus(THURSDAY, BusDirection.HOME, t(13, 30)),
            ),
            blocks = listOf(
                Block(BlockType.STUDY, t(19, 0), t(19, 45)), // daily, after Isha
                Block(BlockType.RESEARCH, t(9, 0), t(10, 30), day = MONDAY),
                Block(BlockType.RESEARCH, t(9, 0), t(11, 0), day = TUESDAY),
                Block(BlockType.RESEARCH, t(10, 30), t(12, 0), "Library", day = WEDNESDAY),
                Block(BlockType.CODE, t(15, 30), t(17, 0), day = SUNDAY),
                Block(BlockType.CODE, t(19, 45), t(20, 45), "After course study", day = TUESDAY),
                Block(BlockType.CODE, t(15, 30), t(17, 0), day = THURSDAY),
            ),
            screensOffLeadMin = 60,
            routines = emptyList(),
        )
    }
}

/** The whole editable plan. Lives in Room (v3+); this is its in-memory form. */
data class ScheduleConfig(
    val planStart: LocalDate,
    val planDays: Int,
    val holidays: Map<LocalDate, String>,
    val phases: List<Schedule.Phase>,
    val wakeOverrides: Map<DayOfWeek, LocalTime>,
    val classes: List<Schedule.ClassSession>,
    val buses: List<Schedule.Bus>,
    val blocks: List<Schedule.Block>,
    val screensOffLeadMin: Long,
    val routines: List<Schedule.Routine> = emptyList(),
    /** Built-in habit keys the user switched off (left out of the checklist, stats and reports). */
    val hiddenHabits: Set<String> = emptySet(),
    /** User's own names for built-in habits, by key. Keys never change, so old checks still count. */
    val habitNames: Map<String, String> = emptyMap(),
    /** Name of your bus route, shown on bus reminders and the timeline. */
    val busRoute: String = Schedule.BUS_ROUTE,
) {
    val planEnd: LocalDate get() = planStart.plusDays((planDays - 1).toLong())

    fun classesOn(d: DayOfWeek) = classes.filter { it.day == d }.sortedBy { it.start }

    fun busOn(d: DayOfWeek, dir: Schedule.BusDirection) =
        buses.filter { it.day == d && it.direction == dir }.minByOrNull { it.time }?.time

    /** Blocks of a type on a weekday: a day-specific block wins over the daily one. */
    fun blockOn(d: DayOfWeek, type: Schedule.BlockType): Schedule.Block? =
        blocks.firstOrNull { it.type == type && it.day == d } ?: blocks.firstOrNull { it.type == type && it.day == null }

    /** Phase for a day number, clamped into the plan. Falls back to the last/first phase if days have gaps. */
    fun phaseForDay(day: Int): Schedule.Phase =
        phases.firstOrNull { day in it.days }
            ?: phases.sortedBy { it.days.first }.let { ps ->
                if (ps.isEmpty()) Schedule.Phase(1, 1..365, LocalTime.of(7, 0), LocalTime.of(23, 0), emptySet(), 0)
                else if (day < (ps.firstOrNull()?.days?.first ?: 1)) ps.first() else ps.last()
            }

    companion object {
        val DEFAULT: ScheduleConfig get() = Schedule.DEFAULT_CONFIG
    }
}
