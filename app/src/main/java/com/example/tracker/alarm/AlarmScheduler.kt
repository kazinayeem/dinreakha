package com.example.tracker.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.tracker.AppContainer
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Reminder
import com.example.tracker.data.schedule.ReminderCategory
import com.example.tracker.data.schedule.ReminderConfig
import com.example.tracker.data.schedule.ReminderPlanner
import com.example.tracker.data.schedule.Schedule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Schedules ONLY the next occurrence of every reminder slot. When one fires,
 * ReminderReceiver calls [rescheduleAll] again, which picks the following occurrence.
 * Also called on boot, time/zone change, app open and every settings / schedule change.
 */
class AlarmScheduler(private val ctx: Context, private val c: AppContainer) {

    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /** Deleted routines whose alarm must be cancelled on the next reschedule. */
    private val routineSlotsToCancel = mutableSetOf<String>()
    fun forgetRoutine(id: Long) { routineSlotsToCancel += ReminderPlanner.routineSlot(id) }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    /** Current plan + settings + recurring bills, loaded fresh from the database. */
    suspend fun reminderConfig(): ReminderConfig {
        c.schedule.load()
        return c.settings.current().toReminderConfig().copy(
            recurring = c.finance.recurringDue(),
            extra = c.calendar.upcomingReminders(LocalDateTime.now(Schedule.ZONE)),
        )
    }

    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now(Schedule.ZONE)) = mutex.withLock {
        val settings = c.settings.current()
        Notifications.syncChannels(ctx, settings)
        val config = reminderConfig()
        val next = ReminderPlanner.nextPerSlot(now, config)

        val recurring = c.finance.allRecurring()
        // Dynamic slots (recurring bills, routines, calendar events) scheduled last time are remembered,
        // so alarms of deleted items get cancelled even after the app restarts.
        val previousDynamic = c.settings.dynamicSlots()
        val staticSlots = ReminderPlanner.ALL_SLOTS - ReminderPlanner.SLOT_SNOOZE
        val dynamicNow = next.keys - staticSlots.toSet()
        val knownSlots = staticSlots + recurring.map { ReminderPlanner.recurringSlot(it.id) } +
            PlanCalendar.config.routines.map { ReminderPlanner.routineSlot(it.id) } + routineSlotsToCancel + previousDynamic + dynamicNow
        knownSlots.distinct().forEach { slot -> next[slot]?.let(::schedule) ?: cancel(slot) }
        c.settings.setDynamicSlots(dynamicNow)
        if (!config.masterEnabled) cancel(ReminderPlanner.SLOT_SNOOZE)
        Log.i(TAG, "Scheduled ${next.size} reminders; next: ${next.values.minByOrNull { it.at }}")
        refreshStatus(now, config)
    }

    /** Updates the status-bar countdown without touching alarms (e.g. after ticking a habit). */
    suspend fun refreshStatus(now: LocalDateTime = LocalDateTime.now(Schedule.ZONE), config: ReminderConfig? = null) {
        val settings = c.settings.current()
        val cfg = config ?: reminderConfig()
        val today: LocalDate = now.toLocalDate()
        // After midnight but before wake, "today" for the checklist is still the evening's plan date.
        val planDate = if (now.isBefore(PlanCalendar.wakeDateTime(today)) && now.toLocalTime().hour < 5) today.minusDays(1) else today
        val (done, total) = c.habits.doneCount(planDate)
        Notifications.updateStatus(ctx, settings.statusNotification && settings.masterEnabled,
            ReminderPlanner.nextOverall(now, cfg), done, total, c.finance.spentToday())
        com.example.tracker.widget.Widgets.refresh(ctx)
    }

    private fun schedule(r: Reminder) {
        val trigger = r.at.atZone(Schedule.ZONE).toInstant().toEpochMilli()
        val pi = pendingIntent(r)
        try {
            if (r.category == ReminderCategory.WAKE) {
                // Alarm-clock alarms are exempt from Doze and show the alarm icon in the status bar.
                am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, Notifications.openAppIntent(ctx, "today", 9001)), pi)
            } else if (canScheduleExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) // inexact fallback
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not permitted, falling back", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun cancel(slot: String) {
        val pi = PendingIntent.getBroadcast(ctx, Notifications.slotIndex(slot), Intent(ctx, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        pi?.let { am.cancel(it); it.cancel() }
    }

    private fun pendingIntent(r: Reminder): PendingIntent =
        PendingIntent.getBroadcast(ctx, Notifications.slotIndex(r.slot), ReminderReceiver.intent(ctx, r),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Wake-up snooze: a one-off alarm-clock alarm [minutes] from now. */
    fun scheduleSnooze(date: LocalDate, minutes: Int) {
        val at = LocalDateTime.now(Schedule.ZONE).plusMinutes(minutes.toLong()).withNano(0)
        schedule(Reminder(ReminderPlanner.SLOT_SNOOZE, ReminderCategory.WAKE, at, "Wake up — snoozed",
            "Snooze over. Up you get — light, water, no phone.", date, com.example.tracker.data.schedule.Habits.WOKE_ON_TIME))
    }

    /** For the Settings "Test" button: fires a real reminder through the full pipeline in 5 s. */
    fun scheduleTest(category: ReminderCategory) {
        val at = LocalDateTime.now(Schedule.ZONE).plusSeconds(5)
        val r = Reminder("test_${category.key}", category, at, "Test: ${category.label}",
            "If you see this, ${category.label.lowercase()} reminders work.", at.toLocalDate())
        val pi = PendingIntent.getBroadcast(ctx, 8000 + category.ordinal, ReminderReceiver.intent(ctx, r, test = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val trigger = at.atZone(Schedule.ZONE).toInstant().toEpochMilli()
        if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
    }

    companion object { private const val TAG = "AlarmScheduler" }
}
