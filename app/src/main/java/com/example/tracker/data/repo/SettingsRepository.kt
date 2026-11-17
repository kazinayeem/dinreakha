package com.example.tracker.data.repo

import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.ReminderPrefEntity
import com.example.tracker.data.db.Setting
import com.example.tracker.data.schedule.QuietHours
import com.example.tracker.data.schedule.QuietMode
import com.example.tracker.data.schedule.ReminderCategory
import com.example.tracker.data.schedule.ReminderConfig
import com.example.tracker.data.schedule.ReminderPref
import com.example.tracker.data.schedule.Schedule.Prayer
import com.example.tracker.data.schedule.SoundMode
import com.example.tracker.data.schedule.TimeFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** What the app lock protects. */
enum class LockScope(val label: String) { WALLET("Wallet"), SETTINGS("Settings"), APP("Whole app") }

/** Category of inspiration quote shown on splash screen. */
enum class QuoteCategory(val label: String) {
    ALL("All / সব ধরনের"),
    ISLAMIC("Ayat & Hadith / হাদিস ও আয়াত"),
    PRODUCTIVITY("Discipline & Focus / প্রেরণা"),
}

/** Language of inspiration quote shown on splash screen. */
enum class QuoteLanguage(val label: String) {
    BOTH("Mixed / উভয় ভাষা"),
    BANGLA("বাংলা"),
    ENGLISH("English"),
}

data class AppSettings(
    val prayerTimes: Map<Prayer, LocalTime>,
    val prefs: Map<ReminderCategory, ReminderPref>,
    val masterEnabled: Boolean,
    val mutedDate: LocalDate?,
    val quiet: QuietHours,
    val holidayFrom: LocalDate?,
    val holidayTo: LocalDate?,
    val snoozeMin: Int,
    val snoozeMax: Int,
    val onboardingDone: Boolean,
    val statusNotification: Boolean,
    val lockScopes: Set<LockScope>,
    val themeMode: ThemeMode,
    val palette: String,
    val firstDayOfWeek: DayOfWeek,
    val use24h: Boolean,
    val quoteCategory: QuoteCategory = QuoteCategory.ALL,
    val quoteLanguage: QuoteLanguage = QuoteLanguage.BOTH,
    val motivationEvening: LocalTime = LocalTime.of(20, 30),
    val language: com.example.tracker.ui.i18n.AppLanguage = com.example.tracker.ui.i18n.AppLanguage.ENGLISH,
    val banglaDigits: Boolean = true,
    val weatherEnabled: Boolean = true,
    val weatherCity: String = "Dhaka",
    val weatherLat: Double = 23.8103,
    val weatherLon: Double = 90.4125,
    val reelsTrackingEnabled: Boolean = true,
    val reelsDailyLimit: Int = 0, // 0 = unlimited
) {
    fun pref(c: ReminderCategory) = prefs[c] ?: c.defaultPref
    val appLock get() = LockScope.WALLET in lockScopes

    fun toReminderConfig() = ReminderConfig(
        prayerTimes = prayerTimes, prefs = prefs, masterEnabled = masterEnabled, mutedDate = mutedDate,
        quiet = quiet, holidayFrom = holidayFrom, holidayTo = holidayTo,
        quoteCategory = quoteCategory, quoteLanguage = quoteLanguage, motivationEvening = motivationEvening,
    )
}

/** key/value `settings` table + `reminder_pref` table → typed settings. */
class SettingsRepository(db: AppDatabase) {
    private val dao = db.settingsDao()
    private val notif = db.notificationDao()

    val settings: Flow<AppSettings> =
        combine(dao.observeAll(), notif.observePrefs()) { rows, prefs -> parse(rows.associate { it.key to it.value }, prefs) }
            .onEach {
                TimeFormat.use24h = it.use24h
                com.example.tracker.ui.i18n.I18n.set(it.language, it.banglaDigits)
            }

    suspend fun current(): AppSettings =
        parse(dao.all().associate { it.key to it.value }, notif.prefs()).also { TimeFormat.use24h = it.use24h }

