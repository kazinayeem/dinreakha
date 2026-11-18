package com.example.tracker.data.schedule

import com.example.tracker.data.schedule.Schedule.Prayer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** How a reminder's time is set in Settings. */
enum class Timing { OFFSET, FIXED_TIME, PLAN }

enum class ChannelGroup(val label: String) { WAKE("Wake-up"), REMINDERS("Reminders"), PRAYERS("Prayers") }

enum class SoundMode(val label: String) { SOUND("Sound"), VIBRATE("Vibrate only"), SILENT("Silent") }

/** Every reminder type, each with its own on/off, timing, sound and ringtone. */
enum class ReminderCategory(
    val key: String,
    val label: String,
    val group: ChannelGroup,
    val timing: Timing,
    val defaultOffsetMin: Int = 0,
    val defaultTime: LocalTime? = null,
    val defaultSound: SoundMode = SoundMode.SOUND,
) {
    WAKE("wake", "Wake-up alarm", ChannelGroup.WAKE, Timing.PLAN),
    BUS("bus", "Bus — leave now", ChannelGroup.REMINDERS, Timing.OFFSET, 30),
    CLASS("class", "Class starting", ChannelGroup.REMINDERS, Timing.OFFSET, 10),
    PRAYERS("prayers", "Prayers", ChannelGroup.PRAYERS, Timing.OFFSET, 0),
    STUDY("study", "Course study", ChannelGroup.REMINDERS, Timing.OFFSET, 5),
    RESEARCH("research", "Research", ChannelGroup.REMINDERS, Timing.OFFSET, 5),
    CODE("code", "Code / project", ChannelGroup.REMINDERS, Timing.OFFSET, 5),
    SCREENS_OFF("screens_off", "Screens off", ChannelGroup.REMINDERS, Timing.PLAN),
    BEDTIME("bedtime", "Go to sleep", ChannelGroup.REMINDERS, Timing.PLAN),
    WEEKLY_REVIEW("weekly_review", "Weekly review (Friday)", ChannelGroup.REMINDERS, Timing.FIXED_TIME, defaultTime = LocalTime.of(20, 0)),
    EXPENSE_LOG("expense_log", "Log today's expenses", ChannelGroup.REMINDERS, Timing.FIXED_TIME, defaultTime = LocalTime.of(21, 30)),
    RECURRING("recurring", "Recurring bills due", ChannelGroup.REMINDERS, Timing.FIXED_TIME, defaultTime = LocalTime.of(10, 0)),
    ROUTINE("routine", "My routines", ChannelGroup.REMINDERS, Timing.PLAN),
    CALENDAR("calendar", "Calendar events", ChannelGroup.REMINDERS, Timing.PLAN),
    /** Three a day: 15 min after waking, midday ([defaultTime], editable) and evening (ReminderConfig.motivationEvening). */
    MOTIVATION("motivation", "Motivation quotes", ChannelGroup.REMINDERS, Timing.FIXED_TIME,
        defaultTime = LocalTime.of(13, 30), defaultSound = SoundMode.VIBRATE);

    val defaultPref get() = ReminderPref(offsetMin = defaultOffsetMin, time = defaultTime, sound = defaultSound)
}

data class ReminderPref(
    val enabled: Boolean = true,
    /** Minutes before the event (Timing.OFFSET). */
    val offsetMin: Int = 0,
    /** Clock time (Timing.FIXED_TIME). */
    val time: LocalTime? = null,
    val sound: SoundMode = SoundMode.SOUND,
    /** content:// ringtone URI, or null for the default sound. */
    val ringtone: String? = null,
)

enum class QuietMode(val label: String) { PLAN("Bedtime → wake time"), CUSTOM("Custom hours"), OFF("Off") }

data class QuietHours(
    val mode: QuietMode = QuietMode.PLAN,
    val start: LocalTime = LocalTime.of(23, 0),
    val end: LocalTime = LocalTime.of(6, 30),
) {
    fun isQuiet(at: LocalDateTime): Boolean = when (mode) {
        QuietMode.OFF -> false
        QuietMode.PLAN -> PlanCalendar.isQuietTime(at)
        QuietMode.CUSTOM -> {
            val t = at.toLocalTime()
            if (start < end) t > start && t < end else t > start || t < end // wraps midnight
        }
    }
}

