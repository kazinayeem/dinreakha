package com.example.tracker.focus

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.tracker.AppContainer
import com.example.tracker.R
import com.example.tracker.alarm.Notifications
import com.example.tracker.container
import com.example.tracker.data.focus.FocusMath
import com.example.tracker.data.focus.FocusRun
import com.example.tracker.data.focus.FocusType
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

/**
 * Runs the focus timer without a long-lived service:
 *  - the state lives in the database (survives the app being closed or killed),
 *  - an exact alarm fires at the end,
 *  - an ongoing notification shows the live countdown (system chronometer) with Pause / +5 min / Stop.
 */
class FocusController(private val ctx: Context, private val c: AppContainer) {
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val mutex = Mutex()
    private fun now() = LocalDateTime.now(Schedule.ZONE).withNano(0)

    fun ensureChannels() {
        nm.createNotificationChannel(NotificationChannel(CH_RUNNING, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Silent countdown while a focus session runs"; setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Focus finished", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When a focus session ends"
        })
    }

    suspend fun start(type: FocusType, minutes: Int, dnd: Boolean, title: String = type.label) = mutex.withLock {
        val run = FocusRun(type, minutes.coerceIn(1, 600), now(), dnd = dnd, title = title)
        c.focus.setActive(run)
        if (dnd) enterDnd()
        scheduleEnd(run); show(run)
    }

    suspend fun pause() = mutex.withLock {
        val run = c.focus.current()?.pause(now()) ?: return@withLock
        c.focus.setActive(run); cancelEnd(); show(run)
    }

    suspend fun resume() = mutex.withLock {
        val run = c.focus.current()?.resume(now()) ?: return@withLock
        c.focus.setActive(run); scheduleEnd(run); show(run)
    }

    suspend fun extend(minutes: Int = 5) = mutex.withLock {
        val run = c.focus.current()?.extend(minutes) ?: return@withLock
        c.focus.setActive(run); if (!run.isPaused) scheduleEnd(run); show(run)
    }

    /** Stop now (logs what was done) or called by the end alarm. */
    suspend fun finish(fromAlarm: Boolean) = mutex.withLock {
        val run = c.focus.current() ?: return@withLock
        if (fromAlarm && !run.isDone(now()) ) { scheduleEnd(run); return@withLock } // extended/paused meanwhile
        cancelEnd()
        nm.cancel(ID_RUNNING)
        if (run.dnd) exitDnd()
        val logged = c.focus.finish(now())
        if (logged != null) {
            val (s, ticked) = logged
            notifyDone(s.id, s.type, s.minutes, ticked, completed = run.isDone(now()))
        }
        c.scheduler.refreshStatus()
    }

    /** Re-shows the notification / re-arms the alarm after reboot or app update. */
    suspend fun restore() {
        val run = c.focus.current() ?: return
        if (!run.isPaused && run.isDone(now())) finish(fromAlarm = true) else { if (!run.isPaused) scheduleEnd(run); show(run) }
    }

    // ---------------- alarm ----------------

    private fun endIntent(): PendingIntent = PendingIntent.getBroadcast(ctx, 7001,
        Intent(ctx, FocusReceiver::class.java).setAction(ACTION_END), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun scheduleEnd(run: FocusRun) {
        val at = run.endsAt(now()).atZone(Schedule.ZONE).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endIntent())
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endIntent())
        } catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endIntent()) }
    }

    private fun cancelEnd() = am.cancel(endIntent())

    // ---------------- notifications ----------------

    private fun action(a: String, code: Int) = PendingIntent.getBroadcast(ctx, code,
        Intent(ctx, FocusReceiver::class.java).setAction(a), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @SuppressLint("MissingPermission")
    private fun show(run: FocusRun) {
        if (!Notifications.canPost(ctx)) return
        val n = now()
        val left = run.remaining(n)
        val b = NotificationCompat.Builder(ctx, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_dinrekha)
            .setContentTitle(if (run.isPaused) "${run.title}: paused" else run.title)
            .setContentText(if (run.isPaused) "${FocusMath.formatHm(left.toMinutes().toInt().coerceAtLeast(1))} left"
                else "Focus until ${TimeFormat.format(run.endsAt(n))}")
            .setContentIntent(Notifications.openAppIntent(ctx, "focus", 7002))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(run.plannedMinutes * 60, run.elapsed(n).seconds.toInt(), false)
        if (!run.isPaused) {
            b.setWhen(run.endsAt(n).atZone(Schedule.ZONE).toInstant().toEpochMilli()).setShowWhen(true)
                .setUsesChronometer(true).setChronometerCountDown(true)
            b.addAction(0, "Pause", action(ACTION_PAUSE, 7003))
        } else b.addAction(0, "Resume", action(ACTION_RESUME, 7004))
        b.addAction(0, "+5 min", action(ACTION_EXTEND, 7005))
        b.addAction(0, "Stop", action(ACTION_STOP, 7006))
        NotificationManagerCompat.from(ctx).notify(ID_RUNNING, b.build())
    }

    @SuppressLint("MissingPermission")
    private fun notifyDone(sessionId: Long, type: FocusType, minutes: Int, ticked: Boolean, completed: Boolean) {
        if (!Notifications.canPost(ctx)) return
        val text = buildString {
            append("${FocusMath.formatHm(minutes)} of ${type.label.lowercase()} logged.")
            if (ticked) append(" Ticked on today's checklist.")
        }
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_dinrekha)
            .setContentTitle(if (completed) "Focus session done" else "Focus session stopped")
            .setContentText(text)
            .setContentIntent(Notifications.openAppIntent(ctx, "focus?note=$sessionId", 7007))
            .addAction(0, "Add what I did", Notifications.openAppIntent(ctx, "focus?note=$sessionId", 7008))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_DONE, n)
    }

    // ---------------- do not disturb ----------------

    fun hasDndAccess() = nm.isNotificationPolicyAccessGranted

    private suspend fun enterDnd() {
        if (!hasDndAccess()) return
        c.focus.setSavedDndFilter(nm.currentInterruptionFilter)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private suspend fun exitDnd() {
        if (!hasDndAccess()) return
        val prev = c.focus.savedDndFilter() ?: NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(prev)
        c.focus.setSavedDndFilter(null)
    }

    companion object {
        const val CH_RUNNING = "focus_running"
        const val CH_DONE = "focus_done"
        const val ID_RUNNING = 7100
        const val ID_DONE = 7101
        const val ACTION_END = "com.example.tracker.FOCUS_END"
        const val ACTION_PAUSE = "com.example.tracker.FOCUS_PAUSE"
        const val ACTION_RESUME = "com.example.tracker.FOCUS_RESUME"
        const val ACTION_EXTEND = "com.example.tracker.FOCUS_EXTEND"
        const val ACTION_STOP = "com.example.tracker.FOCUS_STOP"
    }
}

/** End alarm + notification buttons. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val f = context.container.focusController
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    FocusController.ACTION_END -> f.finish(fromAlarm = true)
                    FocusController.ACTION_STOP -> f.finish(fromAlarm = false)
                    FocusController.ACTION_PAUSE -> f.pause()
                    FocusController.ACTION_RESUME -> f.resume()
                    FocusController.ACTION_EXTEND -> f.extend(5)
                }
            } catch (e: Exception) { Log.e("FocusReceiver", "failed", e) } finally { pending.finish() }
        }
    }
}