    // ---- reminders ----
    suspend fun setPrayerTime(p: Prayer, t: LocalTime) = put(prayerKey(p), t.toString())
    suspend fun setPref(c: ReminderCategory, p: ReminderPref) = notif.upsertPref(
        ReminderPrefEntity(c.key, p.enabled, p.offsetMin, p.time, p.sound.name, p.ringtone))
    suspend fun setEnabled(c: ReminderCategory, on: Boolean) = setPref(c, current().pref(c).copy(enabled = on))
    suspend fun setMaster(on: Boolean) = putBool(MASTER, on)
    suspend fun setMutedDate(d: LocalDate?) = if (d == null) dao.remove(MUTE_DATE) else put(MUTE_DATE, d.toString())
    suspend fun setQuiet(q: QuietHours) {
        put(QUIET_MODE, q.mode.name); put(QUIET_START, q.start.toString()); put(QUIET_END, q.end.toString())
    }
    suspend fun setHolidayMode(from: LocalDate?, to: LocalDate?) {
        if (from == null || to == null) { dao.remove(HOLIDAY_FROM); dao.remove(HOLIDAY_TO) }
        else { put(HOLIDAY_FROM, minOf(from, to).toString()); put(HOLIDAY_TO, maxOf(from, to).toString()) }
    }
    suspend fun setSnooze(minutes: Int, max: Int) { put(SNOOZE_MIN, minutes.toString()); put(SNOOZE_MAX, max.toString()) }

    /** Snoozes used for the wake alarm of [date]. */
    suspend fun snoozeCount(date: LocalDate): Int = dao.get("snooze_count_$date")?.toIntOrNull() ?: 0
    suspend fun setSnoozeCount(date: LocalDate, n: Int) = put("snooze_count_$date", n.toString())

    /** Slots of dynamic reminders scheduled by the last reschedule (for cancelling deleted ones). */
    suspend fun dynamicSlots(): Set<String> = dao.get("dyn_slots")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    suspend fun setDynamicSlots(slots: Set<String>) = put("dyn_slots", slots.joinToString(","))

    // ---- calendar layers ----
    suspend fun calendarLayers(): Set<String> = dao.get("cal_layers")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: DEFAULT_LAYERS
    suspend fun setCalendarLayers(layers: Set<String>) = put("cal_layers", layers.joinToString(","))

    // ---- general ----
    suspend fun setOnboardingDone() = putBool(ONBOARDING_DONE, true)
    suspend fun setStatusNotification(on: Boolean) = putBool(STATUS_NOTIFICATION, on)
    suspend fun setLockScope(scope: LockScope, on: Boolean) = putBool(lockKey(scope), on)
    suspend fun setThemeMode(m: ThemeMode) = put(THEME_MODE, m.name)
    suspend fun setPalette(key: String) = put(PALETTE, key)
    suspend fun setFirstDayOfWeek(d: DayOfWeek) = put(FIRST_DAY, d.name)
    suspend fun setUse24h(on: Boolean) = putBool(USE_24H, on)
    suspend fun setQuoteCategory(c: QuoteCategory) = put(QUOTE_CATEGORY, c.name)
    suspend fun setQuoteLanguage(l: QuoteLanguage) = put(QUOTE_LANGUAGE, l.name)
    suspend fun setMotivationEvening(t: LocalTime) = put(MOTIVATION_EVENING, t.toString())
    suspend fun setLanguage(l: com.example.tracker.ui.i18n.AppLanguage) = put(LANGUAGE, l.code)
    suspend fun setBanglaDigits(on: Boolean) = putBool(BANGLA_DIGITS, on)
    suspend fun setWeatherEnabled(on: Boolean) = putBool(WEATHER_ENABLED, on)
    suspend fun setWeatherLocation(city: String, lat: Double, lon: Double) {
        put(WEATHER_CITY, city)
        put(WEATHER_LAT, lat.toString())
        put(WEATHER_LON, lon.toString())
    }
    suspend fun getCachedWeatherJson(): String? = dao.get(WEATHER_CACHE)
    suspend fun setCachedWeatherJson(json: String) = put(WEATHER_CACHE, json)

    private suspend fun put(k: String, v: String) = dao.put(Setting(k, v))
    private suspend fun putBool(k: String, v: Boolean) = put(k, if (v) "1" else "0")

