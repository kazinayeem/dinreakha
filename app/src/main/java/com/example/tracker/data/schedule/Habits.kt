package com.example.tracker.data.schedule

/** Stable keys stored in habit_check.habit_key. Never rename one: old rows would be orphaned. */
object Habits {
    const val WOKE_ON_TIME = "woke_on_time"
    const val MORNING_ROUTINE = "morning_routine"
    const val COURSE_STUDY = "course_study"
    const val CODE_PROJECT = "code_project"
    const val RESEARCH = "research"
    const val WALK = "walk"
    const val NAP = "nap"
    const val SCREENS_OFF = "screens_off"

    fun prayerKey(p: Schedule.Prayer) = "prayer_${p.key}"

    /** One key per class slot of the day, e.g. "class_1430" (stable even if the class is renamed). */
    fun classKey(start: java.time.LocalTime) = "class_%02d%02d".format(start.hour, start.minute)

    val LABELS: Map<String, String> = buildMap {
        put(WOKE_ON_TIME, "Woke on time")
        put(MORNING_ROUTINE, "Morning routine")
        put(COURSE_STUDY, "Course study")
        put(CODE_PROJECT, "Code")
        put(RESEARCH, "Research")
        put(WALK, "Walk 20 min")
        put(NAP, "Short nap")
        put(SCREENS_OFF, "Screens off")
        Schedule.Prayer.entries.forEach { put(prayerKey(it), it.label) }
    }

    /** The built-in habits the user can hide or rename, in checklist order. */
    val BUILT_IN: List<String> = listOf(
        WOKE_ON_TIME, MORNING_ROUTINE, COURSE_STUDY, CODE_PROJECT, RESEARCH, WALK, NAP, SCREENS_OFF,
    ) + Schedule.Prayer.entries.map(::prayerKey)

    /** Visible built-ins (hidden ones left out). */
    fun visibleBuiltIns(): List<String> = BUILT_IN.filter { it !in PlanCalendar.config.hiddenHabits }

    fun defaultLabel(key: String) = LABELS[key] ?: key
    fun label(key: String) = PlanCalendar.config.habitNames[key] ?: defaultLabel(key)
}
