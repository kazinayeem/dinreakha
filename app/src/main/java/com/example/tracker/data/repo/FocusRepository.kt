package com.example.tracker.data.repo

import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.FocusSessionEntity
import com.example.tracker.data.db.Setting
import com.example.tracker.data.focus.FocusMath
import com.example.tracker.data.focus.FocusRun
import com.example.tracker.data.focus.FocusSessionInfo
import com.example.tracker.data.focus.FocusType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/** The running timer (persisted in `settings`) and the log of finished sessions (`focus_session`). */
class FocusRepository(private val db: AppDatabase, private val habits: HabitRepository) {
    private val settings = db.settingsDao()
    private val dao = db.focusDao()

    val active: Flow<FocusRun?> = settings.observeAll().map { rows -> FocusRun.fromJson(rows.firstOrNull { it.key == KEY }?.value) }
    suspend fun current(): FocusRun? = FocusRun.fromJson(settings.get(KEY))
    suspend fun setActive(run: FocusRun?) = if (run == null) settings.remove(KEY) else settings.put(Setting(KEY, run.toJson()))

    val sessions: Flow<List<FocusSessionInfo>> = dao.observeAll().map { list -> list.mapNotNull(::toInfo) }
    suspend fun allSessions(): List<FocusSessionInfo> = dao.all().mapNotNull(::toInfo)

    private fun toInfo(e: FocusSessionEntity) = runCatching { FocusType.valueOf(e.type) }.getOrNull()
        ?.let { FocusSessionInfo(e.id, it, e.start, e.end, e.minutes, e.note) }

    /**
     * Ends the running timer: logs the minutes actually focused, ticks the habit when it ran (nearly) to the end,
     * and clears the active state. Returns the logged session, or null if under a minute was done.
     */
    suspend fun finish(now: LocalDateTime): Pair<FocusSessionInfo, Boolean>? {
        val run = current() ?: return null
        setActive(null)
        val minutes = FocusMath.loggedMinutes(run, now)
        if (minutes < 1) return null
        val end = run.pausedAt ?: now
        val id = dao.insert(FocusSessionEntity(type = run.type.name, start = run.startedAt, end = end, minutes = minutes))
        val ticked = FocusMath.ticksHabit(run, now)
        if (ticked) run.type.habitKey?.let { habits.setHabit(run.startedAt.toLocalDate(), it, true, end.toLocalTime().withNano(0)) }
        return FocusSessionInfo(id, run.type, run.startedAt, end, minutes, "") to ticked
    }

    suspend fun setNote(id: Long, note: String) = dao.setNote(id, note.trim())
    suspend fun delete(id: Long) = dao.delete(id)

    // remembered "Do not disturb" filter to restore after the session
    suspend fun savedDndFilter(): Int? = settings.get(DND_KEY)?.toIntOrNull()
    suspend fun setSavedDndFilter(v: Int?) = if (v == null) settings.remove(DND_KEY) else settings.put(Setting(DND_KEY, v.toString()))

    companion object {
        private const val KEY = "focus_active"
        private const val DND_KEY = "focus_dnd_prev"
    }
}
