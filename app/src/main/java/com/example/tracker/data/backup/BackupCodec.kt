package com.example.tracker.data.backup

import com.example.tracker.data.db.Account
import com.example.tracker.data.db.AccountType
import com.example.tracker.data.db.BlockRuleEntity
import com.example.tracker.data.db.BlockRuleType
import com.example.tracker.data.db.Budget
import com.example.tracker.data.db.BusTimeEntity
import com.example.tracker.data.db.Category
import com.example.tracker.data.db.CategoryKind
import com.example.tracker.data.db.ClassNoteEntity
import com.example.tracker.data.db.ClassSessionEntity
import com.example.tracker.data.db.CourseEntity
import com.example.tracker.data.db.DayLog
import com.example.tracker.data.db.DayNoteEntity
import com.example.tracker.data.db.EventEntity
import com.example.tracker.data.db.EventExceptionEntity
import com.example.tracker.data.db.EventReminderEntity
import com.example.tracker.data.db.FocusBlockEntity
import com.example.tracker.data.db.FocusSessionEntity
import com.example.tracker.data.db.HabitCheck
import com.example.tracker.data.db.HolidayEntity
import com.example.tracker.data.db.NoteImageEntity
import com.example.tracker.data.db.PlanPhaseEntity
import com.example.tracker.data.db.Recurring
import com.example.tracker.data.db.ReminderPrefEntity
import com.example.tracker.data.db.RoutineEntity
import com.example.tracker.data.db.Setting
import com.example.tracker.data.db.SleepLogEntity
import com.example.tracker.data.db.Txn
import com.example.tracker.data.db.TxnType
import com.example.tracker.data.db.WakeOverrideEntity
import com.example.tracker.data.db.WeeklyReview
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Row ⇄ JSON mapping for the backup file. Pure (no Room, no Android), so the whole format is
 * round-trip tested on the JVM. Every value is written as a string, number or boolean — never as
 * a raw object — and every nullable value as JSON null (org.json's put(k, null) would drop the key).
 */
object BackupCodec {

    const val FORMAT = "dinrekha-backup"
    const val VERSION = 6

    /** A backup row that can't be read. The message names the table, the row and the reason. */
    class BadBackup(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Everything in a backup, already turned back into rows. Null = that table isn't in the file (older backup). */
    data class Contents(
        val exportedAt: String?,
        val dayLogs: List<DayLog>,
        val habitChecks: List<HabitCheck>,
        val reviews: List<WeeklyReview>,
        val settings: List<Setting>,
        val accounts: List<Account>,
        val categories: List<Category>,
        val txns: List<Txn>,
        val budgets: List<Budget>,
        val recurring: List<Recurring>,
        val schedule: Schedule?,
        val calendar: Calendar?,
        val focusSessions: List<FocusSessionEntity>?,
        val courses: List<CourseEntity>?,
        val classNotes: List<ClassNoteEntity>?,
        val noteImages: List<NoteImageEntity>?,
        val sleep: List<SleepLogEntity>?,
        val blockRules: List<BlockRuleEntity>?,
    ) {
        /** Short human summary for the "Replace all data?" dialog. */
        fun summary(): String = buildList {
            add("${txns.size} transactions")
            add("${habitChecks.size} habit checks")
            calendar?.let { add("${it.events.size} events") }
            courses?.let { add("${it.size} courses") }
            classNotes?.let { add("${it.size} class notes") }
            sleep?.let { add("${it.size} nights of sleep") }
        }.joinToString(", ")
    }

    data class Schedule(
        val classes: List<ClassSessionEntity>,
        val buses: List<BusTimeEntity>,
        val blocks: List<FocusBlockEntity>,
        val phases: List<PlanPhaseEntity>,
        val wakeOverrides: List<WakeOverrideEntity>,
        val holidays: List<HolidayEntity>,
        val routines: List<RoutineEntity>,
        val reminderPrefs: List<ReminderPrefEntity>,
    )

    data class Calendar(
        val events: List<EventEntity>,
        val exceptions: List<EventExceptionEntity>,
        val reminders: List<EventReminderEntity>,
        val dayNotes: List<DayNoteEntity>,
    )

    // ---- writing ---------------------------------------------------------

    private fun o() = JSONObject()
    private fun JSONObject.s(k: String, v: Any?) = put(k, v?.toString() ?: JSONObject.NULL)
    private fun JSONObject.n(k: String, v: Number?) = put(k, v ?: JSONObject.NULL)

    fun dayLog(it: DayLog) = o().s("date", it.date).n("mood", it.mood).s("note", it.note)
    fun habitCheck(it: HabitCheck) = o().s("date", it.date).s("habit_key", it.habitKey).put("done", it.done).s("done_at", it.doneAt)
    fun review(it: WeeklyReview) = o().put("week_no", it.weekNo).s("went_well", it.wentWell).s("got_in_way", it.gotInWay)
        .s("next_change", it.nextChange).put("created_at", it.createdAt)
    fun setting(it: Setting) = o().s("key", it.key).s("value", it.value)
    fun account(it: Account) = o().put("id", it.id).s("name", it.name).s("type", it.type.name)
        .put("opening_balance_paisa", it.openingBalancePaisa).s("opening_date", it.openingDate)
        .s("last4", it.last4).put("color", it.color).put("archived", it.archived)
    fun category(it: Category) = o().put("id", it.id).s("name", it.name).s("icon", it.icon).s("kind", it.kind.name)
    fun txn(it: Txn) = o().put("id", it.id).s("type", it.type.name).put("amount_paisa", it.amountPaisa)
        .put("account_id", it.accountId).n("to_account_id", it.toAccountId).n("category_id", it.categoryId)
        .s("date_time", it.dateTime).s("note", it.note).n("linked_txn_id", it.linkedTxnId)
    fun budget(it: Budget) = o().put("id", it.id).put("category_id", it.categoryId).s("month", it.month).put("limit_paisa", it.limitPaisa)
    fun recurring(it: Recurring) = o().put("id", it.id).s("title", it.title).put("amount_paisa", it.amountPaisa)
        .put("category_id", it.categoryId).put("account_id", it.accountId).put("day_of_month", it.dayOfMonth)
        .put("active", it.active).s("last_confirmed_month", it.lastConfirmedMonth)
    fun classSession(it: ClassSessionEntity) = o().put("day", it.dayOfWeek.value).s("start", it.start).s("end", it.end)
        .s("course", it.course).s("room", it.room)
    fun bus(it: BusTimeEntity) = o().put("day", it.dayOfWeek.value).s("direction", it.direction).s("time", it.time)
    fun block(it: FocusBlockEntity) = o().s("type", it.type).n("day", it.dayOfWeek?.value).s("start", it.start).s("end", it.end).s("place", it.place)
    fun phase(it: PlanPhaseEntity) = o().put("number", it.number).put("first_day", it.firstDay).put("last_day", it.lastDay)
        .s("wake", it.wake).s("bed", it.bed).s("prayers", it.prayers).put("expected_blocks", it.expectedBlocks)
    fun wakeOverride(it: WakeOverrideEntity) = o().put("day", it.dayOfWeek.value).s("time", it.time)
    fun holiday(it: HolidayEntity) = o().s("date", it.date).s("name", it.name)
    fun routine(it: RoutineEntity) = o().put("id", it.id).s("title", it.title).s("icon", it.icon).s("repeat", it.repeat)
        .s("days", it.days).put("day_of_month", it.dayOfMonth).s("time", it.time).put("remind", it.remind).put("active", it.active)
    fun reminderPref(it: ReminderPrefEntity) = o().s("category", it.category).put("enabled", it.enabled).put("offset_min", it.offsetMin)
        .s("time", it.time).s("sound", it.sound).s("ringtone", it.ringtone)
    fun event(it: EventEntity) = o().put("id", it.id).s("title", it.title).s("start", it.start).s("end", it.end)
        .put("all_day", it.allDay).s("location", it.location).s("notes", it.notes).put("color", it.color)
        .s("rrule", it.rrule).s("until", it.until)
    fun eventException(it: EventExceptionEntity) = o().put("event_id", it.eventId).s("original_date", it.originalDate)
        .put("cancelled", it.cancelled).s("title", it.title).s("start", it.start).s("end", it.end)
        .s("location", it.location).s("notes", it.notes)
    fun eventReminder(it: EventReminderEntity) = o().put("event_id", it.eventId).put("minutes_before", it.minutesBefore)
    fun dayNote(it: DayNoteEntity) = o().s("date", it.date).s("text", it.text)
    fun focusSession(it: FocusSessionEntity) = o().s("type", it.type).s("start", it.start).s("end", it.end)
        .put("minutes", it.minutes).s("note", it.note)
    fun course(it: CourseEntity) = o().put("id", it.id).s("code", it.code).s("name", it.name).s("teacher", it.teacher)
        .s("room", it.room).put("color", it.color).s("semester", it.semester).put("archived", it.archived)
    fun blockRule(it: BlockRuleEntity) = o().put("id", it.id).s("type", it.type.name).s("name", it.name)
        .s("target_packages", it.targetPackages).s("target_domains", it.targetDomains).put("limit_minutes", it.limitMinutes)
        .put("session_limit_minutes", it.sessionLimitMinutes).put("cooldown_minutes", it.cooldownMinutes)
        .s("start_time", it.startTime).s("end_time", it.endTime).s("days", it.days).put("strict", it.strict)
        .put("enabled", it.enabled).put("created_at", it.createdAt)
    fun classNote(it: ClassNoteEntity) = o().put("id", it.id).put("course_id", it.courseId).s("date", it.date)
        .s("class_start_time", it.classStartTime).s("title", it.title).s("text", it.text).s("tags", it.tags)
        .put("important", it.important).put("created_at", it.createdAt).put("updated_at", it.updatedAt)
    fun noteImage(it: NoteImageEntity) = o().put("id", it.id).put("note_id", it.noteId).s("file_path", it.filePath)
        .s("thumb_path", it.thumbPath).put("width", it.width).put("height", it.height).s("ocr_text", it.ocrText)
        .put("sort_order", it.sortOrder).put("created_at", it.createdAt)
    fun sleep(it: SleepLogEntity) = o().s("night", it.night).s("bed_at", it.bedAt).s("wake_at", it.wakeAt)
        .n("quality", it.quality).s("note", it.note)

    fun <T> arr(items: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> items.forEach { a.put(f(it)) } }

    // ---- reading ---------------------------------------------------------

    /**
     * Reads a whole backup file into rows without touching the database, so a bad file is rejected
     * before anything is erased. Throws [BadBackup] naming the table and row that failed.
     */
    fun parse(text: String): Contents {
        val root = try {
            JSONObject(text.trim().removePrefix("﻿"))
        } catch (e: Exception) {
            throw BadBackup("This file isn't a readable backup (${e.message?.take(80)})", e)
        }
        if (root.optString("format") != FORMAT && !root.has("habit_check")) throw BadBackup("This file isn't a Dinrekha backup")

        return Contents(
            exportedAt = root.str("exported_at"),
            dayLogs = rows(root, "day_log") { DayLog(date(it, "date"), it.int("mood"), it.text("note")) },
            habitChecks = rows(root, "habit_check") {
                HabitCheck(date = date(it, "date"), habitKey = req(it, "habit_key"), done = bool(it, "done"),
                    doneAt = it.str("done_at")?.let(LocalTime::parse))
            },
            reviews = rows(root, "weekly_review") {
                WeeklyReview(it.getInt("week_no"), it.text("went_well"), it.text("got_in_way"), it.text("next_change"), it.optLong("created_at"))
            },
            settings = rows(root, "settings") { Setting(req(it, "key"), it.text("value")) },
            accounts = rows(root, "account") {
                Account(it.getLong("id"), req(it, "name"), AccountType.valueOf(req(it, "type")), it.optLong("opening_balance_paisa"),
                    date(it, "opening_date"), it.str("last4"), it.getLong("color"), bool(it, "archived"))
            },
            categories = rows(root, "category") {
                Category(it.getLong("id"), req(it, "name"), it.text("icon"), CategoryKind.valueOf(req(it, "kind")))
            },
            txns = rows(root, "txn") {
                Txn(it.getLong("id"), TxnType.valueOf(req(it, "type")), it.getLong("amount_paisa"), it.getLong("account_id"),
                    it.long("to_account_id"), it.long("category_id"), LocalDateTime.parse(req(it, "date_time")), it.text("note"),
                    it.long("linked_txn_id"))
            },
            budgets = rows(root, "budget") { Budget(it.getLong("id"), it.getLong("category_id"), req(it, "month"), it.getLong("limit_paisa")) },
            recurring = rows(root, "recurring") {
                Recurring(it.getLong("id"), req(it, "title"), it.getLong("amount_paisa"), it.getLong("category_id"), it.getLong("account_id"),
                    it.getInt("day_of_month"), bool(it, "active"), it.str("last_confirmed_month"))
            },
            schedule = if (!root.has("class_session")) null else Schedule(
                classes = rows(root, "class_session") {
                    ClassSessionEntity(0, DayOfWeek.of(it.getInt("day")), time(it, "start"), time(it, "end"), req(it, "course"), it.text("room"))
                },
                buses = rows(root, "bus_time") { BusTimeEntity(0, DayOfWeek.of(it.getInt("day")), req(it, "direction"), time(it, "time")) },
                blocks = rows(root, "focus_block") {
                    FocusBlockEntity(0, req(it, "type"), it.int("day")?.let(DayOfWeek::of), time(it, "start"), time(it, "end"), it.text("place"))
                },
                phases = rows(root, "plan_phase") {
                    PlanPhaseEntity(it.getInt("number"), it.getInt("first_day"), it.getInt("last_day"), time(it, "wake"), time(it, "bed"),
                        it.text("prayers"), it.getInt("expected_blocks"))
                },
                wakeOverrides = rows(root, "wake_override") { WakeOverrideEntity(DayOfWeek.of(it.getInt("day")), time(it, "time")) },
                holidays = rows(root, "holiday") { HolidayEntity(date(it, "date"), it.text("name")) },
                routines = rows(root, "routine") {
                    RoutineEntity(it.getLong("id"), req(it, "title"), it.text("icon"), req(it, "repeat"), it.text("days"),
                        it.optInt("day_of_month", 1), it.str("time")?.let(LocalTime::parse), bool(it, "remind"), bool(it, "active"))
                },
                reminderPrefs = rows(root, "reminder_pref") {
                    ReminderPrefEntity(req(it, "category"), bool(it, "enabled"), it.optInt("offset_min"), it.str("time")?.let(LocalTime::parse),
                        it.text("sound").ifEmpty { "SOUND" }, it.str("ringtone"))
                },
            ),
            calendar = if (!root.has("event")) null else Calendar(
                events = rows(root, "event") {
                    EventEntity(it.getLong("id"), req(it, "title"), LocalDateTime.parse(req(it, "start")), LocalDateTime.parse(req(it, "end")),
                        bool(it, "all_day"), it.text("location"), it.text("notes"), it.getLong("color"), it.str("rrule"),
                        it.str("until")?.let(LocalDate::parse))
                },
                exceptions = rows(root, "event_exception") {
                    EventExceptionEntity(0, it.getLong("event_id"), date(it, "original_date"), bool(it, "cancelled"), it.str("title"),
                        it.str("start")?.let(LocalDateTime::parse), it.str("end")?.let(LocalDateTime::parse), it.str("location"), it.str("notes"))
                },
                reminders = rows(root, "event_reminder") { EventReminderEntity(0, it.getLong("event_id"), it.getInt("minutes_before")) },
                dayNotes = rows(root, "day_note") { DayNoteEntity(date(it, "date"), it.text("text")) },
            ),
            focusSessions = if (!root.has("focus_session")) null else rows(root, "focus_session") {
                FocusSessionEntity(0, req(it, "type"), LocalDateTime.parse(req(it, "start")), LocalDateTime.parse(req(it, "end")),
                    it.getInt("minutes"), it.text("note"))
            },
            courses = if (!root.has("course")) null else rows(root, "course") {
                CourseEntity(it.getLong("id"), req(it, "code"), it.text("name"), it.str("teacher"), it.str("room"), it.getLong("color"),
                    it.str("semester") ?: "Fall 2026", bool(it, "archived"))
            },
            classNotes = if (!root.has("class_note")) null else rows(root, "class_note") {
                ClassNoteEntity(it.getLong("id"), it.getLong("course_id"), date(it, "date"), it.str("class_start_time")?.let(LocalTime::parse),
                    it.str("title"), it.text("text"), it.text("tags"), bool(it, "important"), it.optLong("created_at"), it.optLong("updated_at"))
            },
            noteImages = if (!root.has("note_image")) null else rows(root, "note_image") {
                NoteImageEntity(it.getLong("id"), it.getLong("note_id"), req(it, "file_path"), it.text("thumb_path"), it.optInt("width"),
                    it.optInt("height"), it.str("ocr_text"), it.optInt("sort_order"), it.optLong("created_at"))
            },
            sleep = if (!root.has("sleep_log")) null else rows(root, "sleep_log") {
                SleepLogEntity(date(it, "night"), LocalDateTime.parse(req(it, "bed_at")), LocalDateTime.parse(req(it, "wake_at")),
                    it.int("quality"), it.text("note"))
            },
            blockRules = if (!root.has("block_rule")) null else rows(root, "block_rule") {
                BlockRuleEntity(it.getLong("id"), BlockRuleType.valueOf(req(it, "type")), it.text("name"), it.text("target_packages"),
                    it.text("target_domains"), it.optInt("limit_minutes"), it.optInt("session_limit_minutes"), it.optInt("cooldown_minutes"),
                    it.str("start_time")?.let(LocalTime::parse), it.str("end_time")?.let(LocalTime::parse), it.text("days"),
                    bool(it, "strict"), bool(it, "enabled"), it.optLong("created_at"))
            },
        )
    }

    /** Every row of [table], or a [BadBackup] that says which row and why. */
    private fun <T> rows(root: JSONObject, table: String, f: (JSONObject) -> T): List<T> {
        val a = root.optJSONArray(table) ?: return emptyList()
        return (0 until a.length()).map { i ->
            try {
                f(a.getJSONObject(i))
            } catch (e: Exception) {
                throw BadBackup("Backup table \"$table\", row ${i + 1}: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }
    }

    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else get(k).toString()
    private fun JSONObject.text(k: String): String = str(k) ?: ""
    private fun JSONObject.int(k: String): Int? = if (!has(k) || isNull(k)) null else getInt(k)
    private fun JSONObject.long(k: String): Long? = if (!has(k) || isNull(k)) null else getLong(k)
    private fun req(o: JSONObject, k: String): String = o.str(k) ?: throw BadBackup("missing \"$k\"")
    private fun date(o: JSONObject, k: String): LocalDate = LocalDate.parse(req(o, k))
    private fun time(o: JSONObject, k: String): LocalTime = LocalTime.parse(req(o, k))
    /** Accepts true/false, 1/0 and "true"/"1" — older files and hand edits use all of them. */
    private fun bool(o: JSONObject, k: String): Boolean = when (val v = if (o.has(k)) o.get(k) else null) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "true" || v == "1"
        else -> false
    }
}
