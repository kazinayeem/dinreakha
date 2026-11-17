package com.example.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

enum class BlockRuleType {
    DAILY_LIMIT,
    SCHEDULE,
    FOCUS,
    CLASS,
    APP_GROUP,
}

@Entity(
    tableName = "block_rule",
)
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: BlockRuleType,
    val name: String,
    @ColumnInfo(name = "target_packages") val targetPackages: String, // Comma-separated package names
    @ColumnInfo(name = "target_domains") val targetDomains: String = "", // Comma-separated website domains (e.g. facebook.com,youtube.com)
    @ColumnInfo(name = "limit_minutes") val limitMinutes: Int = 0, // 0 = always blocked when active
    @ColumnInfo(name = "session_limit_minutes") val sessionLimitMinutes: Int = 0, // 0 = disabled
    @ColumnInfo(name = "cooldown_minutes") val cooldownMinutes: Int = 0, // 0 = disabled
    @ColumnInfo(name = "start_time") val startTime: LocalTime? = null,
    @ColumnInfo(name = "end_time") val endTime: LocalTime? = null,
    val days: String = "", // Comma-separated day numbers: "1,2,3,4,5" (empty means every day)
    val strict: Boolean = false,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "app_usage",
    primaryKeys = ["date", "package_name"],
    indices = [
        Index("date"),
    ],
)
data class AppUsageEntity(
    val date: LocalDate,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long = 0L,
    val opens: Int = 0,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "block_event",
    indices = [
        Index("timestamp"),
        Index("package_name"),
    ],
)
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String,
    @ColumnInfo(name = "emergency_used") val emergencyUsed: Boolean = false,
)

@Entity(
    tableName = "emergency_unlock",
)
data class EmergencyUnlockEntity(
    @PrimaryKey val date: LocalDate,
    @ColumnInfo(name = "unlock_count") val unlockCount: Int = 0,
    @ColumnInfo(name = "last_unlock_time") val lastUnlockTime: Long = 0L,
)

@Entity(
    tableName = "reels_usage",
    primaryKeys = ["date", "platform"],
    indices = [
        Index("date"),
    ],
)
data class ReelsUsageEntity(
    val date: LocalDate,
    val platform: String, // "instagram", "youtube", "facebook"
    val count: Int = 0,
    @ColumnInfo(name = "last_increment_at") val lastIncrementAt: Long = System.currentTimeMillis(),
)

@Dao
interface BlockerDao {

    // ---- Rules ----
    @Query("SELECT * FROM block_rule ORDER BY enabled DESC, id DESC")
    fun observeRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rule WHERE enabled = 1")
    fun observeEnabledRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rule ORDER BY id DESC")
    suspend fun getAllRules(): List<BlockRuleEntity>

    @Query("SELECT * FROM block_rule WHERE enabled = 1")
    suspend fun getEnabledRules(): List<BlockRuleEntity>

    @Query("SELECT * FROM block_rule WHERE id = :id")
    suspend fun getRule(id: Long): BlockRuleEntity?

    @Upsert
    suspend fun upsertRule(rule: BlockRuleEntity): Long

    @Query("DELETE FROM block_rule WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("UPDATE block_rule SET enabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, enabled: Boolean)

    // ---- App Usage ----
    @Query("SELECT * FROM app_usage WHERE date = :date ORDER BY foreground_ms DESC")
    fun observeAppUsageForDate(date: LocalDate): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage WHERE date = :date ORDER BY foreground_ms DESC")
    suspend fun getAppUsageForDate(date: LocalDate): List<AppUsageEntity>

    @Query("SELECT * FROM app_usage WHERE date = :date AND package_name = :packageName LIMIT 1")
    suspend fun getAppUsage(date: LocalDate, packageName: String): AppUsageEntity?

    @Query("SELECT * FROM app_usage WHERE package_name = :packageName AND date >= :startDate ORDER BY date ASC")
    fun observeRecentUsageForPackage(packageName: String, startDate: LocalDate): Flow<List<AppUsageEntity>>

    @Upsert
    suspend fun upsertAppUsage(usage: AppUsageEntity)

    @Query("UPDATE app_usage SET foreground_ms = foreground_ms + :deltaMs, last_opened_at = :now WHERE date = :date AND package_name = :packageName")
    suspend fun addForegroundUsage(date: LocalDate, packageName: String, deltaMs: Long, now: Long): Int

    @Query("UPDATE app_usage SET opens = opens + 1, last_opened_at = :now WHERE date = :date AND package_name = :packageName")
    suspend fun incrementOpens(date: LocalDate, packageName: String, now: Long): Int

    // ---- Block Events ----
    @Query("SELECT * FROM block_event ORDER BY timestamp DESC LIMIT :limit")
    fun observeBlockEvents(limit: Int = 100): Flow<List<BlockEventEntity>>

    @Query("SELECT COUNT(*) FROM block_event WHERE timestamp >= :startOfDayTimestamp")
    fun observeBlockCountToday(startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_event WHERE timestamp >= :startOfDayTimestamp")
    suspend fun getBlockCountToday(startOfDayTimestamp: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockEvent(event: BlockEventEntity): Long

    // ---- Emergency Unlock ----
    @Query("SELECT * FROM emergency_unlock WHERE date = :date LIMIT 1")
    suspend fun getEmergencyUnlock(date: LocalDate): EmergencyUnlockEntity?

    @Query("SELECT * FROM emergency_unlock WHERE date = :date LIMIT 1")
    fun observeEmergencyUnlock(date: LocalDate): Flow<EmergencyUnlockEntity?>

    @Upsert
    suspend fun upsertEmergencyUnlock(unlock: EmergencyUnlockEntity)

    // ---- Reels & Shorts Usage ----
    @Query("SELECT * FROM reels_usage WHERE date = :date ORDER BY count DESC")
    fun observeReelsUsageForDate(date: LocalDate): Flow<List<ReelsUsageEntity>>

    @Query("SELECT * FROM reels_usage WHERE date = :date ORDER BY count DESC")
    suspend fun getReelsUsageForDate(date: LocalDate): List<ReelsUsageEntity>

    @Query("SELECT SUM(count) FROM reels_usage WHERE date = :date")
    fun observeTotalReelsCountForDate(date: LocalDate): Flow<Int?>

    @Query("SELECT SUM(count) FROM reels_usage WHERE date = :date")
    suspend fun getTotalReelsCountForDate(date: LocalDate): Int?

    @Query("UPDATE reels_usage SET count = count + 1, last_increment_at = :now WHERE date = :date AND platform = :platform")
    suspend fun incrementReelCount(date: LocalDate, platform: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReelsUsage(usage: ReelsUsageEntity): Long

    @Query("DELETE FROM reels_usage WHERE date = :date")
    suspend fun clearReelsUsageForDate(date: LocalDate)

    @Query("DELETE FROM reels_usage")
    suspend fun clearAllReelsUsage()

    // ---- Wipes ----
    @Query("DELETE FROM block_rule")
    suspend fun clearAllRules()

    @Query("DELETE FROM app_usage")
    suspend fun clearAllUsage()

    @Query("DELETE FROM block_event")
    suspend fun clearAllEvents()

    @Query("DELETE FROM emergency_unlock")
    suspend fun clearAllUnlocks()
}
