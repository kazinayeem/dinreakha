package com.example.tracker.widget

import android.content.Context
import com.example.tracker.container
import com.example.tracker.data.finance.FinanceMath
import com.example.tracker.data.focus.FocusRun
import com.example.tracker.data.repo.LockScope
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.ReminderPlanner
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.TimeFormat
import com.example.tracker.ui.stats.HabitStats
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Everything the home-screen widgets show, read once per update. */
data class WidgetSnapshot(
    val now: LocalDateTime,
    val planDate: LocalDate,
    val nextTitle: String?,
    val nextAt: LocalDateTime?,
    val checklist: List<Item>,
    val wake: LocalDateTime,
    val bed: LocalDateTime,
    val spentToday: Long,
    val spentMonth: Long,
    val balance: Long?,          // null while the Wallet lock is on
    val focus: FocusRun?,
    val streak: Int,
    val dayNumber: Int,
    val planDays: Int,
    val lastNightMin: Long?,
    val mood: Int?,
    val reels: Int,
    val topApps: List<Pair<String, Long>>, // label to minutes
) {
    data class Item(val key: String, val label: String, val done: Boolean)

    val done get() = checklist.count { it.done }
    val total get() = checklist.size
    val dayFraction: Float get() =
        (Duration.between(wake, now).toMinutes().toFloat() / Duration.between(wake, bed).toMinutes().coerceAtLeast(1)).coerceIn(0f, 1f)

    fun nextIn(): String? = nextAt?.let {
        val m = Duration.between(now, it).toMinutes().coerceAtLeast(0)
        if (m >= 60) "in ${m / 60}h ${m % 60}m" else "in ${m}m"
    }

    fun time(t: LocalDateTime) = TimeFormat.format(t)

    companion object {
        suspend fun load(ctx: Context): WidgetSnapshot {
            val c = ctx.container
            val now = LocalDateTime.now(Schedule.ZONE)
            val today = now.toLocalDate()
            val planDate = if (now.isBefore(PlanCalendar.wakeDateTime(today)) && now.hour < 5) today.minusDays(1) else today

            val next = runCatching { ReminderPlanner.nextOverall(now, c.scheduler.reminderConfig()) }.getOrNull()
            val day = c.habits.observeDay(planDate).first()
            val list = PlanCalendar.checklist(planDate).filter { it.expected }
                .map { Item(it.key, it.label, it.key in day.doneKeys) }

            val settings = c.settings.current()
            val month = YearMonth.from(today)
            val monthTxns = c.db.txnDao().between(month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay())
            val balance = if (LockScope.WALLET in settings.lockScopes) null else
                FinanceMath.total(c.db.accountDao().all(), c.db.txnDao().all())

            val plan = c.habits.observePlan().first()
            val pm = ctx.packageManager
            val usage = c.blocker.observeAppUsage(today).first()
                .filter { it.foregroundMs >= 60_000 && it.packageName != ctx.packageName }
                .sortedByDescending { it.foregroundMs }.take(3)
                .map { u ->
                    val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(u.packageName, 0)).toString() }.getOrDefault(u.packageName)
                    label to u.foregroundMs / 60_000
                }

            return WidgetSnapshot(
                now = now,
                planDate = planDate,
                nextTitle = next?.title,
                nextAt = next?.at,
                checklist = list,
                wake = PlanCalendar.wakeDateTime(planDate),
                bed = PlanCalendar.bedDateTime(planDate),
                spentToday = c.finance.spentToday(),
                spentMonth = FinanceMath.monthTotals(month, monthTxns).expense,
                balance = balance,
                focus = c.focus.active.first(),
                streak = HabitStats.currentStreak(plan, today),
                dayNumber = PlanCalendar.dayNumber(today),
                planDays = PlanCalendar.planDays,
                lastNightMin = c.db.sleepDao().get(today.minusDays(1))?.minutes,
                mood = c.db.dayLogDao().get(today)?.mood,
                reels = c.blocker.getTotalReelsCount(),
                topApps = usage,
            )
        }
    }
}
