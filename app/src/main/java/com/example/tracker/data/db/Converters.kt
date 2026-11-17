package com.example.tracker.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** java.time ↔ ISO-8601 text. ISO strings sort correctly, so range queries work on them. */
class Converters {
    @TypeConverter fun dateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToDate(s: String?): LocalDate? = s?.let(LocalDate::parse)

    @TypeConverter fun timeToString(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun stringToTime(s: String?): LocalTime? = s?.let(LocalTime::parse)

    @TypeConverter fun dateTimeToString(t: LocalDateTime?): String? = t?.toString()
    @TypeConverter fun stringToDateTime(s: String?): LocalDateTime? = s?.let(LocalDateTime::parse)

    /** Mon=1 … Sun=7 (ISO). */
    @TypeConverter fun dayToInt(d: java.time.DayOfWeek?): Int? = d?.value
    @TypeConverter fun intToDay(i: Int?): java.time.DayOfWeek? = i?.let(java.time.DayOfWeek::of)
}
