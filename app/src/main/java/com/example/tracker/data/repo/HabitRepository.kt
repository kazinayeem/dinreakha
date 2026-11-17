package com.example.tracker.data.repo

import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.DayLog
import com.example.tracker.data.db.HabitCheck
import com.example.tracker.data.db.WeeklyReview
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import com.example.tracker.data.schedule.ScheduleConfig
import java.time.LocalDate
import java.time.LocalTime

data class DayState(
    val date: LocalDate,
    val done: Map<String, LocalTime?>,
    val log: DayLog?,
) {
    val doneKeys get() = done.keys
    val completion get() = PlanCalendar.completion(date, doneKeys)
}

class HabitRepository(private val db: AppDatabase, private val config: Flow<ScheduleConfig>) {
    private val habits = db.habitCheckDao()
    private val logs = db.dayLogDao()
    private val reviews = db.weeklyReviewDao()

    fun observeDay(date: LocalDate): Flow<DayState> =
        combine(habits.observeForDate(date), logs.observe(date)) { checks, log ->
            DayState(date, checks.filter { it.done }.associate { it.habitKey to it.doneAt }, log)
        }

    /** Day states for the whole plan (and a day around it). Follows schedule edits (start date / length). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observePlan(): Flow<Map<LocalDate, DayState>> = config.flatMapLatest { cfg ->
        val from = cfg.planStart.minusDays(1)
        val to = cfg.planEnd.plusDays(1)
        combine(habits.observeRange(from, to), logs.observeRange(from, to)) { checks, dayLogs ->
            val byDate = checks.filter { it.done }.groupBy { it.date }
            val logByDate = dayLogs.associateBy { it.date }
            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith { d ->
                DayState(d, byDate[d].orEmpty().associate { it.habitKey to it.doneAt }, logByDate[d])
            }
        }
    }

    suspend fun setHabit(date: LocalDate, key: String, done: Boolean, at: LocalTime? = null) {
        habits.put(HabitCheck(date = date, habitKey = key, done = done,
            doneAt = if (done) (at ?: LocalTime.now(Schedule.ZONE).withNano(0)) else null))
    }

    suspend fun doneCount(date: LocalDate): Pair<Int, Int> {
        val done = habits.getForDate(date).filter { it.done }.map { it.habitKey }.toSet()
        val expected = PlanCalendar.checklist(date).filter { it.expected }
        return expected.count { it.key in done } to expected.size
    }

    suspend fun setMood(date: LocalDate, mood: Int?) {
        val old = logs.get(date)
        logs.upsert((old ?: DayLog(date)).copy(mood = mood))
    }

    suspend fun setNote(date: LocalDate, note: String) {
        val old = logs.get(date)
        logs.upsert((old ?: DayLog(date)).copy(note = note))
    }

    fun observeReview(week: Int): Flow<WeeklyReview?> = reviews.observe(week)
    suspend fun saveReview(r: WeeklyReview) = reviews.upsert(r)
}