    private fun parse(m: Map<String, String>, prefRows: List<ReminderPrefEntity>): AppSettings {
        fun time(k: String) = m[k]?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        fun date(k: String) = m[k]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val prefs = prefRows.mapNotNull { r ->
            val cat = ReminderCategory.entries.firstOrNull { it.key == r.category } ?: return@mapNotNull null
            cat to ReminderPref(r.enabled, r.offsetMin, r.time ?: cat.defaultTime,
                runCatching { SoundMode.valueOf(r.sound) }.getOrDefault(SoundMode.SOUND), r.ringtone)
        }.toMap()
        return AppSettings(
            prayerTimes = Prayer.entries.associateWith { p -> time(prayerKey(p)) ?: p.defaultTime },
            prefs = prefs,
            masterEnabled = m[MASTER] != "0",
            mutedDate = date(MUTE_DATE),
            quiet = QuietHours(
                runCatching { QuietMode.valueOf(m[QUIET_MODE]!!) }.getOrDefault(QuietMode.PLAN),
                time(QUIET_START) ?: LocalTime.of(23, 0), time(QUIET_END) ?: LocalTime.of(6, 30)),
            holidayFrom = date(HOLIDAY_FROM),
            holidayTo = date(HOLIDAY_TO),
            snoozeMin = m[SNOOZE_MIN]?.toIntOrNull() ?: 10,
            snoozeMax = m[SNOOZE_MAX]?.toIntOrNull() ?: 3,
            onboardingDone = m[ONBOARDING_DONE] == "1",
            statusNotification = m[STATUS_NOTIFICATION] != "0",
            lockScopes = LockScope.entries.filter { s ->
                // Wallet lock was the v1 "app_lock" switch (default on); the others default off.
                m[lockKey(s)]?.let { it == "1" } ?: (s == LockScope.WALLET && m[LEGACY_APP_LOCK] != "0")
            }.toSet(),
            themeMode = runCatching { ThemeMode.valueOf(m[THEME_MODE]!!) }.getOrDefault(ThemeMode.SYSTEM),
            palette = m[PALETTE] ?: "dinrekha",
            firstDayOfWeek = runCatching { DayOfWeek.valueOf(m[FIRST_DAY]!!) }.getOrDefault(DayOfWeek.SATURDAY),
            use24h = m[USE_24H] == "1", // default: 12-hour AM/PM
            quoteCategory = runCatching { QuoteCategory.valueOf(m[QUOTE_CATEGORY]!!) }.getOrDefault(QuoteCategory.ALL),
            quoteLanguage = runCatching { QuoteLanguage.valueOf(m[QUOTE_LANGUAGE]!!) }.getOrDefault(QuoteLanguage.BOTH),
            language = if (m[LANGUAGE] == "bn") com.example.tracker.ui.i18n.AppLanguage.BANGLA else com.example.tracker.ui.i18n.AppLanguage.ENGLISH,
            banglaDigits = m[BANGLA_DIGITS] != "0",
            motivationEvening = m[MOTIVATION_EVENING]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(20, 30),
            weatherEnabled = m[WEATHER_ENABLED] != "0",
            weatherCity = m[WEATHER_CITY] ?: "Dhaka",
            weatherLat = m[WEATHER_LAT]?.toDoubleOrNull() ?: 23.8103,
            weatherLon = m[WEATHER_LON]?.toDoubleOrNull() ?: 90.4125,
            reelsTrackingEnabled = m[REELS_TRACKING_ENABLED] != "0",
            reelsDailyLimit = m[REELS_DAILY_LIMIT]?.toIntOrNull() ?: 0,
        )
    }

    suspend fun setReelsTrackingEnabled(v: Boolean) = putBool(REELS_TRACKING_ENABLED, v)
    suspend fun setReelsDailyLimit(limit: Int) = put(REELS_DAILY_LIMIT, limit.toString())

    companion object {
        fun prayerKey(p: Prayer) = "prayer_time_${p.key}"
        fun lockKey(s: LockScope) = "lock_${s.name.lowercase()}"
        const val MASTER = "notifications_master"
        const val MUTE_DATE = "mute_date"
        const val QUIET_MODE = "quiet_mode"
        const val QUIET_START = "quiet_start"
        const val QUIET_END = "quiet_end"
        const val HOLIDAY_FROM = "holiday_mode_from"
        const val HOLIDAY_TO = "holiday_mode_to"
        const val SNOOZE_MIN = "snooze_min"
        const val SNOOZE_MAX = "snooze_max"
        const val ONBOARDING_DONE = "onboarding_done"
        const val STATUS_NOTIFICATION = "status_notification"
        const val LEGACY_APP_LOCK = "app_lock"
        const val THEME_MODE = "theme_mode"
        const val PALETTE = "palette"
        const val FIRST_DAY = "first_day_of_week"
        const val USE_24H = "use_24h"
        const val QUOTE_CATEGORY = "quote_category"
        const val QUOTE_LANGUAGE = "quote_language"
        const val MOTIVATION_EVENING = "motivation_evening"
        const val LANGUAGE = "app_language"
        const val BANGLA_DIGITS = "bangla_digits"
        const val WEATHER_ENABLED = "weather_enabled"
        const val WEATHER_CITY = "weather_city"
        const val WEATHER_LAT = "weather_lat"
        const val WEATHER_LON = "weather_lon"
        const val WEATHER_CACHE = "weather_cache"
        const val REELS_TRACKING_ENABLED = "reels_tracking_enabled"
        const val REELS_DAILY_LIMIT = "reels_daily_limit"
        val DEFAULT_LAYERS = setOf("events", "schedule", "holidays", "notes")
    }
}
