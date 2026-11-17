package com.example.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// ---- v3: the editable plan (seeded from Schedule.DEFAULT_CONFIG) ----

@Entity(tableName = "class_session")
data class ClassSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val course: String,
    val room: String,
)

@Entity(tableName = "bus_time")
data class BusTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: DayOfWeek,
    /** TO_CAMPUS | HOME */
    val direction: String,
    val time: LocalTime,
)

@Entity(tableName = "focus_block")
data class FocusBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** STUDY | RESEARCH | CODE */
    val type: String,
    /** null = every day */
    @ColumnInfo(name = "day_of_week") val dayOfWeek: DayOfWeek?,
    val start: LocalTime,
    val end: LocalTime,
    val place: String,
)

@Entity(tableName = "plan_phase")
data class PlanPhaseEntity(
    @PrimaryKey val number: Int,
    @ColumnInfo(name = "first_day") val firstDay: Int,
    @ColumnInfo(name = "last_day") val lastDay: Int,
    val wake: LocalTime,
    val bed: LocalTime,
    /** comma-separated prayer keys, e.g. "maghrib,isha" */
    val prayers: String,
    @ColumnInfo(name = "expected_blocks") val expectedBlocks: Int,
)

@Entity(tableName = "wake_override")
data class WakeOverrideEntity(
    @PrimaryKey @ColumnInfo(name = "day_of_week") val dayOfWeek: DayOfWeek,
    val time: LocalTime,
)

@Entity(tableName = "holiday")
data class HolidayEntity(
    @PrimaryKey val date: LocalDate,
    val name: String,
)

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val icon: String,
    /** DAILY | WEEKLY | MONTHLY */
    val repeat: String,
    /** comma-separated ISO day numbers (Mon=1 … Sun=7) for WEEKLY */
    val days: String,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int,
    val time: LocalTime?,
    val remind: Boolean,
    val active: Boolean,
)

// ---- v3: notification control ----

@Entity(tableName = "reminder_pref")
data class ReminderPrefEntity(
    /** ReminderCategory.key */
    @PrimaryKey val category: String,
    val enabled: Boolean,
    @ColumnInfo(name = "offset_min") val offsetMin: Int,
    val time: LocalTime?,
    /** SOUND | VIBRATE | SILENT */
    val sound: String,
    val ringtone: String?,
)

@Entity(tableName = "notification_log", indices = [Index("fired_at")])
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slot: String,
    val category: String,
    val title: String,
    @ColumnInfo(name = "fired_at") val firedAt: LocalDateTime,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM class_session ORDER BY day_of_week, start") fun observeClasses(): Flow<List<ClassSessionEntity>>
    @Query("SELECT * FROM bus_time ORDER BY day_of_week, time") fun observeBuses(): Flow<List<BusTimeEntity>>
    @Query("SELECT * FROM focus_block ORDER BY type, day_of_week") fun observeBlocks(): Flow<List<FocusBlockEntity>>
    @Query("SELECT * FROM plan_phase ORDER BY number") fun observePhases(): Flow<List<PlanPhaseEntity>>
    @Query("SELECT * FROM wake_override ORDER BY day_of_week") fun observeWakeOverrides(): Flow<List<WakeOverrideEntity>>
    @Query("SELECT * FROM holiday ORDER BY date") fun observeHolidays(): Flow<List<HolidayEntity>>
    @Query("SELECT * FROM routine ORDER BY id") fun observeRoutines(): Flow<List<RoutineEntity>>
    @Query("SELECT * FROM routine ORDER BY id") suspend fun routines(): List<RoutineEntity>
    @Upsert suspend fun upsertRoutine(e: RoutineEntity)
    @Query("DELETE FROM routine WHERE id = :id") suspend fun deleteRoutine(id: Long)
    @Query("DELETE FROM routine") suspend fun clearRoutines()

    @Query("SELECT * FROM class_session") suspend fun classes(): List<ClassSessionEntity>
    @Query("SELECT * FROM bus_time") suspend fun buses(): List<BusTimeEntity>
    @Query("SELECT * FROM focus_block") suspend fun blocks(): List<FocusBlockEntity>
    @Query("SELECT * FROM plan_phase ORDER BY number") suspend fun phases(): List<PlanPhaseEntity>
    @Query("SELECT * FROM wake_override") suspend fun wakeOverrides(): List<WakeOverrideEntity>
    @Query("SELECT * FROM holiday ORDER BY date") suspend fun holidays(): List<HolidayEntity>

    @Upsert suspend fun upsertClass(e: ClassSessionEntity)
    @Upsert suspend fun upsertBus(e: BusTimeEntity)
    @Upsert suspend fun upsertBlock(e: FocusBlockEntity)
    @Upsert suspend fun upsertPhase(e: PlanPhaseEntity)
    @Upsert suspend fun upsertWakeOverride(e: WakeOverrideEntity)
    @Upsert suspend fun upsertHoliday(e: HolidayEntity)

    @Query("DELETE FROM class_session WHERE id = :id") suspend fun deleteClass(id: Long)
    @Query("DELETE FROM bus_time WHERE id = :id") suspend fun deleteBus(id: Long)
    @Query("DELETE FROM focus_block WHERE id = :id") suspend fun deleteBlock(id: Long)
    @Query("DELETE FROM plan_phase WHERE number = :n") suspend fun deletePhase(n: Int)
    @Query("DELETE FROM wake_override WHERE day_of_week = :d") suspend fun deleteWakeOverride(d: DayOfWeek)
    @Query("DELETE FROM holiday WHERE date = :d") suspend fun deleteHoliday(d: LocalDate)

    @Query("DELETE FROM class_session") suspend fun clearClasses()
    @Query("DELETE FROM bus_time") suspend fun clearBuses()
    @Query("DELETE FROM focus_block") suspend fun clearBlocks()
    @Query("DELETE FROM plan_phase") suspend fun clearPhases()
    @Query("DELETE FROM wake_override") suspend fun clearWakeOverrides()
    @Query("DELETE FROM holiday") suspend fun clearHolidays()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM reminder_pref") fun observePrefs(): Flow<List<ReminderPrefEntity>>
    @Query("SELECT * FROM reminder_pref") suspend fun prefs(): List<ReminderPrefEntity>
    @Upsert suspend fun upsertPref(e: ReminderPrefEntity)
    @Query("DELETE FROM reminder_pref") suspend fun clearPrefs()

    @Insert suspend fun log(e: NotificationLogEntity)
    @Query("SELECT * FROM notification_log ORDER BY fired_at DESC LIMIT :limit") fun observeLog(limit: Int = 100): Flow<List<NotificationLogEntity>>
    /** Keep the log small. */
    @Query("DELETE FROM notification_log WHERE id NOT IN (SELECT id FROM notification_log ORDER BY fired_at DESC LIMIT 300)")
    suspend fun trimLog()
    @Query("DELETE FROM notification_log") suspend fun clearLog()
}
