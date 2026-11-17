package com.example.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One night of sleep, keyed by the evening it started ("night of 17 Nov" = bed on the 17th, up on the 18th).
 * Same convention as the daily report: the report for date D uses the night of D.
 */
@Entity(tableName = "sleep_log")
data class SleepLogEntity(
    @PrimaryKey val night: LocalDate,
    @ColumnInfo(name = "bed_at") val bedAt: LocalDateTime,
    @ColumnInfo(name = "wake_at") val wakeAt: LocalDateTime,
    /** 1 (rough) … 5 (great); null = not rated. */
    val quality: Int? = null,
    val note: String = "",
) {
    val minutes: Long get() = Duration.between(bedAt, wakeAt).toMinutes().coerceAtLeast(0)
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_log ORDER BY night DESC")
    fun observeAll(): Flow<List<SleepLogEntity>>

    @Query("SELECT * FROM sleep_log WHERE night = :night")
    suspend fun get(night: LocalDate): SleepLogEntity?

    @Query("SELECT * FROM sleep_log WHERE night BETWEEN :from AND :to ORDER BY night")
    suspend fun range(from: LocalDate, to: LocalDate): List<SleepLogEntity>

    @Upsert suspend fun upsert(log: SleepLogEntity)

    @Query("DELETE FROM sleep_log WHERE night = :night") suspend fun delete(night: LocalDate)
    @Query("SELECT * FROM sleep_log ORDER BY night") suspend fun all(): List<SleepLogEntity>
    @Query("DELETE FROM sleep_log") suspend fun clear()
}
