package com.example.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        // v1 — habits
        DayLog::class, HabitCheck::class, WeeklyReview::class, Setting::class,
        // v2 — finance
        Account::class, Category::class, Txn::class, Budget::class, Recurring::class,
        // v3 — editable schedule + notification control
        ClassSessionEntity::class, BusTimeEntity::class, FocusBlockEntity::class, PlanPhaseEntity::class,
        WakeOverrideEntity::class, HolidayEntity::class, RoutineEntity::class, ReminderPrefEntity::class, NotificationLogEntity::class,
        // v4 — calendar
        EventEntity::class, EventExceptionEntity::class, EventReminderEntity::class, DayNoteEntity::class,
        // v5 — focus timer
        FocusSessionEntity::class,
        // v6 — class notes & photos
        CourseEntity::class, ClassNoteEntity::class, NoteImageEntity::class,
        // v7 — app blocker & usage limits
        BlockRuleEntity::class, AppUsageEntity::class, BlockEventEntity::class, EmergencyUnlockEntity::class,
        // v8 — reels & shorts counter
        ReelsUsageEntity::class,
        // v9 — progress reports & share cards
        ReportCacheEntity::class,
        // v10 — sleep log
        SleepLogEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dayLogDao(): DayLogDao
    abstract fun habitCheckDao(): HabitCheckDao
    abstract fun weeklyReviewDao(): WeeklyReviewDao
    abstract fun settingsDao(): SettingsDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun notificationDao(): NotificationDao
    abstract fun calendarDao(): CalendarDao
    abstract fun focusDao(): FocusDao
    abstract fun classNoteDao(): ClassNoteDao
    abstract fun blockerDao(): BlockerDao
    abstract fun reportCacheDao(): ReportCacheDao
    abstract fun sleepDao(): SleepDao

    companion object {
        const val NAME = "tracker.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .addCallback(object : Callback() {
                    // Fresh install at the latest version: seed what the migrations would have.
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Migrations.seedFinance(db)
                        Migrations.seedSchedule(db)
                        Migrations.seedCourses(db)
                        Migrations.seedBlockRules(db)
                    }
                })
                .build()
    }
}
