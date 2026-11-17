package com.example.tracker.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

// ---- v5: focus timer ----

@Entity(tableName = "focus_session", indices = [Index("start")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** STUDY | RESEARCH | CODE | OTHER */
    val type: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val minutes: Int,
    val note: String = "",
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_session ORDER BY start DESC") fun observeAll(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_session ORDER BY start") suspend fun all(): List<FocusSessionEntity>
    @Insert suspend fun insert(e: FocusSessionEntity): Long
    @Query("UPDATE focus_session SET note = :note WHERE id = :id") suspend fun setNote(id: Long, note: String)
    @Query("DELETE FROM focus_session WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM focus_session") suspend fun clear()
}
