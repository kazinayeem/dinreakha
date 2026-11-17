package com.example.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DayLogDao {
    @Query("SELECT * FROM day_log WHERE date = :date")
    fun observe(date: LocalDate): Flow<DayLog?>

    @Query("SELECT * FROM day_log WHERE date = :date")
    suspend fun get(date: LocalDate): DayLog?

    @Query("SELECT * FROM day_log WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayLog>>

    @Upsert suspend fun upsert(log: DayLog)

    @Query("SELECT * FROM day_log ORDER BY date DESC")
    fun observeAll(): Flow<List<DayLog>>

    @Query("SELECT * FROM day_log ORDER BY date") suspend fun all(): List<DayLog>
    @Query("DELETE FROM day_log") suspend fun clear()
}

@Dao
interface HabitCheckDao {
    @Query("SELECT * FROM habit_check WHERE date = :date")
    fun observeForDate(date: LocalDate): Flow<List<HabitCheck>>

    @Query("SELECT * FROM habit_check WHERE date BETWEEN :from AND :to")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<HabitCheck>>

    @Query("SELECT * FROM habit_check WHERE date = :date")
    suspend fun getForDate(date: LocalDate): List<HabitCheck>

    /** REPLACE resolves the unique (date, habit_key) conflict — works on every SQLite version (minSdk 26). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(check: HabitCheck)

    @Query("SELECT * FROM habit_check ORDER BY date") suspend fun all(): List<HabitCheck>
    @Query("DELETE FROM habit_check") suspend fun clear()
}

@Dao
interface WeeklyReviewDao {
    @Query("SELECT * FROM weekly_review WHERE week_no = :week")
    fun observe(week: Int): Flow<WeeklyReview?>

    @Upsert suspend fun upsert(review: WeeklyReview)

    @Query("SELECT * FROM weekly_review ORDER BY week_no") suspend fun all(): List<WeeklyReview>
    @Query("DELETE FROM weekly_review") suspend fun clear()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings") fun observeAll(): Flow<List<Setting>>
    @Query("SELECT * FROM settings") suspend fun all(): List<Setting>
    @Query("SELECT value FROM settings WHERE `key` = :key") suspend fun get(key: String): String?
    @Upsert suspend fun put(setting: Setting)
    @Query("DELETE FROM settings WHERE `key` = :key") suspend fun remove(key: String)
    @Query("DELETE FROM settings") suspend fun clear()
}
