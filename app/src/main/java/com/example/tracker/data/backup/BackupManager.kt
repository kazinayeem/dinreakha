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
        val C = BackupCodec
        val root = JSONObject()
        root.put("format", C.FORMAT)
        root.put("version", C.VERSION)
        root.put("exported_at", LocalDateTime.now(Schedule.ZONE).toString())
        root.put("day_log", C.arr(db.dayLogDao().all(), C::dayLog))
        root.put("habit_check", C.arr(db.habitCheckDao().all(), C::habitCheck))
        root.put("weekly_review", C.arr(db.weeklyReviewDao().all(), C::review))
        root.put("settings", C.arr(db.settingsDao().all(), C::setting))
        root.put("account", C.arr(db.accountDao().all(), C::account))
        root.put("category", C.arr(db.categoryDao().all(), C::category))
        root.put("txn", C.arr(db.txnDao().all(), C::txn))
        root.put("budget", C.arr(db.budgetDao().all(), C::budget))
        root.put("recurring", C.arr(db.recurringDao().all(), C::recurring))
        val sd = db.scheduleDao()
        root.put("class_session", C.arr(sd.classes(), C::classSession))
        root.put("bus_time", C.arr(sd.buses(), C::bus))
        root.put("focus_block", C.arr(sd.blocks(), C::block))
        root.put("plan_phase", C.arr(sd.phases(), C::phase))
        root.put("wake_override", C.arr(sd.wakeOverrides(), C::wakeOverride))
        root.put("holiday", C.arr(sd.holidays(), C::holiday))
        root.put("routine", C.arr(sd.routines(), C::routine))
        root.put("reminder_pref", C.arr(db.notificationDao().prefs(), C::reminderPref))
        val cd = db.calendarDao()
        root.put("event", C.arr(cd.events(), C::event))
        root.put("event_exception", C.arr(cd.exceptions(), C::eventException))
        root.put("event_reminder", C.arr(cd.reminders(), C::eventReminder))
        root.put("day_note", C.arr(cd.notes(), C::dayNote))
        root.put("focus_session", C.arr(db.focusDao().all(), C::focusSession))
        root.put("course", C.arr(db.classNoteDao().allCourses(), C::course))
        root.put("block_rule", C.arr(db.blockerDao().getAllRules(), C::blockRule))
        // Class notes and photo references (the photo files themselves stay in the app folder on this phone).
        root.put("class_note", C.arr(db.classNoteDao().allNotesForBackup(), C::classNote))
        root.put("note_image", C.arr(db.classNoteDao().allImagesForBackup(), C::noteImage))
        root.put("sleep_log", C.arr(db.sleepDao().all(), C::sleep))
        val text = root.toString(2)
        // Never hand out a file we couldn't read back ourselves.
        BackupCodec.parse(text)
        return text
    }

    /** Reads and checks a backup without changing anything; throws [BackupCodec.BadBackup] if it can't be restored. */
    fun inspect(text: String): BackupCodec.Contents = BackupCodec.parse(text)

    /**
     * Replaces ALL data with the backup. The whole file is read and checked first, so a bad file
     * changes nothing; the writes then run in one transaction (all-or-nothing).
     */
    suspend fun importJson(text: String) {
        val b = BackupCodec.parse(text)
        db.withTransaction {
            db.dayLogDao().clear(); db.habitCheckDao().clear(); db.weeklyReviewDao().clear(); db.settingsDao().clear()
            db.txnDao().clear(); db.budgetDao().clear(); db.recurringDao().clear()
            db.accountDao().clear(); db.categoryDao().clear()

            b.dayLogs.forEach { db.dayLogDao().upsert(it) }
            b.habitChecks.forEach { db.habitCheckDao().put(it) }
            b.reviews.forEach { db.weeklyReviewDao().upsert(it) }
            b.settings.forEach { db.settingsDao().put(it) }
            b.accounts.forEach { db.accountDao().upsert(it) }
            b.categories.forEach { db.categoryDao().upsert(it) }
            b.txns.forEach { db.txnDao().insert(it) }
            b.budgets.forEach { db.budgetDao().upsert(it) }
            b.recurring.forEach { db.recurringDao().upsert(it) }

            // Newer tables are only replaced when the backup contains them (older backups keep what's here).
            b.schedule?.let { s ->
                val sd = db.scheduleDao()
                sd.clearClasses(); sd.clearBuses(); sd.clearBlocks(); sd.clearPhases(); sd.clearWakeOverrides(); sd.clearHolidays(); sd.clearRoutines()
                db.notificationDao().clearPrefs()
                s.classes.forEach { sd.upsertClass(it) }
                s.buses.forEach { sd.upsertBus(it) }
                s.blocks.forEach { sd.upsertBlock(it) }
                s.phases.forEach { sd.upsertPhase(it) }
                s.wakeOverrides.forEach { sd.upsertWakeOverride(it) }
                s.holidays.forEach { sd.upsertHoliday(it) }
                s.routines.forEach { sd.upsertRoutine(it) }
                s.reminderPrefs.forEach { db.notificationDao().upsertPref(it) }
            }
            b.calendar?.let { cal ->
                val cd = db.calendarDao()
                cd.clearReminders(); cd.clearExceptions(); cd.clearEvents(); cd.clearNotes()
                cal.events.forEach { cd.upsertEvent(it) }
                cal.exceptions.forEach { cd.putException(it) }
                if (cal.reminders.isNotEmpty()) cd.insertReminders(cal.reminders)
                cal.dayNotes.forEach { cd.upsertNote(it) }
            }
            b.focusSessions?.let { list -> db.focusDao().clear(); list.forEach { db.focusDao().insert(it) } }
            b.courses?.let { list -> db.classNoteDao().clearCourses(); list.forEach { db.classNoteDao().upsertCourse(it) } }
            b.classNotes?.let { list ->
                db.classNoteDao().clearImages(); db.classNoteDao().clearNotes()
                list.forEach { db.classNoteDao().restoreNote(it) }
                b.noteImages.orEmpty().forEach { db.classNoteDao().restoreImage(it) }
            }
            b.sleep?.let { list -> db.sleepDao().clear(); list.forEach { db.sleepDao().upsert(it) } }
            b.blockRules?.let { list -> db.blockerDao().clearAllRules(); list.forEach { db.blockerDao().upsertRule(it) } }
            // Cached report scores belong to the old data.
            db.reportCacheDao().clear()
        }
        scheduleRepo?.load()
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

}
