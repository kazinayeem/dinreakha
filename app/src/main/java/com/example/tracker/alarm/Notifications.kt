package com.example.tracker.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.widget.RemoteViews
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.tracker.MainActivity
import com.example.tracker.R
import com.example.tracker.data.finance.Money
import com.example.tracker.data.repo.AppSettings
import com.example.tracker.data.repo.BudgetAlert
import com.example.tracker.data.schedule.ChannelGroup
import com.example.tracker.data.schedule.Habits
import com.example.tracker.data.schedule.Reminder
import com.example.tracker.data.schedule.ReminderCategory
import com.example.tracker.data.schedule.ReminderPlanner
import com.example.tracker.data.schedule.ReminderPref
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.SoundMode
import com.example.tracker.data.schedule.TimeFormat
import java.time.LocalDate

object Notifications {
    const val CH_STATUS = "status"
    private const val CH_BUDGET = "c_budget"
    private const val CHANNEL_PREFIX = "c_"
    private val LEGACY_CHANNELS = listOf("wake_up", "reminders", "prayers")

    const val STATUS_ID = 1
    private const val BUDGET_ID_BASE = 5000
    const val EXTRA_ROUTE = "route"

    // ---------------- channels ----------------

    /**
     * Android can't change a channel's sound after creation, so each reminder type gets a channel whose id
     * encodes its sound settings. When you change the sound/ringtone, a new channel is created and the old one deleted.
     */
    fun channelId(c: ReminderCategory, p: ReminderPref): String =
        "$CHANNEL_PREFIX${c.key}_${p.sound.name.lowercase()}_${Integer.toHexString((p.ringtone ?: "default").hashCode())}"

