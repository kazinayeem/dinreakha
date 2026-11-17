package com.example.tracker.ui.calendar

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tracker.AppContainer
import com.example.tracker.data.calendar.Occurrence
import com.example.tracker.data.calendar.Recurrences
import com.example.tracker.data.repo.CalendarData
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.PlanCalendar.EventType
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.ScheduleConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class CalView(val label: String) { MONTH("Month"), WEEK("Week"), DAY("Day"), AGENDA("Agenda") }

/** Layers you can switch on/off. */
enum class Layer(val key: String, val label: String) {
    EVENTS("events", "My events"), SCHEDULE("schedule", "Classes, buses, prayers & blocks"),
    HOLIDAYS("holidays", "Holidays"), NOTES("notes", "Day notes"),
}

enum class ItemKind { EVENT, SCHEDULE, HOLIDAY }

/** Anything drawn on the calendar. */
data class CalItem(
    val key: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val color: Color,
    val kind: ItemKind,
    val subtitle: String = "",
    val occurrence: Occurrence? = null,
    val scheduleType: EventType? = null,
) {
    fun touches(d: LocalDate): Boolean {
        val last = if (end.toLocalTime() == LocalTime.MIDNIGHT && end.isAfter(start)) end.toLocalDate().minusDays(1) else end.toLocalDate()
        return !d.isBefore(start.toLocalDate()) && !d.isAfter(maxOf(start.toLocalDate(), last))
    }
}

data class CalState(
    val data: CalendarData,
    val config: ScheduleConfig,
    val layers: Set<Layer>,
    val prayerTimes: Map<Schedule.Prayer, LocalTime>,
)

val SCHEDULE_COLORS = mapOf(
    EventType.CLASS to Color(0xFFF2A93B),
    EventType.BUS_TO_CAMPUS to Color(0xFFC77DFF), EventType.BUS_HOME to Color(0xFFC77DFF),
    EventType.PRAYER to Color(0xFF2FB38A),
    EventType.FOCUS to Color(0xFF4FA8E8),
    EventType.ROUTINE to Color(0xFFF07AAE),
)
val HOLIDAY_COLOR = Color(0xFFE0605E)

class CalendarViewModel(private val c: AppContainer) : ViewModel() {
    val view = MutableStateFlow(CalView.MONTH)
    /** The date the current view is anchored on (selected day). */
    val anchor = MutableStateFlow(LocalDate.now(Schedule.ZONE))
    private val layers = MutableStateFlow(Layer.entries.toSet())

    init {
        viewModelScope.launch {
            val saved = c.settings.calendarLayers()
            layers.value = Layer.entries.filter { it.key in saved }.toSet()
        }
    }

    val state: StateFlow<CalState?> = combine(c.calendar.data, c.scheduleState, layers, c.settings.settings) { d, cfg, l, s ->
        CalState(d, cfg, l, s.prayerTimes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleLayer(l: Layer) {
        val next = if (l in layers.value) layers.value - l else layers.value + l
        layers.value = next
        viewModelScope.launch { c.settings.setCalendarLayers(next.map { it.key }.toSet()) }
    }

    /** Everything visible between [from] and [to] (inclusive), sorted: all-day first, then by time. */
    fun itemsBetween(s: CalState, from: LocalDate, to: LocalDate): List<CalItem> = buildList {
        if (Layer.EVENTS in s.layers) {
            Recurrences.expandAll(s.data.events, s.data.exceptions, from, to).forEach { o ->
                add(CalItem("e_${o.key}", o.title, o.start, o.end, o.allDay, Color(o.event.color), ItemKind.EVENT,
                    o.location, occurrence = o))
            }
        }
        var d = from
        while (!d.isAfter(to)) {
            val day = d
            if (Layer.HOLIDAYS in s.layers) s.config.holidays[day]?.let { name ->
                add(CalItem("h_$day", name, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), true, HOLIDAY_COLOR, ItemKind.HOLIDAY))
            }
            if (Layer.SCHEDULE in s.layers) {
                PlanCalendar.timeline(day, s.prayerTimes)
                    .filter { it.type !in setOf(EventType.WAKE, EventType.SCREENS_OFF, EventType.BEDTIME) && !it.optional }
                    .forEach { e ->
                        add(CalItem("s_${day}_${e.type}_${e.start.toLocalTime()}", e.title, e.start,
                            e.end ?: e.start.plusMinutes(if (e.type == EventType.PRAYER) 15 else 30), false,
                            SCHEDULE_COLORS[e.type] ?: Color.Gray, ItemKind.SCHEDULE, e.detail, scheduleType = e.type))
                    }
            }
            d = d.plusDays(1)
        }
    }.sortedWith(compareBy<CalItem>({ !it.allDay }, { it.start }))

    fun notesOn(s: CalState, d: LocalDate): String? = if (Layer.NOTES in s.layers) s.data.notes[d] else null

    // ---- actions ----
    fun skip(o: Occurrence) = viewModelScope.launch { c.calendar.skipOccurrence(o.event.id, o.originalDate); c.scheduler.rescheduleAll() }
    fun restore(o: Occurrence) = viewModelScope.launch { c.calendar.restoreOccurrence(o.event.id, o.originalDate); c.scheduler.rescheduleAll() }
    fun deleteEvent(id: Long) = viewModelScope.launch { c.calendar.deleteEvent(id); c.scheduler.rescheduleAll() }
    fun saveNote(d: LocalDate, text: String) = viewModelScope.launch { c.calendar.saveNote(d, text) }
}
