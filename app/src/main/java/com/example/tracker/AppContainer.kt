package com.example.tracker

import android.app.Application
import android.content.Context
import com.example.tracker.alarm.AlarmScheduler
import com.example.tracker.alarm.Notifications
import com.example.tracker.data.backup.BackupManager
import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.repo.FinanceRepository
import com.example.tracker.data.repo.HabitRepository
import com.example.tracker.data.repo.ScheduleRepository
import com.example.tracker.data.repo.SettingsRepository
import com.example.tracker.data.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.ScheduleConfig

/** Manual dependency injection: one instance of each, created lazily. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val db: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings by lazy { SettingsRepository(db) }
    val schedule by lazy { ScheduleRepository(db) }
    val calendar by lazy { com.example.tracker.data.repo.CalendarRepository(db) }
    val focus by lazy { com.example.tracker.data.repo.FocusRepository(db, habits) }
    val focusController by lazy { com.example.tracker.focus.FocusController(appContext, this) }
    val habits by lazy { HabitRepository(db, scheduleState) }
    val finance by lazy { FinanceRepository(db) }
    val backup by lazy { BackupManager(db, appContext, schedule) }
    val weather by lazy { WeatherRepository(appContext, settings) }
    val notes by lazy { com.example.tracker.data.repo.ClassNoteRepository(appContext, db, schedule, appScope) }
    val blocker by lazy { com.example.tracker.data.repo.BlockerRepository(appContext, db, focus, schedule) }
    val reports by lazy { com.example.tracker.data.repo.ReportRepository(appContext, db, schedule) }
    val scheduler by lazy { AlarmScheduler(appContext, this) }

    /** Lives as long as the process — for fire-and-forget work like rescheduling. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The live plan, shared by every screen (also mirrored into PlanCalendar.config). */
    val scheduleState: StateFlow<ScheduleConfig> by lazy {
        schedule.config.stateIn(appScope, SharingStarted.Eagerly, PlanCalendar.config)
    }

    fun rescheduleAsync() { appScope.launch { scheduler.rescheduleAll() } }
}

class TrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scheduleState // start mirroring the editable plan from the database
        Notifications.syncChannels(this, null)
        container.focusController.ensureChannels()
        // Language (and 12/24h) apply app-wide, also to notifications and widgets, from the first moment.
        container.appScope.launch { container.settings.settings.collect { } }
        // Home-screen widgets follow the data: redraw shortly after any relevant table changes.
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        container.appScope.launch {
            container.db.invalidationTracker
                .createFlow("habit_check", "txn", "day_log", "sleep_log", "reels_usage", "settings", "focus_session", emitInitialState = false)
                .debounce(3_000)
                .collect { com.example.tracker.widget.Widgets.refresh(this@TrackerApp) }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as TrackerApp).container