/** Minimal view of a recurring finance item, so the planner stays free of Room types. */
data class RecurringDue(
    val id: Long,
    val title: String,
    val amountText: String,
    val dayOfMonth: Int,
    val lastConfirmedMonth: String?,
)

data class ReminderConfig(
    val prayerTimes: Map<Prayer, LocalTime> = Prayer.entries.associateWith { it.defaultTime },
    val prefs: Map<ReminderCategory, ReminderPref> = emptyMap(),
    /** Master switch: off = no reminders at all. */
    val masterEnabled: Boolean = true,
    /** Everything except the wake-up alarm is silent on this date. */
    val mutedDate: LocalDate? = null,
    val recurring: List<RecurringDue> = emptyList(),
    val quiet: QuietHours = QuietHours(),
    /** Holiday mode: no class & bus reminders between these dates (inclusive). */
    val holidayFrom: LocalDate? = null,
    val holidayTo: LocalDate? = null,
    /** Pre-computed reminders from other modules (calendar events), one slot each. */
    val extra: List<Reminder> = emptyList(),
    val quoteCategory: com.example.tracker.data.repo.QuoteCategory = com.example.tracker.data.repo.QuoteCategory.ALL,
    val quoteLanguage: com.example.tracker.data.repo.QuoteLanguage = com.example.tracker.data.repo.QuoteLanguage.BOTH,
    val motivationEvening: LocalTime = LocalTime.of(20, 30),
) {
    fun pref(c: ReminderCategory): ReminderPref = prefs[c] ?: c.defaultPref
    fun isEnabled(c: ReminderCategory) = masterEnabled && pref(c).enabled
    fun inHolidayMode(d: LocalDate) =
        holidayFrom != null && holidayTo != null && !d.isBefore(holidayFrom) && !d.isAfter(holidayTo)

    /** Convenience for tests / callers: config with exactly these categories enabled. */
    fun withEnabled(enabled: Set<ReminderCategory>) =
        copy(prefs = ReminderCategory.entries.associateWith { pref(it).copy(enabled = it in enabled) })
}

data class Reminder(
    /** Stable slot id; one alarm is scheduled per slot (its next occurrence). */
    val slot: String,
    val category: ReminderCategory,
    val at: LocalDateTime,
    val title: String,
    val text: String,
    /** Plan date the reminder belongs to (bedtime after midnight still belongs to the evening before). */
    val date: LocalDate,
    /** Habit marked done by the notification's "Done" action, or null for no Done button. */
    val habitKey: String? = null,
)

object ReminderPlanner {

    const val SLOT_WAKE = "wake"
    const val SLOT_SCREENS_OFF = "screens_off"
    const val SLOT_BEDTIME = "bedtime"
    const val SLOT_BUS = "bus"
    const val SLOT_CLASS = "class"
    const val SLOT_WEEKLY = "weekly_review"
    const val SLOT_WEEKLY_CARD = "weekly_card"
    const val SLOT_EXPENSE_LOG = "expense_log"
    const val SLOT_SNOOZE = "wake_snooze"
    val SLOT_MOTIVATION = listOf("motivation_morning", "motivation_midday", "motivation_evening")
    private const val RECURRING_PREFIX = "recurring_"
    fun recurringSlot(id: Long) = "$RECURRING_PREFIX$id"
    fun recurringIdOf(slot: String): Long? =
        slot.removePrefix(RECURRING_PREFIX).takeIf { slot.startsWith(RECURRING_PREFIX) }?.toLongOrNull()
    private const val ROUTINE_PREFIX = "routine_"
    fun routineSlot(id: Long) = "$ROUTINE_PREFIX$id"
    fun routineIdOf(slot: String): Long? =
        slot.removePrefix(ROUTINE_PREFIX).takeIf { slot.startsWith(ROUTINE_PREFIX) }?.toLongOrNull()
    fun prayerSlot(p: Prayer) = "prayer_${p.key}"
    fun blockSlot(t: Schedule.BlockType) = "block_${t.name.lowercase()}"

    fun categoryOf(t: Schedule.BlockType) = when (t) {
        Schedule.BlockType.STUDY -> ReminderCategory.STUDY
        Schedule.BlockType.RESEARCH -> ReminderCategory.RESEARCH
        Schedule.BlockType.CODE -> ReminderCategory.CODE
    }