    fun syncChannels(ctx: Context, s: AppSettings?) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannelGroups(ChannelGroup.entries.map { NotificationChannelGroup(it.name, it.label) })
        val wanted = ReminderCategory.entries.map { c -> buildChannel(c, s?.pref(c) ?: c.defaultPref) }
        val status = NotificationChannel(CH_STATUS, "Status bar countdown", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Silent, always-on: next reminder and time remaining"; setShowBadge(false)
        }
        val budget = NotificationChannel(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            group = ChannelGroup.REMINDERS.name
        }
        nm.createNotificationChannels(wanted + status + budget)
        // Channels owned by other modules (App Blocker warnings) start with the same prefix: never delete them.
        val keep = (wanted.map { it.id } + CH_STATUS + CH_BUDGET + com.example.tracker.data.repo.BlockerRepository.CH_LIMIT_WARN).toSet()
        nm.notificationChannels.filter { (it.id.startsWith(CHANNEL_PREFIX) && it.id !in keep) || it.id in LEGACY_CHANNELS }
            .forEach { nm.deleteNotificationChannel(it.id) }
    }

    private fun buildChannel(c: ReminderCategory, p: ReminderPref): NotificationChannel {
        val importance = if (p.sound == SoundMode.SILENT) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
        return NotificationChannel(channelId(c, p), c.label, importance).apply {
            group = c.group.name
            description = "${c.label} — ${p.sound.label}"
            val isAlarm = c == ReminderCategory.WAKE
            when (p.sound) {
                SoundMode.SOUND -> {
                    val uri = p.ringtone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(
                        if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
                    setSound(uri, AudioAttributes.Builder()
                        .setUsage(if (isAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    enableVibration(true)
                }
                SoundMode.VIBRATE -> { setSound(null, null); enableVibration(true) }
                SoundMode.SILENT -> { setSound(null, null); enableVibration(false) }
            }
            if (isAlarm) {
                vibrationPattern = longArrayOf(0, 800, 400, 800)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        }
    }

    fun canPost(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun openAppIntent(ctx: Context, route: String? = null, requestCode: Int = 0): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            route?.let { putExtra(EXTRA_ROUTE, it) }
        }
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun notificationId(slot: String): Int = 100 + slotIndex(slot)

    /** Stable index per slot (static slots first, then recurring by id). The snooze shares the wake notification. */
    fun slotIndex(slot: String): Int =
        ReminderPlanner.ALL_SLOTS.indexOf(slot).takeIf { it >= 0 }
            ?: ReminderPlanner.routineIdOf(slot)?.let { 100_000 + it.toInt() }
            ?: slot.takeIf { it.startsWith("event_") }?.let { 1_000_000 + Math.floorMod(it.hashCode(), 50_000_000) }
            ?: (1000 + (ReminderPlanner.recurringIdOf(slot) ?: 0L).toInt())

    // ---------------- reminders ----------------

    @SuppressLint("MissingPermission")
    fun showReminder(ctx: Context, r: ReminderPayload, s: AppSettings, snoozesLeft: Int) {
        if (!canPost(ctx)) return
        val isWake = r.category == ReminderCategory.WAKE
        val id = if (isWake) notificationId(ReminderPlanner.SLOT_WAKE) else notificationId(r.slot)
        val route = when (r.category) {
            ReminderCategory.EXPENSE_LOG, ReminderCategory.RECURRING -> "wallet/add"
            ReminderCategory.WEEKLY_REVIEW -> if (r.slot == ReminderPlanner.SLOT_WEEKLY_CARD) "reports/weekly" else "review"
            ReminderCategory.CALENDAR -> "calendar"
            else -> "today"
        }
        val b = NotificationCompat.Builder(ctx, channelId(r.category, s.pref(r.category)))
            .setSmallIcon(R.drawable.ic_stat_dinrekha)
            .setContentTitle(com.example.tracker.ui.i18n.tr(r.title))
            .setContentText(com.example.tracker.ui.i18n.tr(r.text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(com.example.tracker.ui.i18n.tr(r.text)))
            .setContentIntent(openAppIntent(ctx, route, id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isWake) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)

        if (r.habitKey != null) {
            b.addAction(0, if (isWake) "I'm up ✓" else "Done ✓", DoneReceiver.donePendingIntent(ctx, id, r.date, r.habitKey))
        }
        if (isWake && snoozesLeft > 0) {
            b.addAction(0, "Snooze ${s.snoozeMin} min ($snoozesLeft left)", DoneReceiver.snoozePendingIntent(ctx, id, r.date))
        }
        ReminderPlanner.recurringIdOf(r.slot)?.let { rid -> b.addAction(0, "Add ✓", DoneReceiver.recurringPendingIntent(ctx, id, rid)) }
        if (r.category == ReminderCategory.EXPENSE_LOG) b.addAction(0, "Add expense", openAppIntent(ctx, "wallet/add", id + 50))
        val isClassSlot = r.habitKey?.startsWith("class_") == true || r.slot.startsWith("class_") || r.habitKey == Habits.COURSE_STUDY
        if (isClassSlot) {
            b.addAction(0, "📷 Capture note", openAppIntent(ctx, "notes/capture", id + 70))
        }
        if (isWake) b.setOngoing(true).setAutoCancel(false).setFullScreenIntent(openAppIntent(ctx, "today", id + 60), true)

        val n = b.build()
        if (isWake && s.pref(ReminderCategory.WAKE).sound != SoundMode.SILENT) n.flags = n.flags or android.app.Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    @SuppressLint("MissingPermission")
    fun showBudgetAlerts(ctx: Context, alerts: List<BudgetAlert>) {
        if (!canPost(ctx)) return
        alerts.forEach { a ->
            val text = if (a.percent >= 100) "You've reached your ${a.category.name} budget: ${Money.format(a.spent)} of ${Money.format(a.limit)}."
            else "${a.percent}% of your ${a.category.name} budget used: ${Money.format(a.spent)} of ${Money.format(a.limit)}."
            val n = NotificationCompat.Builder(ctx, CH_BUDGET)
                .setSmallIcon(R.drawable.ic_stat_dinrekha)
                .setContentTitle("${a.category.icon} Budget ${a.percent}%")
                .setContentText(text)
                .setContentIntent(openAppIntent(ctx, "wallet/budgets", BUDGET_ID_BASE))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(BUDGET_ID_BASE + a.category.id.toInt() * 2 + if (a.percent >= 100) 1 else 0, n)
        }
    }

    /**
     * Silent, ongoing status-bar notification: the next reminder with a live countdown
     * (system chronometer, so no polling) and today's checklist progress.
     */
    @SuppressLint("MissingPermission")
    fun updateStatus(ctx: Context, enabled: Boolean, next: Reminder?, done: Int, total: Int, spentToday: Long) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!enabled || !canPost(ctx)) { nm.cancel(STATUS_ID); return }
        val whenMillis = next?.at?.atZone(Schedule.ZONE)?.toInstant()?.toEpochMilli()
        val pct = if (total > 0) done * 100 / total else 0

        // Custom layout: big live countdown in the accent colour, progress ring on the right,
        // and in the expanded view a bar + habits/money rows. Colours follow the light/dark shade.
        fun RemoteViews.fill(): RemoteViews = apply {
            setTextViewText(R.id.n_title, com.example.tracker.ui.i18n.tr(next?.title ?: "All clear for today"))
            if (whenMillis != null) {
                val base = android.os.SystemClock.elapsedRealtime() + (whenMillis - System.currentTimeMillis())
                setViewVisibility(R.id.n_timer, android.view.View.VISIBLE)
                setChronometer(R.id.n_timer, base, null, true)
                setChronometerCountDown(R.id.n_timer, true)
                setTextViewText(R.id.n_at, com.example.tracker.ui.i18n.tr("at ${TimeFormat.format(next.at)}"))
            } else {
                setViewVisibility(R.id.n_timer, android.view.View.GONE)
                setTextViewText(R.id.n_at, com.example.tracker.ui.i18n.tr("Nothing else scheduled"))
            }
            setProgressBar(R.id.n_ring, 100, pct, false)
            setTextViewText(R.id.n_count, com.example.tracker.ui.i18n.tr("$done/$total"))
        }
        val small = RemoteViews(ctx.packageName, R.layout.notif_status_small).fill()
        val big = RemoteViews(ctx.packageName, R.layout.notif_status_big).fill().apply {
            setProgressBar(R.id.n_bar, 100, pct, false)
            setTextViewText(R.id.n_habits, com.example.tracker.ui.i18n.tr("$done of $total done"))
            setTextViewText(R.id.n_spent, com.example.tracker.ui.i18n.tr("${Money.format(spentToday)} spent"))
        }

        val b = NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_dinrekha)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            // Plain-text fallback for the lock screen, watches and accessibility
            .setContentTitle(next?.let { "${it.title}, ${TimeFormat.format(it.at)}" } ?: "All clear for today")
            .setContentText("$done of $total done, ${Money.format(spentToday)} spent")
            .setContentIntent(openAppIntent(ctx, "today", STATUS_ID))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        nm.notify(STATUS_ID, b.build())
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}

/** Everything the receiver needs to show a reminder, carried as Intent extras. */
data class ReminderPayload(
    val slot: String,
    val category: ReminderCategory,
    val title: String,
    val text: String,
    val date: LocalDate,
    val habitKey: String?,
)
