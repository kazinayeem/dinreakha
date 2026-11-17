package com.example.tracker.data.repo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.tracker.R
import com.example.tracker.alarm.Notifications
import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.AppUsageEntity
import com.example.tracker.data.db.BlockEventEntity
import com.example.tracker.data.db.BlockRuleEntity
import com.example.tracker.data.db.BlockRuleType
import com.example.tracker.data.db.BlockerDao
import com.example.tracker.data.db.EmergencyUnlockEntity
import com.example.tracker.data.db.ReelsUsageEntity
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class BlockDecision(
    val isBlocked: Boolean,
    val reason: String = "",
    val ruleName: String = "",
    val ruleType: BlockRuleType? = null,
    val limitMinutes: Int = 0,
    val usedMinutes: Int = 0,
    val timeUntilUnlock: String? = null,
    val isStrict: Boolean = false,
    val matchedPackage: String = "",
)

data class LimitedAppProgress(
    val packageName: String,
    val appName: String,
    val ruleName: String,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val progress: Float, // 0.0f .. 1.0f
    val isBlocked: Boolean,
)

class BlockerRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val focusRepo: FocusRepository,
    private val scheduleRepo: ScheduleRepository,
) {
    private val dao: BlockerDao = db.blockerDao()
    private val nm = context.getSystemService(NotificationManager::class.java)

    companion object {
        const val CH_LIMIT_WARN = "c_limit_warnings"
        const val EMERGENCY_UNLOCK_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        const val MAX_EMERGENCY_UNLOCKS_PER_DAY = 2

        // System allow-list: essentials that must never be blocked under any circumstances
        val SYSTEM_ALLOW_LIST = setOf(
            "com.example.tracker", // Dinrekha itself
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.google.android.contacts",
            "com.android.contacts",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.settings",
            "com.google.android.apps.maps",
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            // Essential BD banking & MFS
            "com.bKash.customerapp",
            "com.konasl.nagad",
            "com.rocket.wallet",
            "bd.gov.upay",
        )
    }

    init {
        ensureNotificationChannels()
    }

    private fun ensureNotificationChannels() {
        val channel = NotificationChannel(
            CH_LIMIT_WARN,
            "App Limit Warnings",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Warnings when approaching daily app usage limits (50%, 80%, 1 min left)"
        }
        nm.createNotificationChannel(channel)
    }

    // ==================== Rules ====================

    fun observeRules(): Flow<List<BlockRuleEntity>> = dao.observeRules()
    suspend fun getAllRules(): List<BlockRuleEntity> = dao.getAllRules()
    suspend fun getRule(id: Long): BlockRuleEntity? = dao.getRule(id)
    suspend fun saveRule(rule: BlockRuleEntity): Long = dao.upsertRule(rule)
    suspend fun deleteRule(id: Long) = dao.deleteRule(id)
    suspend fun setRuleEnabled(id: Long, enabled: Boolean) = dao.setRuleEnabled(id, enabled)

    // ==================== App Usage & Stats ====================

    fun observeAppUsage(date: LocalDate = LocalDate.now(Schedule.ZONE)): Flow<List<AppUsageEntity>> =
        dao.observeAppUsageForDate(date)

    suspend fun getTodayUsage(packageName: String, date: LocalDate = LocalDate.now(Schedule.ZONE)): AppUsageEntity? =
        dao.getAppUsage(date, packageName)

    suspend fun recordForegroundTime(
        packageName: String,
        deltaMs: Long,
        date: LocalDate = LocalDate.now(Schedule.ZONE),
    ) = withContext(Dispatchers.IO) {
        if (deltaMs <= 0L || isAllowListed(packageName)) return@withContext
        val now = System.currentTimeMillis()
        val updated = dao.addForegroundUsage(date, packageName, deltaMs, now)
        if (updated == 0) {
            val entity = AppUsageEntity(
                date = date,
                packageName = packageName,
                foregroundMs = deltaMs,
                opens = 1,
                lastOpenedAt = now,
            )
            dao.upsertAppUsage(entity)
        }
        checkLimitThresholdWarnings(packageName, date)
    }

    suspend fun recordAppOpen(
        packageName: String,
        date: LocalDate = LocalDate.now(Schedule.ZONE),
    ) = withContext(Dispatchers.IO) {
        if (isAllowListed(packageName)) return@withContext
        val now = System.currentTimeMillis()
        val updated = dao.incrementOpens(date, packageName, now)
        if (updated == 0) {
            val entity = AppUsageEntity(
                date = date,
                packageName = packageName,
                foregroundMs = 0L,
                opens = 1,
                lastOpenedAt = now,
            )
            dao.upsertAppUsage(entity)
        }
    }

    suspend fun logBlockEvent(packageName: String, reason: String, emergencyUsed: Boolean = false) =
        withContext(Dispatchers.IO) {
            dao.insertBlockEvent(
                BlockEventEntity(
                    packageName = packageName,
                    timestamp = System.currentTimeMillis(),
                    reason = reason,
                    emergencyUsed = emergencyUsed,
                )
            )
        }

    fun observeBlockCountToday(date: LocalDate = LocalDate.now(Schedule.ZONE)): Flow<Int> {
        val startOfDay = date.atStartOfDay(Schedule.ZONE).toInstant().toEpochMilli()
        return dao.observeBlockCountToday(startOfDay)
    }

    // ==================== Reels & Shorts Tracking ====================

    fun observeReelsUsage(date: LocalDate = LocalDate.now(Schedule.ZONE)): Flow<List<ReelsUsageEntity>> =
        dao.observeReelsUsageForDate(date)

    fun observeTotalReelsCount(date: LocalDate = LocalDate.now(Schedule.ZONE)): Flow<Int> =
        dao.observeTotalReelsCountForDate(date).map { it ?: 0 }

    suspend fun getReelsUsage(date: LocalDate = LocalDate.now(Schedule.ZONE)): List<ReelsUsageEntity> =
        dao.getReelsUsageForDate(date)

    suspend fun getTotalReelsCount(date: LocalDate = LocalDate.now(Schedule.ZONE)): Int =
        dao.getTotalReelsCountForDate(date) ?: 0

    suspend fun recordReelWatched(
        platform: String,
        date: LocalDate = LocalDate.now(Schedule.ZONE),
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = dao.incrementReelCount(date, platform, now)
        if (updated == 0) {
            dao.insertReelsUsage(
                ReelsUsageEntity(
                    date = date,
                    platform = platform,
                    count = 1,
                    lastIncrementAt = now,
                )
            )
        }
        checkReelsAlertThreshold(platform, date)
    }

    suspend fun resetReelsUsage(date: LocalDate = LocalDate.now(Schedule.ZONE)) = withContext(Dispatchers.IO) {
        dao.clearReelsUsageForDate(date)
    }

    private suspend fun checkReelsAlertThreshold(platform: String, date: LocalDate) {
        val total = dao.getTotalReelsCountForDate(date) ?: 0
        val settings = db.settingsDao().all().associate { it.key to it.value }
        val limit = settings[SettingsRepository.REELS_DAILY_LIMIT]?.toIntOrNull() ?: 0

        // 1. Daily limit reached alert
        if (limit > 0 && total >= limit) {
            postLimitNotification(
                id = 9991,
                title = "🎬 Reels Limit Reached ($total/$limit)",
                text = "You've reached your daily limit for Reels & Shorts on Instagram, YouTube & Facebook.",
            )
        }
        // 2. Mindful interval alert (every 25 reels)
        else if (total > 0 && total % 25 == 0) {
            postLimitNotification(
                id = 9992,
                title = "Mindful Break • $total Reels Watched",
                text = "You've scrolled $total Reels & Shorts today. Consider taking a 5-minute break for your mind.",
            )
        }
    }

    // ==================== Decision & Evaluation Engine ====================

    fun isAllowListed(packageName: String): Boolean {
        if (packageName in SYSTEM_ALLOW_LIST) return true
        if (packageName.startsWith("com.android.phone") || packageName.startsWith("com.google.android.dialer")) return true
        return false
    }

    suspend fun isEmergencyUnlockActive(date: LocalDate = LocalDate.now(Schedule.ZONE)): Boolean {
        val unlock = dao.getEmergencyUnlock(date) ?: return false
        val elapsed = System.currentTimeMillis() - unlock.lastUnlockTime
        return elapsed in 0L..EMERGENCY_UNLOCK_DURATION_MS
    }

    suspend fun getRemainingEmergencyUnlockMinutes(date: LocalDate = LocalDate.now(Schedule.ZONE)): Int {
        val unlock = dao.getEmergencyUnlock(date) ?: return 0
        val remainingMs = EMERGENCY_UNLOCK_DURATION_MS - (System.currentTimeMillis() - unlock.lastUnlockTime)
        return (remainingMs / 60000L).toInt().coerceAtLeast(0)
    }

    suspend fun canEmergencyUnlock(date: LocalDate = LocalDate.now(Schedule.ZONE)): Boolean {
        val unlock = dao.getEmergencyUnlock(date)
        val count = unlock?.unlockCount ?: 0
        return count < MAX_EMERGENCY_UNLOCKS_PER_DAY
    }

    suspend fun performEmergencyUnlock(date: LocalDate = LocalDate.now(Schedule.ZONE)): Boolean = withContext(Dispatchers.IO) {
        val unlock = dao.getEmergencyUnlock(date)
        val count = unlock?.unlockCount ?: 0
        if (count >= MAX_EMERGENCY_UNLOCKS_PER_DAY) return@withContext false
        val newUnlock = EmergencyUnlockEntity(
            date = date,
            unlockCount = count + 1,
            lastUnlockTime = System.currentTimeMillis(),
        )
        dao.upsertEmergencyUnlock(newUnlock)
        true
    }

    suspend fun evaluate(
        packageName: String,
        activeDomain: String? = null,
        now: LocalDateTime = LocalDateTime.now(Schedule.ZONE),
    ): BlockDecision = withContext(Dispatchers.IO) {
        // 1. System allow-list check
        if (isAllowListed(packageName)) {
            return@withContext BlockDecision(isBlocked = false)
        }

        val date = now.toLocalDate()
        val time = now.toLocalTime()

        // 2. Active emergency unlock check
        if (isEmergencyUnlockActive(date)) {
            return@withContext BlockDecision(isBlocked = false)
        }

        val rules = dao.getEnabledRules()
        if (rules.isEmpty()) return@withContext BlockDecision(isBlocked = false)

        val usage = dao.getAppUsage(date, packageName)
        val usedMinutes = ((usage?.foregroundMs ?: 0L) / 60000L).toInt()

        for (rule in rules) {
            val packages = rule.targetPackages.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val domains = rule.targetDomains.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

            val packageMatches = packageName.lowercase() in packages
            val domainMatches = activeDomain != null && domains.any { d -> activeDomain.lowercase().contains(d) }

            if (!packageMatches && !domainMatches) continue

            // Evaluate Rule by Type
            when (rule.type) {
                BlockRuleType.FOCUS -> {
                    val focus = focusRepo.current()
                    if (focus != null && !focus.isPaused && !focus.isDone(now)) {
                        return@withContext BlockDecision(
                            isBlocked = true,
                            reason = "Focus session in progress (${focus.title})",
                            ruleName = rule.name,
                            ruleType = rule.type,
                            isStrict = rule.strict,
                            matchedPackage = packageName,
                        )
                    }
                }

                BlockRuleType.CLASS -> {
                    val activeClass = findActiveClass(now)
                    if (activeClass != null) {
                        return@withContext BlockDecision(
                            isBlocked = true,
                            reason = "Class in session (${activeClass.title} in Room ${activeClass.room})",
                            ruleName = rule.name,
                            ruleType = rule.type,
                            isStrict = rule.strict,
                            matchedPackage = packageName,
                        )
                    }
                }

                BlockRuleType.SCHEDULE -> {
                    if (isWithinSchedule(rule, now)) {
                        val unlockStr = rule.endTime?.let { "Unlocks at ${TimeFormat.format(it)}" }
                        return@withContext BlockDecision(
                            isBlocked = true,
                            reason = "${rule.name} is active",
                            ruleName = rule.name,
                            ruleType = rule.type,
                            timeUntilUnlock = unlockStr,
                            isStrict = rule.strict,
                            matchedPackage = packageName,
                        )
                    }
                }

                BlockRuleType.DAILY_LIMIT, BlockRuleType.APP_GROUP -> {
                    val totalUsed = if (rule.type == BlockRuleType.APP_GROUP) {
                        // Shared group limit across all packages in rule
                        val groupUsage = dao.getAppUsageForDate(date)
                        groupUsage.filter { it.packageName.lowercase() in packages }.sumOf { (it.foregroundMs / 60000L).toInt() }
                    } else usedMinutes

                    if (rule.limitMinutes == 0) {
                        // Always blocked
                        return@withContext BlockDecision(
                            isBlocked = true,
                            reason = "${rule.name} is permanently restricted",
                            ruleName = rule.name,
                            ruleType = rule.type,
                            limitMinutes = 0,
                            usedMinutes = totalUsed,
                            isStrict = rule.strict,
                            matchedPackage = packageName,
                        )
                    }

                    if (totalUsed >= rule.limitMinutes) {
                        return@withContext BlockDecision(
                            isBlocked = true,
                            reason = "Daily limit reached: $totalUsed / ${rule.limitMinutes} min",
                            ruleName = rule.name,
                            ruleType = rule.type,
                            limitMinutes = rule.limitMinutes,
                            usedMinutes = totalUsed,
                            timeUntilUnlock = "Resets at midnight",
                            isStrict = rule.strict,
                            matchedPackage = packageName,
                        )
                    }

                    // Check session limit & cooldown
                    if (rule.sessionLimitMinutes > 0 && rule.cooldownMinutes > 0 && usage != null) {
                        val sessionMs = rule.sessionLimitMinutes * 60000L
                        val cooldownMs = rule.cooldownMinutes * 60000L
                        val nowMs = System.currentTimeMillis()
                        val lastOpen = usage.lastOpenedAt

                        // If user opened and remained in app longer than session limit
                        if (nowMs - lastOpen in sessionMs..(sessionMs + cooldownMs)) {
                            val remainingCooldownMin = (((sessionMs + cooldownMs) - (nowMs - lastOpen)) / 60000L).toInt() + 1
                            return@withContext BlockDecision(
                                isBlocked = true,
                                reason = "Session limit reached ($remainingCooldownMin min cool-down)",
                                ruleName = rule.name,
                                ruleType = rule.type,
                                limitMinutes = rule.limitMinutes,
                                usedMinutes = totalUsed,
                                timeUntilUnlock = "Cool-down: $remainingCooldownMin min",
                                isStrict = rule.strict,
                                matchedPackage = packageName,
                            )
                        }
                    }
                }
            }
        }

        BlockDecision(isBlocked = false)
    }

    // ==================== Schedule Helpers ====================

    fun isWithinSchedule(rule: BlockRuleEntity, now: LocalDateTime): Boolean {
        val days = rule.days.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (days.isNotEmpty() && now.dayOfWeek.value !in days) return false

        val start = rule.startTime ?: return false
        val end = rule.endTime ?: return false
        val current = now.toLocalTime()

        return if (start.isBefore(end)) {
            // Normal same-day window (e.g. 09:00 to 17:00)
            !current.isBefore(start) && !current.isAfter(end)
        } else {
            // Spans midnight (e.g. 23:00 to 05:05)
            !current.isBefore(start) || !current.isAfter(end)
        }
    }

    private suspend fun findActiveClass(now: LocalDateTime): Schedule.ClassSession? {
        val dow = now.dayOfWeek
        val time = now.toLocalTime()
        val classes = scheduleRepo.load().classes
        return classes.firstOrNull { session ->
            session.day == dow && !time.isBefore(session.start) && !time.isAfter(session.end)
        }
    }

    // ==================== Strict Mode Anti-Cheat ====================

    suspend fun isStrictModeActive(rule: BlockRuleEntity, now: LocalDateTime = LocalDateTime.now(Schedule.ZONE)): Boolean {
        if (!rule.strict || !rule.enabled) return false
        val date = now.toLocalDate()
        val usage = dao.getAppUsage(date, rule.targetPackages.split(",").firstOrNull() ?: "")
        val usedMinutes = ((usage?.foregroundMs ?: 0L) / 60000L).toInt()

        return when (rule.type) {
            BlockRuleType.FOCUS -> focusRepo.current()?.let { !it.isPaused && !it.isDone(now) } == true
            BlockRuleType.CLASS -> findActiveClass(now) != null
            BlockRuleType.SCHEDULE -> isWithinSchedule(rule, now)
            BlockRuleType.DAILY_LIMIT, BlockRuleType.APP_GROUP -> rule.limitMinutes > 0 && usedMinutes >= (rule.limitMinutes * 0.8f)
        }
    }

    // ==================== Warning Notifications ====================

    private suspend fun checkLimitThresholdWarnings(packageName: String, date: LocalDate) {
        val rules = dao.getEnabledRules().filter {
            it.type in listOf(BlockRuleType.DAILY_LIMIT, BlockRuleType.APP_GROUP) &&
                packageName.lowercase() in it.targetPackages.lowercase().split(",") &&
                it.limitMinutes > 0
        }
        if (rules.isEmpty()) return

        val usage = dao.getAppUsage(date, packageName) ?: return
        val usedMinutes = (usage.foregroundMs / 60000L).toInt()

        for (rule in rules) {
            val limit = rule.limitMinutes
            val p50 = (limit * 0.50f).toInt()
            val p80 = (limit * 0.80f).toInt()
            val p1m = (limit - 1).coerceAtLeast(0)

            when (usedMinutes) {
                p1m -> postLimitNotification(
                    id = 9001 + rule.id.toInt(),
                    title = "⚠️ 1 minute remaining on ${rule.name}",
                    text = "You've used $usedMinutes of $limit min. ${rule.name} will be blocked once reached.",
                )
                p80 -> postLimitNotification(
                    id = 9002 + rule.id.toInt(),
                    title = "${rule.name}: 80% daily limit used",
                    text = "$usedMinutes of $limit minutes used today.",
                )
                p50 -> postLimitNotification(
                    id = 9003 + rule.id.toInt(),
                    title = "${rule.name}: 50% limit reached",
                    text = "$usedMinutes of $limit minutes used.",
                )
            }
        }
    }

    private fun postLimitNotification(id: Int, title: String, text: String) {
        val builder = NotificationCompat.Builder(context, CH_LIMIT_WARN)
            .setSmallIcon(R.drawable.ic_stat_dinrekha)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        nm.notify(id, builder.build())
    }

    // ==================== System UsageStatsManager Sync ====================

    suspend fun syncUsageFromSystem(date: LocalDate = LocalDate.now(Schedule.ZONE)): Unit = withContext(Dispatchers.IO) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@withContext
        val startOfDay = date.atStartOfDay(Schedule.ZONE).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
        if (stats.isNullOrEmpty()) return@withContext

        stats.forEach { stat ->
            val pkg = stat.packageName
            val timeInFg = stat.totalTimeInForeground
            if (timeInFg > 0L && !isAllowListed(pkg)) {
                val existing = dao.getAppUsage(date, pkg)
                if (existing == null || timeInFg > existing.foregroundMs) {
                    val updated = AppUsageEntity(
                        date = date,
                        packageName = pkg,
                        foregroundMs = timeInFg,
                        opens = existing?.opens ?: 1,
                        lastOpenedAt = stat.lastTimeUsed.coerceAtLeast(existing?.lastOpenedAt ?: now),
                    )
                    dao.upsertAppUsage(updated)
                }
            }
        }
    }
}