    /** Every static slot — used for stable request codes and for cancelling. */
    val ALL_SLOTS: List<String> = buildList {
        add(SLOT_WAKE); add(SLOT_SCREENS_OFF); add(SLOT_BEDTIME); add(SLOT_BUS); add(SLOT_WEEKLY)
        add(SLOT_EXPENSE_LOG)
        Prayer.entries.forEach { add(prayerSlot(it)) }
        Schedule.BlockType.entries.forEach { add(blockSlot(it)) }
        add(SLOT_CLASS); add(SLOT_SNOOZE); add(SLOT_WEEKLY_CARD) // appended last so older request codes stay stable
        addAll(SLOT_MOTIVATION)
    }

    private val HM get() = TimeFormat.formatter

    /** All raw reminder candidates that belong to plan date [date] (before toggle/quiet/mute filters). */
    fun candidatesFor(date: LocalDate, config: ReminderConfig): List<Reminder> = buildList {
        val holiday = PlanCalendar.isHoliday(date) || config.inHolidayMode(date)
        val dow = date.dayOfWeek
        val phase = PlanCalendar.phaseFor(date)
        val cfg = PlanCalendar.config
        fun before(c: ReminderCategory) = config.pref(c).offsetMin.toLong()
        fun fixed(c: ReminderCategory) = config.pref(c).time ?: c.defaultTime ?: LocalTime.NOON

        val wake = PlanCalendar.wakeDateTime(date)
        add(Reminder(SLOT_WAKE, ReminderCategory.WAKE, wake, "Wake up — ${HM.format(wake)}",
            "Open the curtains, get light, no phone for 30 min.", date, Habits.WOKE_ON_TIME))

        val bed = PlanCalendar.bedDateTime(date)
        add(Reminder(SLOT_SCREENS_OFF, ReminderCategory.SCREENS_OFF, PlanCalendar.screensOffDateTime(date),
            "Screens off", "Bedtime is ${HM.format(bed)}. Put the phone away and wind down.",
            date, Habits.SCREENS_OFF))
        add(Reminder(SLOT_BEDTIME, ReminderCategory.BEDTIME, bed, "Go to sleep",
            "Wake target tomorrow: ${HM.format(PlanCalendar.wakeTime(date.plusDays(1)))}", date))

        // A prayer hidden from the checklist gets no reminder either.
        phase.prayers.filter { Habits.prayerKey(it) !in cfg.hiddenHabits }.forEach { p ->
            val prayerTime = config.prayerTimes[p] ?: p.defaultTime
            val time = if (p == Prayer.MAGHRIB && dow == DayOfWeek.MONDAY)
                maxOf(prayerTime, Schedule.MONDAY_MAGHRIB_REMINDER) else prayerTime
            add(Reminder(prayerSlot(p), ReminderCategory.PRAYERS, date.atTime(time).minusMinutes(before(ReminderCategory.PRAYERS)),
                "${p.label} prayer", "Time for ${p.label}.", date, Habits.prayerKey(p)))
        }

        if (!holiday) {
            cfg.busOn(dow, Schedule.BusDirection.TO_CAMPUS)?.let { bus ->
                add(Reminder(SLOT_BUS, ReminderCategory.BUS, date.atTime(bus).minusMinutes(before(ReminderCategory.BUS)),
                    "Leave now for the ${HM.format(bus)} bus", Schedule.BUS_ROUTE, date))
            }
            cfg.classesOn(dow).forEach { c ->
                add(Reminder(SLOT_CLASS, ReminderCategory.CLASS, date.atTime(c.start).minusMinutes(before(ReminderCategory.CLASS)),
                    "${c.title} at ${HM.format(c.start)}", listOf(c.room, "until ${HM.format(c.end)}").filter { it.isNotBlank() }
                        .joinToString(" · "), date))
            }
        }

        PlanCalendar.focusBlocks(date).filter { it.expected }.forEach { fb ->
            val b = fb.block
            val cat = categoryOf(b.type)
            val lead = before(cat)
            add(Reminder(blockSlot(b.type), cat, date.atTime(b.start).minusMinutes(lead),
                if (lead > 0) "${b.type.label} in $lead min" else "${b.type.label} now",
                "${HM.format(b.start)}–${HM.format(b.end)}" + if (b.place.isNotEmpty()) " · ${b.place}" else "",
                date, b.type.habitKey))
        }

        if (dow == Schedule.WEEKLY_REVIEW_DAY) {
            add(Reminder(SLOT_WEEKLY, ReminderCategory.WEEKLY_REVIEW, date.atTime(fixed(ReminderCategory.WEEKLY_REVIEW)),
                "Weekly review", "Five quiet minutes: what went well, what got in the way, one small change.", date))
            add(Reminder(SLOT_WEEKLY_CARD, ReminderCategory.WEEKLY_REVIEW, date.atTime(LocalTime.of(21, 0)),
                "Your weekly card is ready ✨", "See your weekly achievements and export your story card.", date))
        }

        // Motivation: morning (after waking), midday and evening — one quote each, never repeated until the list wraps.
        listOf(
            Triple("Good morning", wake.plusMinutes(15), 0),
            Triple("Midday boost", date.atTime(fixed(ReminderCategory.MOTIVATION)), 1),
            Triple("Evening reflection", date.atTime(config.motivationEvening), 2),
        ).forEach { (title, at, i) ->
            val q = com.example.tracker.data.quotes.MotivationQuotes.pick(date.toEpochDay(), i, config.quoteCategory, config.quoteLanguage)
            add(Reminder(SLOT_MOTIVATION[i], ReminderCategory.MOTIVATION, at, title, "${q.text}\n— ${q.source}", date))
        }

        add(Reminder(SLOT_EXPENSE_LOG, ReminderCategory.EXPENSE_LOG, date.atTime(fixed(ReminderCategory.EXPENSE_LOG)),
            "Log today's expenses", "Ten seconds now saves guessing later.", date))

        cfg.routines.filter { it.remind && it.time != null && it.occursOn(date) }.forEach { r ->
            add(Reminder(routineSlot(r.id), ReminderCategory.ROUTINE, date.atTime(r.time!!), "${r.icon} ${r.title}",
                r.describe { HM.format(it) }, date, r.habitKey))
        }

        config.extra.filter { it.date == date }.forEach { add(it) }

        val month = YearMonth.from(date)
        config.recurring.forEach { r ->
            val due = month.atDay(r.dayOfMonth.coerceIn(1, month.lengthOfMonth()))
            if (due == date && r.lastConfirmedMonth != month.toString()) {
                add(Reminder(recurringSlot(r.id), ReminderCategory.RECURRING, date.atTime(fixed(ReminderCategory.RECURRING)),
                    "Due today: ${r.title}", "${r.amountText} — tap Add to record it.", date))
            }
        }
    }

