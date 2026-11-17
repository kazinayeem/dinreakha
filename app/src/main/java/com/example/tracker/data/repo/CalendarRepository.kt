package com.example.tracker.data.repo

import androidx.room.withTransaction
import com.example.tracker.data.calendar.CalEvent
import com.example.tracker.data.calendar.EventException
import com.example.tracker.data.calendar.Ics
import com.example.tracker.data.calendar.Recurrence
import com.example.tracker.data.calendar.Recurrences
import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.DayNoteEntity
import com.example.tracker.data.db.EventEntity
import com.example.tracker.data.db.EventExceptionEntity
import com.example.tracker.data.db.EventReminderEntity
import com.example.tracker.data.schedule.Reminder
import com.example.tracker.data.schedule.ReminderCategory
import com.example.tracker.data.schedule.TimeFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalDateTime

data class CalendarData(
    val events: List<CalEvent>,
    val exceptions: List<EventException>,
    val notes: Map<LocalDate, String>,
)

class CalendarRepository(private val db: AppDatabase) {
    private val dao = db.calendarDao()

    val data: Flow<CalendarData> = combine(dao.observeEvents(), dao.observeReminders(), dao.observeExceptions(), dao.observeNotes()) { ev, rem, ex, notes ->
        build(ev, rem, ex, notes)
    }

    suspend fun current(): CalendarData = build(dao.events(), dao.reminders(), dao.exceptions(), dao.notes())

    private fun build(ev: List<EventEntity>, rem: List<EventReminderEntity>, ex: List<EventExceptionEntity>, notes: List<DayNoteEntity>): CalendarData {
        val remBy = rem.groupBy { it.eventId }
        return CalendarData(
            events = ev.map { e -> toDomain(e, remBy[e.id].orEmpty().map { it.minutesBefore }.sorted()) },
            exceptions = ex.map { EventException(it.eventId, it.originalDate, it.cancelled, it.title, it.start, it.end, it.location, it.notes) },
            notes = notes.associate { it.date to it.text },
        )
    }

    private fun toDomain(e: EventEntity, reminders: List<Int>) = CalEvent(
        e.id, e.title, e.start, e.end, e.allDay, e.location, e.notes, e.color, Recurrence.parse(e.rrule), reminders)

    private fun toEntity(e: CalEvent) = EventEntity(e.id, e.title.trim(), e.start, e.end, e.allDay, e.location.trim(), e.notes.trim(),
        e.color, e.recurrence.toRrule(), e.recurrence.until)

    // ---- events ----
    suspend fun saveEvent(e: CalEvent): Long = dao.saveEvent(toEntity(e), e.reminders)
    suspend fun deleteEvent(id: Long) = dao.deleteEvent(id)

    /** "Skip this one": hides a single occurrence of a repeating event. */
    suspend fun skipOccurrence(eventId: Long, originalDate: LocalDate) =
        dao.putException(EventExceptionEntity(eventId = eventId, originalDate = originalDate, cancelled = true,
            title = null, start = null, end = null, location = null, notes = null))

    /** "Edit this event only": stores the changed fields for one occurrence. */
    suspend fun saveOccurrence(eventId: Long, originalDate: LocalDate, title: String, start: LocalDateTime, end: LocalDateTime,
                               location: String, notes: String) =
        dao.putException(EventExceptionEntity(eventId = eventId, originalDate = originalDate, cancelled = false,
            title = title.trim(), start = start, end = end, location = location.trim(), notes = notes.trim()))

    /** Undo a skip / single-occurrence edit. */
    suspend fun restoreOccurrence(eventId: Long, originalDate: LocalDate) = dao.deleteException(eventId, originalDate)

    // ---- day notes ----
    suspend fun saveNote(date: LocalDate, text: String) =
        if (text.isBlank()) dao.deleteNote(date) else dao.upsertNote(DayNoteEntity(date, text.trim()))

    // ---- .ics ----
    suspend fun exportIcs(): String { val d = current(); return Ics.write(d.events, d.exceptions) }

    /** Imports VEVENTs as new events (never overwrites). Returns how many events were added. */
    suspend fun importIcs(text: String): Int {
        val parsed = Ics.parse(text)
        db.withTransaction {
            parsed.forEach { p ->
                val id = dao.saveEvent(toEntity(p.event.copy(id = 0)), p.event.reminders)
                p.exceptions.forEach { ex ->
                    dao.putException(EventExceptionEntity(eventId = id, originalDate = ex.originalDate, cancelled = ex.cancelled,
                        title = ex.title, start = ex.start, end = ex.end, location = ex.location, notes = ex.notes))
                }
            }
        }
        return parsed.size
    }

    /** Next reminder of every (event, minutes-before) pair — one alarm slot each. */
    suspend fun upcomingReminders(now: LocalDateTime): List<Reminder> {
        val d = current()
        return Recurrences.upcomingReminders(d.events, d.exceptions, now)
            .groupBy { (o, m, _) -> slot(o.event.id, m) }
            .map { (slot, list) ->
                val (o, m, at) = list.minBy { it.third }
                val whenText = when {
                    o.allDay -> "All day"
                    m == 0 -> "Now, until ${TimeFormat.format(o.end)}"
                    m < 60 -> "In $m min, at ${TimeFormat.format(o.start)}"
                    m % 1440 == 0 -> "${if (m == 1440) "Tomorrow" else "In ${m / 1440} days"} at ${TimeFormat.format(o.start)}"
                    else -> "At ${TimeFormat.format(o.start)}"
                }
                Reminder(slot, ReminderCategory.CALENDAR, at, o.title,
                    whenText + if (o.location.isNotBlank()) ", ${o.location}" else "", at.toLocalDate())
            }
    }

    companion object {
        fun slot(eventId: Long, minutes: Int) = "event_${eventId}_$minutes"
    }
}
