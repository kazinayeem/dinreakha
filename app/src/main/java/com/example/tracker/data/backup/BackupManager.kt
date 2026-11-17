package com.example.tracker.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.tracker.data.db.Account
import com.example.tracker.data.db.AccountType
import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.Budget
import com.example.tracker.data.db.Category
import com.example.tracker.data.db.CategoryKind
import com.example.tracker.data.db.DayLog
import com.example.tracker.data.db.HabitCheck
import com.example.tracker.data.db.Recurring
import com.example.tracker.data.db.Setting
import com.example.tracker.data.db.Txn
import com.example.tracker.data.db.TxnType
import com.example.tracker.data.db.WeeklyReview
import com.example.tracker.data.db.BusTimeEntity
import com.example.tracker.data.db.ClassSessionEntity
import com.example.tracker.data.db.FocusBlockEntity
import com.example.tracker.data.db.HolidayEntity
import com.example.tracker.data.db.Migrations
import com.example.tracker.data.db.PlanPhaseEntity
import com.example.tracker.data.db.ReminderPrefEntity
import com.example.tracker.data.db.RoutineEntity
import com.example.tracker.data.db.WakeOverrideEntity
import com.example.tracker.data.finance.Money
import com.example.tracker.data.repo.ScheduleRepository
import com.example.tracker.data.schedule.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Full-database JSON backup + CSV of transactions. Files are chosen by the user via SAF. */
class BackupManager(
    private val db: AppDatabase,
    private val context: Context? = null,
    private val scheduleRepo: ScheduleRepository? = null,
) {

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("format", "dinrekha-backup")
        root.put("version", 5)
        root.put("exported_at", LocalDateTime.now().toString())
        root.put("day_log", arr(db.dayLogDao().all()) { JSONObject().put("date", it.date).put("mood", it.mood ?: JSONObject.NULL).put("note", it.note) })
        root.put("habit_check", arr(db.habitCheckDao().all()) {
            JSONObject().put("date", it.date).put("habit_key", it.habitKey).put("done", it.done).put("done_at", it.doneAt ?: JSONObject.NULL)
        })
        root.put("weekly_review", arr(db.weeklyReviewDao().all()) {
            JSONObject().put("week_no", it.weekNo).put("went_well", it.wentWell).put("got_in_way", it.gotInWay)
                .put("next_change", it.nextChange).put("created_at", it.createdAt)
        })
        root.put("settings", arr(db.settingsDao().all()) { JSONObject().put("key", it.key).put("value", it.value) })
        root.put("account", arr(db.accountDao().all()) {
            JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name)
                .put("opening_balance_paisa", it.openingBalancePaisa).put("opening_date", it.openingDate)
                .put("last4", it.last4 ?: JSONObject.NULL).put("color", it.color).put("archived", it.archived)
        })
        root.put("category", arr(db.categoryDao().all()) {
            JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("kind", it.kind.name)
        })
        root.put("txn", arr(db.txnDao().all()) {
            JSONObject().put("id", it.id).put("type", it.type.name).put("amount_paisa", it.amountPaisa)
                .put("account_id", it.accountId).put("to_account_id", it.toAccountId ?: JSONObject.NULL)
                .put("category_id", it.categoryId ?: JSONObject.NULL).put("date_time", it.dateTime)
                .put("note", it.note).put("linked_txn_id", it.linkedTxnId ?: JSONObject.NULL)
        })
        root.put("budget", arr(db.budgetDao().all()) {
            JSONObject().put("id", it.id).put("category_id", it.categoryId).put("month", it.month).put("limit_paisa", it.limitPaisa)
        })
        root.put("recurring", arr(db.recurringDao().all()) {
            JSONObject().put("id", it.id).put("title", it.title).put("amount_paisa", it.amountPaisa)
                .put("category_id", it.categoryId).put("account_id", it.accountId).put("day_of_month", it.dayOfMonth)
                .put("active", it.active).put("last_confirmed_month", it.lastConfirmedMonth ?: JSONObject.NULL)
        })
        // v3 — schedule, routines, notification settings
        val sd = db.scheduleDao()
        root.put("class_session", arr(sd.classes()) { JSONObject().put("day", it.dayOfWeek.value).put("start", it.start).put("end", it.end).put("course", it.course).put("room", it.room) })
        root.put("bus_time", arr(sd.buses()) { JSONObject().put("day", it.dayOfWeek.value).put("direction", it.direction).put("time", it.time) })
        root.put("focus_block", arr(sd.blocks()) { JSONObject().put("type", it.type).put("day", it.dayOfWeek?.value ?: JSONObject.NULL).put("start", it.start).put("end", it.end).put("place", it.place) })
        root.put("plan_phase", arr(sd.phases()) { JSONObject().put("number", it.number).put("first_day", it.firstDay).put("last_day", it.lastDay).put("wake", it.wake).put("bed", it.bed).put("prayers", it.prayers).put("expected_blocks", it.expectedBlocks) })
        root.put("wake_override", arr(sd.wakeOverrides()) { JSONObject().put("day", it.dayOfWeek.value).put("time", it.time) })
        root.put("holiday", arr(sd.holidays()) { JSONObject().put("date", it.date).put("name", it.name) })
        root.put("routine", arr(sd.routines()) { JSONObject().put("id", it.id).put("title", it.title).put("icon", it.icon).put("repeat", it.repeat).put("days", it.days).put("day_of_month", it.dayOfMonth).put("time", it.time ?: JSONObject.NULL).put("remind", it.remind).put("active", it.active) })
        root.put("reminder_pref", arr(db.notificationDao().prefs()) { JSONObject().put("category", it.category).put("enabled", it.enabled).put("offset_min", it.offsetMin).put("time", it.time ?: JSONObject.NULL).put("sound", it.sound).put("ringtone", it.ringtone ?: JSONObject.NULL) })
        // v4 — calendar
        val cd = db.calendarDao()
        root.put("event", arr(cd.events()) { JSONObject().put("id", it.id).put("title", it.title).put("start", it.start).put("end", it.end)
            .put("all_day", it.allDay).put("location", it.location).put("notes", it.notes).put("color", it.color)
            .put("rrule", it.rrule ?: JSONObject.NULL).put("until", it.until ?: JSONObject.NULL) })
        root.put("event_exception", arr(cd.exceptions()) { JSONObject().put("event_id", it.eventId).put("original_date", it.originalDate)
            .put("cancelled", it.cancelled).put("title", it.title ?: JSONObject.NULL).put("start", it.start ?: JSONObject.NULL)
            .put("end", it.end ?: JSONObject.NULL).put("location", it.location ?: JSONObject.NULL).put("notes", it.notes ?: JSONObject.NULL) })
        root.put("event_reminder", arr(cd.reminders()) { JSONObject().put("event_id", it.eventId).put("minutes_before", it.minutesBefore) })
        root.put("day_note", arr(cd.notes()) { JSONObject().put("date", it.date).put("text", it.text) })
        // v5 — focus sessions
        root.put("focus_session", arr(db.focusDao().all()) { JSONObject().put("type", it.type).put("start", it.start).put("end", it.end)
            .put("minutes", it.minutes).put("note", it.note) })
        // v6 — courses
        root.put("course", arr(db.classNoteDao().allCourses()) { JSONObject().put("id", it.id).put("code", it.code).put("name", it.name).put("teacher", it.teacher ?: JSONObject.NULL).put("room", it.room ?: JSONObject.NULL).put("color", it.color).put("semester", it.semester).put("archived", it.archived) })
        // v7 — blocker
        root.put("block_rule", arr(db.blockerDao().getAllRules()) { JSONObject().put("id", it.id).put("type", it.type.name).put("name", it.name).put("target_packages", it.targetPackages).put("target_domains", it.targetDomains).put("limit_minutes", it.limitMinutes).put("session_limit_minutes", it.sessionLimitMinutes).put("cooldown_minutes", it.cooldownMinutes).put("start_time", it.startTime?.toString() ?: JSONObject.NULL).put("end_time", it.endTime?.toString() ?: JSONObject.NULL).put("days", it.days).put("strict", it.strict).put("enabled", it.enabled).put("created_at", it.createdAt) })
        // v6 — class notes and photo references (photo files stay in the app folder on this phone)
        root.put("class_note", arr(db.classNoteDao().allNotesForBackup()) { JSONObject().put("id", it.id).put("course_id", it.courseId)
            .put("date", it.date).put("class_start_time", it.classStartTime?.toString() ?: JSONObject.NULL).put("title", it.title ?: JSONObject.NULL)
            .put("text", it.text).put("tags", it.tags).put("important", it.important).put("created_at", it.createdAt).put("updated_at", it.updatedAt) })
        root.put("note_image", arr(db.classNoteDao().allImagesForBackup()) { JSONObject().put("id", it.id).put("note_id", it.noteId)
            .put("file_path", it.filePath).put("thumb_path", it.thumbPath).put("width", it.width).put("height", it.height)
            .put("ocr_text", it.ocrText ?: JSONObject.NULL).put("sort_order", it.sortOrder).put("created_at", it.createdAt) })
        // v10 — sleep log
        root.put("sleep_log", arr(db.sleepDao().all()) { JSONObject().put("night", it.night).put("bed_at", it.bedAt).put("wake_at", it.wakeAt)
            .put("quality", it.quality ?: JSONObject.NULL).put("note", it.note) })
        return root.toString(2)
    }

    /** Replaces ALL data with the backup, in one transaction (all-or-nothing). */
    suspend fun importJson(text: String) {
        val root = JSONObject(text)
        require(root.optString("format") == "dinrekha-backup" || root.has("habit_check")) { "Not a Dinrekha backup file" }
        db.withTransaction {
            db.dayLogDao().clear(); db.habitCheckDao().clear(); db.weeklyReviewDao().clear(); db.settingsDao().clear()
            db.txnDao().clear(); db.budgetDao().clear(); db.recurringDao().clear()
            db.accountDao().clear(); db.categoryDao().clear()

            each(root, "day_log") { db.dayLogDao().upsert(DayLog(LocalDate.parse(it.getString("date")), it.optIntOrNull("mood"), it.optString("note"))) }
            each(root, "habit_check") {
                db.habitCheckDao().put(HabitCheck(date = LocalDate.parse(it.getString("date")), habitKey = it.getString("habit_key"),
                    done = it.getBoolean("done"), doneAt = it.optStringOrNull("done_at")?.let(LocalTime::parse)))
            }
            each(root, "weekly_review") {
                db.weeklyReviewDao().upsert(WeeklyReview(it.getInt("week_no"), it.optString("went_well"), it.optString("got_in_way"),
                    it.optString("next_change"), it.optLong("created_at")))
            }
            each(root, "settings") { db.settingsDao().put(Setting(it.getString("key"), it.getString("value"))) }
            each(root, "account") {
                db.accountDao().upsert(Account(it.getLong("id"), it.getString("name"), AccountType.valueOf(it.getString("type")),
                    it.getLong("opening_balance_paisa"), LocalDate.parse(it.getString("opening_date")),
                    it.optStringOrNull("last4"), it.getLong("color"), it.getBoolean("archived")))
            }
            each(root, "category") {
                db.categoryDao().upsert(Category(it.getLong("id"), it.getString("name"), it.getString("icon"), CategoryKind.valueOf(it.getString("kind"))))
            }
            each(root, "txn") {
                db.txnDao().insert(Txn(it.getLong("id"), TxnType.valueOf(it.getString("type")), it.getLong("amount_paisa"),
                    it.getLong("account_id"), it.optLongOrNull("to_account_id"), it.optLongOrNull("category_id"),
                    LocalDateTime.parse(it.getString("date_time")), it.optString("note"), it.optLongOrNull("linked_txn_id")))
            }
            each(root, "budget") {
                db.budgetDao().upsert(Budget(it.getLong("id"), it.getLong("category_id"), it.getString("month"), it.getLong("limit_paisa")))
            }
            // v3 tables: only replaced when the backup contains them (older backups keep the current schedule).
            val sd = db.scheduleDao()
            if (root.has("class_session")) {
                sd.clearClasses(); sd.clearBuses(); sd.clearBlocks(); sd.clearPhases(); sd.clearWakeOverrides(); sd.clearHolidays(); sd.clearRoutines()
                db.notificationDao().clearPrefs()
                each(root, "class_session") { sd.upsertClass(ClassSessionEntity(0, java.time.DayOfWeek.of(it.getInt("day")), LocalTime.parse(it.getString("start")), LocalTime.parse(it.getString("end")), it.getString("course"), it.optString("room"))) }
                each(root, "bus_time") { sd.upsertBus(BusTimeEntity(0, java.time.DayOfWeek.of(it.getInt("day")), it.getString("direction"), LocalTime.parse(it.getString("time")))) }
                each(root, "focus_block") { sd.upsertBlock(FocusBlockEntity(0, it.getString("type"), it.optIntOrNull("day")?.let(java.time.DayOfWeek::of), LocalTime.parse(it.getString("start")), LocalTime.parse(it.getString("end")), it.optString("place"))) }
                each(root, "plan_phase") { sd.upsertPhase(PlanPhaseEntity(it.getInt("number"), it.getInt("first_day"), it.getInt("last_day"), LocalTime.parse(it.getString("wake")), LocalTime.parse(it.getString("bed")), it.getString("prayers"), it.getInt("expected_blocks"))) }
                each(root, "wake_override") { sd.upsertWakeOverride(WakeOverrideEntity(java.time.DayOfWeek.of(it.getInt("day")), LocalTime.parse(it.getString("time")))) }
                each(root, "holiday") { sd.upsertHoliday(HolidayEntity(LocalDate.parse(it.getString("date")), it.getString("name"))) }
                each(root, "routine") { sd.upsertRoutine(RoutineEntity(it.getLong("id"), it.getString("title"), it.getString("icon"), it.getString("repeat"), it.optString("days"), it.getInt("day_of_month"), it.optStringOrNull("time")?.let(LocalTime::parse), it.getBoolean("remind"), it.getBoolean("active"))) }
                each(root, "reminder_pref") { db.notificationDao().upsertPref(ReminderPrefEntity(it.getString("category"), it.getBoolean("enabled"), it.getInt("offset_min"), it.optStringOrNull("time")?.let(LocalTime::parse), it.getString("sound"), it.optStringOrNull("ringtone"))) }
            }
            if (root.has("event")) {
                val cd = db.calendarDao()
                cd.clearReminders(); cd.clearExceptions(); cd.clearEvents(); cd.clearNotes()
                each(root, "event") { cd.upsertEvent(com.example.tracker.data.db.EventEntity(it.getLong("id"), it.getString("title"),
                    LocalDateTime.parse(it.getString("start")), LocalDateTime.parse(it.getString("end")), it.getBoolean("all_day"),
                    it.optString("location"), it.optString("notes"), it.getLong("color"), it.optStringOrNull("rrule"),
                    it.optStringOrNull("until")?.let(LocalDate::parse))) }
                each(root, "event_exception") { cd.putException(com.example.tracker.data.db.EventExceptionEntity(0, it.getLong("event_id"),
                    LocalDate.parse(it.getString("original_date")), it.getBoolean("cancelled"), it.optStringOrNull("title"),
                    it.optStringOrNull("start")?.let(LocalDateTime::parse), it.optStringOrNull("end")?.let(LocalDateTime::parse),
                    it.optStringOrNull("location"), it.optStringOrNull("notes"))) }
                val rems = mutableListOf<com.example.tracker.data.db.EventReminderEntity>()
                each(root, "event_reminder") { rems += com.example.tracker.data.db.EventReminderEntity(0, it.getLong("event_id"), it.getInt("minutes_before")) }
                if (rems.isNotEmpty()) cd.insertReminders(rems)
                each(root, "day_note") { cd.upsertNote(com.example.tracker.data.db.DayNoteEntity(LocalDate.parse(it.getString("date")), it.getString("text"))) }
            }
            if (root.has("focus_session")) {
                db.focusDao().clear()
                each(root, "focus_session") { db.focusDao().insert(com.example.tracker.data.db.FocusSessionEntity(0, it.getString("type"),
                    LocalDateTime.parse(it.getString("start")), LocalDateTime.parse(it.getString("end")), it.getInt("minutes"), it.optString("note"))) }
            }
            if (root.has("class_note")) {
                db.classNoteDao().clearImages()
                db.classNoteDao().clearNotes()
                each(root, "class_note") { db.classNoteDao().restoreNote(com.example.tracker.data.db.ClassNoteEntity(it.getLong("id"), it.getLong("course_id"),
                    LocalDate.parse(it.getString("date")), it.optStringOrNull("class_start_time")?.let(LocalTime::parse), it.optStringOrNull("title"),
                    it.optString("text"), it.optString("tags"), it.optBoolean("important"), it.optLong("created_at"), it.optLong("updated_at"))) }
                each(root, "note_image") { db.classNoteDao().restoreImage(com.example.tracker.data.db.NoteImageEntity(it.getLong("id"), it.getLong("note_id"),
                    it.getString("file_path"), it.getString("thumb_path"), it.optInt("width"), it.optInt("height"), it.optStringOrNull("ocr_text"),
                    it.optInt("sort_order"), it.optLong("created_at"))) }
            }
            if (root.has("sleep_log")) {
                db.sleepDao().clear()
                each(root, "sleep_log") { db.sleepDao().upsert(com.example.tracker.data.db.SleepLogEntity(LocalDate.parse(it.getString("night")),
                    LocalDateTime.parse(it.getString("bed_at")), LocalDateTime.parse(it.getString("wake_at")), it.optIntOrNull("quality"), it.optString("note"))) }
            }
            if (root.has("course")) {
                db.classNoteDao().clearCourses()
                each(root, "course") { db.classNoteDao().upsertCourse(com.example.tracker.data.db.CourseEntity(it.getLong("id"), it.getString("code"), it.getString("name"), it.optStringOrNull("teacher"), it.optStringOrNull("room"), it.getLong("color"), it.optString("semester", "Fall 2026"), it.getBoolean("archived"))) }
            }
            if (root.has("block_rule")) {
                db.blockerDao().clearAllRules()
                each(root, "block_rule") { db.blockerDao().upsertRule(com.example.tracker.data.db.BlockRuleEntity(it.getLong("id"), com.example.tracker.data.db.BlockRuleType.valueOf(it.getString("type")), it.getString("name"), it.getString("target_packages"), it.optString("target_domains"), it.getInt("limit_minutes"), it.getInt("session_limit_minutes"), it.getInt("cooldown_minutes"), it.optStringOrNull("start_time")?.let(LocalTime::parse), it.optStringOrNull("end_time")?.let(LocalTime::parse), it.getString("days"), it.getBoolean("strict"), it.getBoolean("enabled"), it.getLong("created_at"))) }
            }
            each(root, "recurring") {
                db.recurringDao().upsert(Recurring(it.getLong("id"), it.getString("title"), it.getLong("amount_paisa"),
                    it.getLong("category_id"), it.getLong("account_id"), it.getInt("day_of_month"), it.getBoolean("active"),
                    it.optStringOrNull("last_confirmed_month")))
            }
        }
    }

    /** Wipes all user data and resets the entire app to a completely blank, factory-fresh state. */
    suspend fun resetToBlankApp() {
        db.withTransaction {
            // 1. Wipe habits, logs, and reviews
            db.dayLogDao().clear()
            db.habitCheckDao().clear()
            db.weeklyReviewDao().clear()

            // 2. Wipe all settings (including onboarding_done, custom preferences, and weather cache)
            db.settingsDao().clear()

            // 3. Wipe all financial accounts, transactions, budgets, recurring
            db.txnDao().clear()
            db.budgetDao().clear()
            db.recurringDao().clear()
            db.accountDao().clear()
            db.categoryDao().clear()

            // 4. Wipe schedule, timetable, buses, routines, overrides, and notifications
            val sd = db.scheduleDao()
            sd.clearClasses()
            sd.clearBuses()
            sd.clearBlocks()
            sd.clearPhases()
            sd.clearWakeOverrides()
            sd.clearHolidays()
            sd.clearRoutines()
            db.notificationDao().clearPrefs()
            db.notificationDao().clearLog()

            // 5. Wipe calendar events, notes, exceptions, reminders
            val cd = db.calendarDao()
            cd.clearReminders()
            cd.clearExceptions()
            cd.clearEvents()
            cd.clearNotes()

            // 6. Wipe focus sessions and sleep log
            db.focusDao().clear()
            db.sleepDao().clear()

            // 7. Wipe class notes and courses
            db.classNoteDao().clearImages()
            db.classNoteDao().clearNotes()
            db.classNoteDao().clearCourses()

            // 8. Wipe blocker data
            val bd = db.blockerDao()
            bd.clearAllEvents()
            bd.clearAllUsage()
            bd.clearAllUnlocks()
            bd.clearAllRules()
            bd.clearAllReelsUsage()
            db.reportCacheDao().clear()

            // 9. Seed only default cash account & basic categories so finance does not crash
            Migrations.seedFinance(db.openHelper.writableDatabase)

            // 10. Seed a clean neutral baseline phase (wake 7:00, bed 23:00, 0 prayers, 0 expected blocks)
            // so phaseForDay never crashes, but schedule, calendar and timetable remain 100% blank!
            sd.upsertPhase(PlanPhaseEntity(
                number = 1,
                firstDay = 1,
                lastDay = 30,
                wake = LocalTime.of(7, 0),
                bed = LocalTime.of(23, 0),
                prayers = "",
                expectedBlocks = 0,
            ))

            val today = LocalDate.now(Schedule.ZONE)
            db.settingsDao().put(Setting("plan_start", today.toString()))
            db.settingsDao().put(Setting("plan_days", "30"))
            db.settingsDao().put(Setting("screens_off_lead", "30"))
            db.settingsDao().put(Setting("onboarding_done", "false"))
        }

        // 11. Delete physical photo files on disk
        context?.let { ctx ->
            withContext(Dispatchers.IO) {
                runCatching {
                    File(ctx.filesDir, "notes").deleteRecursively()
                }
            }
        }

        // 12. Reload schedule explicitly into memory & PlanCalendar.config
        scheduleRepo?.load()
    }

    /** Spreadsheet-friendly CSV of all transactions (amounts in taka with 2 decimals). */
    suspend fun exportTransactionsCsv(): String {
        val accounts = db.accountDao().all().associateBy { it.id }
        val cats = db.categoryDao().all().associateBy { it.id }
        val sb = StringBuilder("date_time,type,amount_bdt,account,to_account,category,note\n")
        db.txnDao().all().forEach { t ->
            val amount = Money.toInput(t.amountPaisa).let { if ('.' in it) it.padEnd(it.indexOf('.') + 3, '0') else "$it.00" }
            sb.append(listOf(t.dateTime.toString(), t.type.name, amount, accounts[t.accountId]?.name ?: "",
                t.toAccountId?.let { accounts[it]?.name } ?: "", t.categoryId?.let { cats[it]?.name } ?: "", t.note)
                .joinToString(",") { csv(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun csv(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private inline fun <T> arr(items: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> items.forEach { a.put(f(it)) } }

    private suspend fun each(root: JSONObject, key: String, f: suspend (JSONObject) -> Unit) {
        val a = root.optJSONArray(key) ?: return
        for (i in 0 until a.length()) f(a.getJSONObject(i))
    }

    private fun JSONObject.optStringOrNull(k: String) = if (isNull(k) || !has(k)) null else getString(k)
    private fun JSONObject.optIntOrNull(k: String) = if (isNull(k) || !has(k)) null else getInt(k)
    private fun JSONObject.optLongOrNull(k: String) = if (isNull(k) || !has(k)) null else getLong(k)
}
