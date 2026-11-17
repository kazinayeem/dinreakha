package com.example.tracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tracker.data.schedule.ScheduleConfig

/**
 * Schema history (JSON snapshots in app/schemas/):
 *  v1 — habits: day_log, habit_check, weekly_review, settings
 *  v2 — finance: account, category, txn, budget, recurring (+ default categories)
 *  v3 — editable schedule (class_session, bus_time, focus_block, plan_phase, wake_override, holiday, routine)
 *       + notification control (reminder_pref, notification_log), seeded from Schedule.DEFAULT_CONFIG
 *  v4 — calendar: event, event_exception, event_reminder, day_note
 *  v5 — focus timer: focus_session
 *
 * To change the schema: bump `version` in AppDatabase, add MIGRATION_n_(n+1) here
 * (copy CREATE statements from the newly exported schemas/<n+1>.json), add it to ALL.
 * Never use destructive migration — this is the only copy of the data.
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `account` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `opening_balance_paisa` INTEGER NOT NULL, `opening_date` TEXT NOT NULL, `last4` TEXT, `color` INTEGER NOT NULL, `archived` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `category` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `kind` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `txn` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `amount_paisa` INTEGER NOT NULL, `account_id` INTEGER NOT NULL, `to_account_id` INTEGER, `category_id` INTEGER, `date_time` TEXT NOT NULL, `note` TEXT NOT NULL, `linked_txn_id` INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_date_time` ON `txn` (`date_time`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_account_id` ON `txn` (`account_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_category_id` ON `txn` (`category_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_to_account_id` ON `txn` (`to_account_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_linked_txn_id` ON `txn` (`linked_txn_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `budget` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category_id` INTEGER NOT NULL, `month` TEXT NOT NULL, `limit_paisa` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_category_id_month` ON `budget` (`category_id`, `month`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `amount_paisa` INTEGER NOT NULL, `category_id` INTEGER NOT NULL, `account_id` INTEGER NOT NULL, `day_of_month` INTEGER NOT NULL, `active` INTEGER NOT NULL, `last_confirmed_month` TEXT)")
            seedFinance(db)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `class_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day_of_week` INTEGER NOT NULL, `start` TEXT NOT NULL, `end` TEXT NOT NULL, `course` TEXT NOT NULL, `room` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `bus_time` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day_of_week` INTEGER NOT NULL, `direction` TEXT NOT NULL, `time` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `focus_block` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `day_of_week` INTEGER, `start` TEXT NOT NULL, `end` TEXT NOT NULL, `place` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `plan_phase` (`number` INTEGER NOT NULL, `first_day` INTEGER NOT NULL, `last_day` INTEGER NOT NULL, `wake` TEXT NOT NULL, `bed` TEXT NOT NULL, `prayers` TEXT NOT NULL, `expected_blocks` INTEGER NOT NULL, PRIMARY KEY(`number`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `wake_override` (`day_of_week` INTEGER NOT NULL, `time` TEXT NOT NULL, PRIMARY KEY(`day_of_week`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `holiday` (`date` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`date`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `routine` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `icon` TEXT NOT NULL, `repeat` TEXT NOT NULL, `days` TEXT NOT NULL, `day_of_month` INTEGER NOT NULL, `time` TEXT, `remind` INTEGER NOT NULL, `active` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_pref` (`category` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `offset_min` INTEGER NOT NULL, `time` TEXT, `sound` TEXT NOT NULL, `ringtone` TEXT, PRIMARY KEY(`category`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `notification_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `slot` TEXT NOT NULL, `category` TEXT NOT NULL, `title` TEXT NOT NULL, `fired_at` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_log_fired_at` ON `notification_log` (`fired_at`)")
            seedSchedule(db)
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `start` TEXT NOT NULL, `end` TEXT NOT NULL, `all_day` INTEGER NOT NULL, `location` TEXT NOT NULL, `notes` TEXT NOT NULL, `color` INTEGER NOT NULL, `rrule` TEXT, `until` TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `event_exception` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `event_id` INTEGER NOT NULL, `original_date` TEXT NOT NULL, `cancelled` INTEGER NOT NULL, `title` TEXT, `start` TEXT, `end` TEXT, `location` TEXT, `notes` TEXT)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_event_exception_event_id_original_date` ON `event_exception` (`event_id`, `original_date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `event_reminder` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `event_id` INTEGER NOT NULL, `minutes_before` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_reminder_event_id` ON `event_reminder` (`event_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `day_note` (`date` TEXT NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`date`))")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `focus_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `start` TEXT NOT NULL, `end` TEXT NOT NULL, `minutes` INTEGER NOT NULL, `note` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_session_start` ON `focus_session` (`start`)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `course` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT, `room` TEXT, `color` INTEGER NOT NULL, `semester` TEXT NOT NULL, `archived` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `class_note` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `course_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `class_start_time` TEXT, `title` TEXT, `text` TEXT NOT NULL, `tags` TEXT NOT NULL, `important` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_class_note_course_id_date` ON `class_note` (`course_id`, `date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_class_note_date` ON `class_note` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `note_image` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `note_id` INTEGER NOT NULL, `file_path` TEXT NOT NULL, `thumb_path` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `ocr_text` TEXT, `sort_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_image_note_id` ON `note_image` (`note_id`)")
            seedCourses(db)
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `block_rule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `target_packages` TEXT NOT NULL, `target_domains` TEXT NOT NULL, `limit_minutes` INTEGER NOT NULL, `session_limit_minutes` INTEGER NOT NULL, `cooldown_minutes` INTEGER NOT NULL, `start_time` TEXT, `end_time` TEXT, `days` TEXT NOT NULL, `strict` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_usage` (`date` TEXT NOT NULL, `package_name` TEXT NOT NULL, `foreground_ms` INTEGER NOT NULL, `opens` INTEGER NOT NULL, `last_opened_at` INTEGER NOT NULL, PRIMARY KEY(`date`, `package_name`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_usage_date` ON `app_usage` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `block_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `package_name` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `reason` TEXT NOT NULL, `emergency_used` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_block_event_timestamp` ON `block_event` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_block_event_package_name` ON `block_event` (`package_name`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `emergency_unlock` (`date` TEXT NOT NULL, `unlock_count` INTEGER NOT NULL, `last_unlock_time` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            seedBlockRules(db)
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `reels_usage` (`date` TEXT NOT NULL, `platform` TEXT NOT NULL, `count` INTEGER NOT NULL, `last_increment_at` INTEGER NOT NULL, PRIMARY KEY(`date`, `platform`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reels_usage_date` ON `reels_usage` (`date`)")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `report_cache` (`period_type` TEXT NOT NULL, `period_start` TEXT NOT NULL, `json` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`period_type`, `period_start`))")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `sleep_log` (`night` TEXT NOT NULL, `bed_at` TEXT NOT NULL, `wake_at` TEXT NOT NULL, `quality` INTEGER, `note` TEXT NOT NULL, PRIMARY KEY(`night`))")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)

    /** Writes Schedule.DEFAULT_CONFIG into the v3 tables (also used by "Reset to defaults"). */
    fun seedSchedule(db: SupportSQLiteDatabase, cfg: ScheduleConfig = ScheduleConfig.DEFAULT) {
        cfg.classes.forEach {
            db.execSQL("INSERT INTO class_session(day_of_week, start, end, course, room) VALUES (?,?,?,?,?)",
                arrayOf(it.day.value, it.start.toString(), it.end.toString(), it.title, it.room))
        }
        cfg.buses.forEach {
            db.execSQL("INSERT INTO bus_time(day_of_week, direction, time) VALUES (?,?,?)",
                arrayOf(it.day.value, it.direction.name, it.time.toString()))
        }
        cfg.blocks.forEach {
            db.execSQL("INSERT INTO focus_block(type, day_of_week, start, end, place) VALUES (?,?,?,?,?)",
                arrayOf(it.type.name, it.day?.value, it.start.toString(), it.end.toString(), it.place))
        }
        cfg.phases.forEach {
            db.execSQL("INSERT OR REPLACE INTO plan_phase(number, first_day, last_day, wake, bed, prayers, expected_blocks) VALUES (?,?,?,?,?,?,?)",
                arrayOf(it.number, it.days.first, it.days.last, it.wake.toString(), it.bed.toString(),
                    it.prayers.joinToString(",") { p -> p.key }, it.expectedFocusBlocks))
        }
        cfg.wakeOverrides.forEach { (d, t) ->
            db.execSQL("INSERT OR REPLACE INTO wake_override(day_of_week, time) VALUES (?,?)", arrayOf(d.value, t.toString()))
        }
        cfg.holidays.forEach { (d, n) ->
            db.execSQL("INSERT OR REPLACE INTO holiday(date, name) VALUES (?,?)", arrayOf(d.toString(), n))
        }
        db.execSQL("INSERT OR REPLACE INTO settings(`key`, value) VALUES ('plan_start', ?)", arrayOf(cfg.planStart.toString()))
        db.execSQL("INSERT OR REPLACE INTO settings(`key`, value) VALUES ('plan_days', ?)", arrayOf(cfg.planDays.toString()))
        db.execSQL("INSERT OR REPLACE INTO settings(`key`, value) VALUES ('screens_off_lead', ?)", arrayOf(cfg.screensOffLeadMin.toString()))
    }

    val DEFAULT_EXPENSE_CATEGORIES = listOf(
        "Food" to "🍚", "Transport/Bus" to "🚌", "Rent" to "🏠", "Utilities & bills" to "💡",
        "Mobile & internet" to "📶", "Education/books" to "📚", "Tech & software" to "💻",
        "Health" to "💊", "Family" to "👪", "Shopping" to "🛍️", "Entertainment" to "🎬",
        FEES_CATEGORY to "🧾", "Other" to "📦",
    )
    val DEFAULT_INCOME_CATEGORIES = listOf(
        "Salary" to "💼", "Freelance/project" to "🧑‍💻", "Family" to "👪", "Other income" to "➕",
    )

    /** Default categories + one Cash account so the wallet works on first open. */
    fun seedFinance(db: SupportSQLiteDatabase) {
        DEFAULT_EXPENSE_CATEGORIES.forEach { (name, icon) ->
            db.execSQL("INSERT INTO category(name, icon, kind) VALUES (?, ?, 'EXPENSE')", arrayOf(name, icon))
        }
        DEFAULT_INCOME_CATEGORIES.forEach { (name, icon) ->
            db.execSQL("INSERT INTO category(name, icon, kind) VALUES (?, ?, 'INCOME')", arrayOf(name, icon))
        }
        db.execSQL(
            "INSERT INTO account(name, type, opening_balance_paisa, opening_date, last4, color, archived) " +
                "VALUES ('Cash', 'CASH', 0, date('now', 'localtime'), NULL, ${0xFF3E8E63}, 0)"
        )
    }

    /** Default courses seeded from DIU SWE schedule. */
    fun seedCourses(db: SupportSQLiteDatabase) {
        val defaultCourses = listOf(
            CourseSeed("SE544", "Intro to Machine Learning", "Dr. A. Rahman", "KT-604", 0xFF3F51B5),
            CourseSeed("SE333", "Artificial Intelligence", "Dr. M. Hasan", "KT-712B", 0xFF009688),
            CourseSeed("SE331", "SE Design Capstone", "Prof. K. Ahmed", "ANNEX-410", 0xFFE91E63),
            CourseSeed("SE332", "Information System Security", "Dr. S. Islam", "KT-701B", 0xFFFF5722),
            CourseSeed("SE334", "AI Lab", "Engr. N. Kabir", "KT-710", 0xFF00BCD4),
            CourseSeed("MATH", "Statistics", "Prof. B. Roy", "", 0xFF4CAF50),
        )
        defaultCourses.forEach { c ->
            db.execSQL(
                "INSERT INTO course(code, name, teacher, room, color, semester, archived) VALUES (?, ?, ?, ?, ?, 'Fall 2026', 0)",
                arrayOf(c.code, c.name, c.teacher, c.room, c.color),
            )
        }
    }

    /** Default starter blocker rules: Sleep Mode, Focus Guard, and Social Media Limits. */
    fun seedBlockRules(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        // 1. Sleep Mode (Schedule block)
        db.execSQL(
            "INSERT INTO block_rule(type, name, target_packages, target_domains, limit_minutes, session_limit_minutes, cooldown_minutes, start_time, end_time, days, strict, enabled, created_at) " +
                "VALUES ('SCHEDULE', 'Sleep Mode', 'com.facebook.katana,com.facebook.orca,com.instagram.android,com.google.android.youtube,com.zhiliaoapp.musically', 'facebook.com,instagram.com,youtube.com,tiktok.com', 0, 0, 0, '23:00', '05:05', '', 1, 1, ?)",
            arrayOf<Any?>(now),
        )
        // 2. Focus Session Guard
        db.execSQL(
            "INSERT INTO block_rule(type, name, target_packages, target_domains, limit_minutes, session_limit_minutes, cooldown_minutes, start_time, end_time, days, strict, enabled, created_at) " +
                "VALUES ('FOCUS', 'Focus Session Guard', 'com.facebook.katana,com.facebook.orca,com.instagram.android,com.google.android.youtube,com.zhiliaoapp.musically', 'facebook.com,instagram.com,youtube.com,tiktok.com', 0, 0, 0, NULL, NULL, '', 1, 1, ?)",
            arrayOf<Any?>(now),
        )
        // 3. Class Hours Silence
        db.execSQL(
            "INSERT INTO block_rule(type, name, target_packages, target_domains, limit_minutes, session_limit_minutes, cooldown_minutes, start_time, end_time, days, strict, enabled, created_at) " +
                "VALUES ('CLASS', 'Class Time Silence', 'com.facebook.katana,com.facebook.orca,com.instagram.android,com.google.android.youtube,com.zhiliaoapp.musically', 'facebook.com,instagram.com,youtube.com,tiktok.com', 0, 0, 0, NULL, NULL, '', 0, 1, ?)",
            arrayOf<Any?>(now),
        )
        // 4. Facebook Daily Limit (30m)
        db.execSQL(
            "INSERT INTO block_rule(type, name, target_packages, target_domains, limit_minutes, session_limit_minutes, cooldown_minutes, start_time, end_time, days, strict, enabled, created_at) " +
                "VALUES ('DAILY_LIMIT', 'Facebook', 'com.facebook.katana,com.facebook.lite', 'facebook.com,m.facebook.com', 30, 10, 15, NULL, NULL, '', 0, 1, ?)",
            arrayOf<Any?>(now),
        )
        // 5. YouTube Daily Limit (45m)
        db.execSQL(
            "INSERT INTO block_rule(type, name, target_packages, target_domains, limit_minutes, session_limit_minutes, cooldown_minutes, start_time, end_time, days, strict, enabled, created_at) " +
                "VALUES ('DAILY_LIMIT', 'YouTube', 'com.google.android.youtube', 'youtube.com,m.youtube.com', 45, 20, 15, NULL, NULL, '', 0, 1, ?)",
            arrayOf<Any?>(now),
        )
    }
}

private data class CourseSeed(val code: String, val name: String, val teacher: String, val room: String, val color: Long)

const val FEES_CATEGORY = "Fees & charges"
