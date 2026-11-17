package com.example.tracker.data.focus

import com.example.tracker.data.schedule.Habits
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime

/** What a focus session is for. Study / research / code tick their habit when a session finishes. */
enum class FocusType(val label: String, val habitKey: String?) {
    STUDY("Course study", Habits.COURSE_STUDY),
    RESEARCH("Research", Habits.RESEARCH),
    CODE("Code / project", Habits.CODE_PROJECT),
    OTHER("Other", null),
}

/**
 * The running timer — pure state, no Android. Stored as JSON so it survives the app being killed;
 * the end is driven by an exact alarm, the visible countdown by the notification chronometer.
 */
data class FocusRun(
    val type: FocusType,
    val plannedMinutes: Int,
    val startedAt: LocalDateTime,
    /** Total time spent paused before the current pause (ms). */
    val pausedMs: Long = 0,
    /** Non-null while paused. */
    val pausedAt: LocalDateTime? = null,
    val dnd: Boolean = false,
    /** Label shown in the notification, e.g. "Research — Library". */
    val title: String = type.label,
) {
    val isPaused get() = pausedAt != null

    /** Focused time so far (pauses excluded). */
    fun elapsed(now: LocalDateTime): Duration {
        val until = pausedAt ?: now
        return Duration.between(startedAt, until).minusMillis(pausedMs).coerceAtLeast0()
    }

    fun remaining(now: LocalDateTime): Duration =
        Duration.ofMinutes(plannedMinutes.toLong()).minus(elapsed(now)).coerceAtLeast0()

    fun isDone(now: LocalDateTime) = remaining(now).isZero

    /** Wall-clock end if it keeps running from [now] (meaningless while paused). */
    fun endsAt(now: LocalDateTime): LocalDateTime = now.plus(remaining(now))

    fun pause(now: LocalDateTime) = if (isPaused) this else copy(pausedAt = now)

    fun resume(now: LocalDateTime) =
        if (!isPaused) this else copy(pausedMs = pausedMs + Duration.between(pausedAt, now).toMillis(), pausedAt = null)

    fun extend(minutes: Int) = copy(plannedMinutes = (plannedMinutes + minutes).coerceIn(1, 600))

    fun toJson(): String = JSONObject()
        .put("type", type.name).put("planned", plannedMinutes).put("started", startedAt.toString())
        .put("pausedMs", pausedMs).put("pausedAt", pausedAt?.toString() ?: JSONObject.NULL)
        .put("dnd", dnd).put("title", title).toString()

    companion object {
        val PRESETS = listOf(25, 45, 90)

        fun fromJson(s: String?): FocusRun? = s?.let {
            runCatching {
                val o = JSONObject(it)
                FocusRun(FocusType.valueOf(o.getString("type")), o.getInt("planned"), LocalDateTime.parse(o.getString("started")),
                    o.optLong("pausedMs"), if (o.isNull("pausedAt")) null else LocalDateTime.parse(o.getString("pausedAt")),
                    o.optBoolean("dnd"), o.optString("title", FocusType.valueOf(o.getString("type")).label))
            }.getOrNull()
        }
    }
}

private fun Duration.coerceAtLeast0(): Duration = if (isNegative) Duration.ZERO else this

/** A finished (or stopped) session, as logged. */
data class FocusSessionInfo(
    val id: Long,
    val type: FocusType,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val minutes: Int,
    val note: String,
)

object FocusMath {
    /** Minutes worth logging: whole minutes focused, at least 1 if anything was done. */
    fun loggedMinutes(run: FocusRun, now: LocalDateTime): Int = (run.elapsed(now).toMinutes()).toInt().coerceAtMost(run.plannedMinutes)

    /** A session ticks its habit only when it ran to the end (or at least 80% of it when stopped). */
    fun ticksHabit(run: FocusRun, now: LocalDateTime): Boolean =
        run.type.habitKey != null && loggedMinutes(run, now) * 100 >= run.plannedMinutes * 80

    /** Minutes per type for a set of sessions. */
    fun minutesByType(sessions: List<FocusSessionInfo>): Map<FocusType, Int> =
        sessions.groupBy { it.type }.mapValues { (_, l) -> l.sumOf { it.minutes } }

    fun formatHm(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
