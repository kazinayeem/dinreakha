package com.example.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "day_log")
data class DayLog(
    @PrimaryKey val date: LocalDate,
    val mood: Int? = null,
    val note: String = "",
)

@Entity(
    tableName = "habit_check",
    indices = [Index(value = ["date", "habit_key"], unique = true)],
)
data class HabitCheck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    @ColumnInfo(name = "habit_key") val habitKey: String,
    val done: Boolean,
    @ColumnInfo(name = "done_at") val doneAt: LocalTime? = null,
)

@Entity(tableName = "weekly_review")
data class WeeklyReview(
    @PrimaryKey @ColumnInfo(name = "week_no") val weekNo: Int,
    @ColumnInfo(name = "went_well") val wentWell: String = "",
    @ColumnInfo(name = "got_in_way") val gotInWay: String = "",
    @ColumnInfo(name = "next_change") val nextChange: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)
