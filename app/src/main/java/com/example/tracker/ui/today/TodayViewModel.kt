package com.example.tracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tracker.AppContainer
import com.example.tracker.data.finance.FinanceMath
import com.example.tracker.data.repo.DayState
import com.example.tracker.data.weather.WeatherState
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Emits the current Dhaka date, re-checking every 30 s (so the screen rolls over at midnight). */
fun currentDateFlow(): Flow<LocalDate> = flow {
    while (true) { emit(LocalDate.now(Schedule.ZONE)); delay(30_000) }
}.distinctUntilChanged()

fun nowFlow(): Flow<LocalDateTime> = flow {
    while (true) { emit(LocalDateTime.now(Schedule.ZONE)); delay(30_000) }
}

data class TodayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val day: DayState,
    val timeline: List<PlanCalendar.PlanEvent>,
    val checklist: List<PlanCalendar.ChecklistItem>,
    val now: LocalDateTime,
    val spentToday: Long,
    val startAgain: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer, fixedDate: LocalDate?) : ViewModel() {

    private val dateFlow: Flow<LocalDate> = fixedDate?.let { flowOf(it) } ?: currentDateFlow()

    val ui: StateFlow<TodayUi?> = combine(dateFlow, c.scheduleState) { d, _ -> d }.flatMapLatest { date ->
        val yesterday = date.minusDays(1)
        combine(
            c.habits.observeDay(date),
            c.habits.observeDay(yesterday),
            c.settings.settings,
            c.finance.txnsFlow,
            nowFlow(),
        ) { day, prev, settings, txns, now ->
            val isToday = date == now.toLocalDate()
            TodayUi(
                date = date,
                isToday = isToday,
                day = day,
                timeline = PlanCalendar.timeline(date, settings.prayerTimes),
                checklist = PlanCalendar.checklist(date),
                now = now,
                spentToday = FinanceMath.spentOn(date, txns),
                // Gentle restart prompt — only when yesterday was a plan day with nothing ticked.
                startAgain = isToday && PlanCalendar.dayNumber(date) in 2..PlanCalendar.planDays && prev.doneKeys.isEmpty(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weather: StateFlow<WeatherState> = c.weather.weatherFlow

    fun refreshWeather() = viewModelScope.launch {
        c.weather.refresh(force = true)
    }

    fun toggle(date: LocalDate, key: String, done: Boolean) = viewModelScope.launch {
        c.habits.setHabit(date, key, done)
        c.scheduler.refreshStatus()
    }

    fun setMood(date: LocalDate, mood: Int?) = viewModelScope.launch { c.habits.setMood(date, mood) }
    fun setNote(date: LocalDate, note: String) = viewModelScope.launch { c.habits.setNote(date, note) }
}
