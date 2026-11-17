package com.example.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ---- v9: Progress reports & share card cache ----

@Entity(
    tableName = "report_cache",
    primaryKeys = ["period_type", "period_start"],
)
data class ReportCacheEntity(
    @ColumnInfo(name = "period_type") val periodType: String,
    @ColumnInfo(name = "period_start") val periodStart: String,
    val json: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ReportCacheDao {
    @Query("SELECT * FROM report_cache WHERE period_type = :type AND period_start = :start LIMIT 1")
    fun observeReport(type: String, start: String): Flow<ReportCacheEntity?>

    @Query("SELECT * FROM report_cache WHERE period_type = :type AND period_start = :start LIMIT 1")
    suspend fun getReport(type: String, start: String): ReportCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReportCacheEntity)

    @Query("DELETE FROM report_cache WHERE period_type = :type AND period_start = :start")
    suspend fun delete(type: String, start: String)

    @Query("DELETE FROM report_cache")
    suspend fun clear()
}