    /** Applies the master switch, per-type toggles, mute-today and quiet hours. */
    fun isAllowed(r: Reminder, config: ReminderConfig): Boolean {
        if (!config.isEnabled(r.category)) return false
        if (r.category == ReminderCategory.WAKE) return true // the only thing allowed during sleep / mute
        if (config.mutedDate != null &&
            (r.date == config.mutedDate || r.at.toLocalDate() == config.mutedDate)) return false
        return !config.quiet.isQuiet(r.at)
    }

    /** Allowed reminders belonging to [date], sorted by time. */
    fun remindersFor(date: LocalDate, config: ReminderConfig): List<Reminder> =
        candidatesFor(date, config).filter { isAllowed(it, config) }.sortedBy { it.at }

    /**
     * The next upcoming occurrence (strictly after [now]) of every slot, searching [horizonDays] ahead.
     * Slots with nothing upcoming are absent from the map.
     */
    fun nextPerSlot(now: LocalDateTime, config: ReminderConfig, horizonDays: Int = 14): Map<String, Reminder> {
        if (!config.masterEnabled) return emptyMap()
        val start = now.toLocalDate().minusDays(1) // the previous evening's bedtime may be after midnight
        return (0..horizonDays + 1).asSequence()
            .flatMap { remindersFor(start.plusDays(it.toLong()), config) }
            .filter { it.at.isAfter(now) }
            .groupBy { it.slot }
            .mapValues { (_, list) -> list.minBy { it.at } }
    }

    /** The very next reminder of any kind — shown in the status-bar countdown. */
    fun nextOverall(now: LocalDateTime, config: ReminderConfig): Reminder? =
        nextPerSlot(now, config).values.filter { it.category != ReminderCategory.MOTIVATION }.minByOrNull { it.at }
}
