package com.example.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

// ---- v4: calendar ----

@Entity(tableName = "event")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    @ColumnInfo(name = "all_day") val allDay: Boolean,
    val location: String,
    val notes: String,
    /** ARGB */
    val color: Long,
    /** iCalendar RRULE, null = does not repeat. */
    val rrule: String?,
    /** Last date a repeating event can occur (copied from the RRULE for cheap filtering). */
    val until: LocalDate?,
)

@Entity(
    tableName = "event_exception",
    indices = [Index(value = ["event_id", "original_date"], unique = true)],
)
data class EventExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "original_date") val originalDate: LocalDate,
    val cancelled: Boolean,
    val title: String?,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val location: String?,
    val notes: String?,
)

@Entity(tableName = "event_reminder", indices = [Index("event_id")])
data class EventReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "minutes_before") val minutesBefore: Int,
)

@Entity(tableName = "day_note")
data class DayNoteEntity(
    @PrimaryKey val date: LocalDate,
    val text: String,
)

@Dao
abstract class CalendarDao {
    @Query("SELECT * FROM event ORDER BY start") abstract fun observeEvents(): Flow<List<EventEntity>>
    @Query("SELECT * FROM event ORDER BY start") abstract suspend fun events(): List<EventEntity>
    @Query("SELECT * FROM event WHERE id = :id") abstract suspend fun event(id: Long): EventEntity?
    @Query("SELECT * FROM event_exception") abstract fun observeExceptions(): Flow<List<EventExceptionEntity>>
    @Query("SELECT * FROM event_exception") abstract suspend fun exceptions(): List<EventExceptionEntity>
    @Query("SELECT * FROM event_reminder") abstract fun observeReminders(): Flow<List<EventReminderEntity>>
    @Query("SELECT * FROM event_reminder") abstract suspend fun reminders(): List<EventReminderEntity>
    @Query("SELECT * FROM day_note ORDER BY date") abstract fun observeNotes(): Flow<List<DayNoteEntity>>
    @Query("SELECT * FROM day_note ORDER BY date") abstract suspend fun notes(): List<DayNoteEntity>

    @Insert abstract suspend fun insertEvent(e: EventEntity): Long
    @Upsert abstract suspend fun upsertEvent(e: EventEntity)
    @Insert abstract suspend fun insertReminders(r: List<EventReminderEntity>)
    @Query("DELETE FROM event_reminder WHERE event_id = :eventId") abstract suspend fun deleteReminders(eventId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putException(e: EventExceptionEntity)
    @Query("DELETE FROM event_exception WHERE event_id = :eventId AND original_date = :date") abstract suspend fun deleteException(eventId: Long, date: LocalDate)
    @Query("DELETE FROM event_exception WHERE event_id = :eventId") abstract suspend fun deleteExceptions(eventId: Long)
    @Query("DELETE FROM event WHERE id = :id") abstract suspend fun deleteEventRow(id: Long)
    @Upsert abstract suspend fun upsertNote(n: DayNoteEntity)
    @Query("DELETE FROM day_note WHERE date = :date") abstract suspend fun deleteNote(date: LocalDate)

    @Query("DELETE FROM event") abstract suspend fun clearEvents()
    @Query("DELETE FROM event_exception") abstract suspend fun clearExceptions()
    @Query("DELETE FROM event_reminder") abstract suspend fun clearReminders()
    @Query("DELETE FROM day_note") abstract suspend fun clearNotes()

    /** Insert or update an event and replace its reminders, atomically. Returns the event id. */
    @Transaction
    open suspend fun saveEvent(e: EventEntity, reminderMinutes: List<Int>): Long {
        val id = if (e.id == 0L) insertEvent(e) else { upsertEvent(e); e.id }
        deleteReminders(id)
        if (reminderMinutes.isNotEmpty()) insertReminders(reminderMinutes.distinct().map { EventReminderEntity(eventId = id, minutesBefore = it) })
        return id
    }

    /** Delete an event with its reminders and exceptions. */
    @Transaction
    open suspend fun deleteEvent(id: Long) { deleteReminders(id); deleteExceptions(id); deleteEventRow(id) }
}
