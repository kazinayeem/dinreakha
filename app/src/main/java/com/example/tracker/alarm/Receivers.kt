package com.example.tracker.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.tracker.container
import com.example.tracker.data.schedule.Reminder
import com.example.tracker.data.schedule.ReminderCategory
import com.example.tracker.data.schedule.ReminderPlanner
import com.example.tracker.data.schedule.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Runs [block] off the main thread while keeping the receiver alive (goAsync, ≤10 s). */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try { block() } catch (e: Exception) { Log.e("Receiver", "failed", e) } finally { pending.finish() }
    }
}

/** An alarm fired: show the notification, log it, then schedule the next occurrence of every slot. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val payload = ReminderPayload(
            slot = intent.getStringExtra(EXTRA_SLOT) ?: return,
            category = runCatching { ReminderCategory.valueOf(intent.getStringExtra(EXTRA_CATEGORY)!!) }.getOrNull() ?: return,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: LocalDate.now(Schedule.ZONE),
            habitKey = intent.getStringExtra(EXTRA_HABIT),
        )
        val isTest = intent.getBooleanExtra(EXTRA_TEST, false)
        val c = context.container
        async {
            val s = c.settings.current()
            Notifications.syncChannels(context, s)
            val snoozesLeft = if (payload.category == ReminderCategory.WAKE)
                (s.snoozeMax - c.settings.snoozeCount(payload.date)).coerceAtLeast(0) else 0
            Notifications.showReminder(context, payload, s, snoozesLeft)
            c.schedule.logFired(payload.slot, payload.category.key,
                (if (isTest) "[test] " else "") + payload.title, LocalDateTime.now(Schedule.ZONE).withNano(0))
            if (!isTest) c.scheduler.rescheduleAll()
        }
    }

    companion object {
        const val ACTION = "com.example.tracker.REMINDER"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_DATE = "date"
        const val EXTRA_HABIT = "habit"
        const val EXTRA_TEST = "test"

        fun intent(ctx: Context, r: Reminder, test: Boolean = false): Intent =
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION)
                .putExtra(EXTRA_SLOT, r.slot)
                .putExtra(EXTRA_CATEGORY, r.category.name)
                .putExtra(EXTRA_TITLE, r.title)
                .putExtra(EXTRA_TEXT, r.text)
                .putExtra(EXTRA_DATE, r.date.toString())
                .putExtra(EXTRA_HABIT, r.habitKey)
                .putExtra(EXTRA_TEST, test)
    }
}

/** Notification actions ("Done ✓", "I'm up ✓", "Snooze", "Add ✓") — update Room without opening the app. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val c = context.container
        async {
            when (intent.action) {
                ACTION_DONE -> {
                    val date = LocalDate.parse(intent.getStringExtra(EXTRA_DATE))
                    val key = intent.getStringExtra(EXTRA_HABIT) ?: return@async
                    c.habits.setHabit(date, key, true)
                    // "I'm up" also cancels a pending snooze.
                    if (notifId == Notifications.notificationId(ReminderPlanner.SLOT_WAKE)) c.scheduler.cancel(ReminderPlanner.SLOT_SNOOZE)
                }
                ACTION_SNOOZE -> {
                    val date = LocalDate.parse(intent.getStringExtra(EXTRA_DATE))
                    val s = c.settings.current()
                    val used = c.settings.snoozeCount(date)
                    if (used < s.snoozeMax) {
                        c.settings.setSnoozeCount(date, used + 1)
                        c.scheduler.scheduleSnooze(date, s.snoozeMin)
                    }
                }
                ACTION_RECURRING -> {
                    val alerts = c.finance.confirmRecurring(intent.getLongExtra(EXTRA_RECURRING, -1))
                    Notifications.showBudgetAlerts(context, alerts)
                    c.scheduler.rescheduleAll()
                }
            }
            Notifications.cancel(context, notifId)
            c.scheduler.refreshStatus()
        }
    }

    companion object {
        const val ACTION_DONE = "com.example.tracker.DONE"
        const val ACTION_SNOOZE = "com.example.tracker.SNOOZE"
        const val ACTION_RECURRING = "com.example.tracker.RECURRING_ADD"
        private const val EXTRA_NOTIF_ID = "notif_id"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_HABIT = "habit"
        private const val EXTRA_RECURRING = "recurring_id"

        private fun pi(ctx: Context, code: Int, i: Intent) =
            PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun donePendingIntent(ctx: Context, notifId: Int, date: LocalDate, habitKey: String): PendingIntent =
            pi(ctx, 20_000 + notifId, Intent(ctx, DoneReceiver::class.java).setAction(ACTION_DONE)
                .putExtra(EXTRA_NOTIF_ID, notifId).putExtra(EXTRA_DATE, date.toString()).putExtra(EXTRA_HABIT, habitKey))

        fun snoozePendingIntent(ctx: Context, notifId: Int, date: LocalDate): PendingIntent =
            pi(ctx, 40_000 + notifId, Intent(ctx, DoneReceiver::class.java).setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_NOTIF_ID, notifId).putExtra(EXTRA_DATE, date.toString()))

        fun recurringPendingIntent(ctx: Context, notifId: Int, recurringId: Long): PendingIntent =
            pi(ctx, 30_000 + notifId, Intent(ctx, DoneReceiver::class.java).setAction(ACTION_RECURRING)
                .putExtra(EXTRA_NOTIF_ID, notifId).putExtra(EXTRA_RECURRING, recurringId))
    }
}

/** Boot, clock change, time-zone change, app update, exact-alarm permission change → reschedule everything. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("RescheduleReceiver", "Rescheduling after ${intent.action}")
        async {
            context.container.scheduler.rescheduleAll()
            context.container.focusController.restore() // re-arm a running focus timer
        }
    }
}
