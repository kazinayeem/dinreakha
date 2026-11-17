package com.example.tracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.tracker.R
import com.example.tracker.container
import com.example.tracker.data.finance.Money
import com.example.tracker.data.focus.FocusType
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.sleep.SleepMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

// ======================================================================= 1. Next up
class NextUpWidget : DinrekhaWidget() {
    @Composable override fun Content(ctx: Context, d: WidgetSnapshot) {
        Card(GlanceModifier.clickable(openRoute(ctx, "today"))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_w_clock)
                Spacer(GlanceModifier.width(6.dp))
                Label("Next")
            }
            Spacer(GlanceModifier.defaultWeight())
            Title(d.nextTitle ?: "All clear")
            if (d.nextAt != null) {
                Big(d.nextIn() ?: "", 24.sp, GlanceTheme.colors.primary)
                Label("at ${d.time(d.nextAt)}")
            } else Label("Nothing else today")
        }
    }
}

// ======================================================================= 2. Checklist (tap to tick)
class ChecklistWidget : DinrekhaWidget() {
    @Composable override fun Content(ctx: Context, d: WidgetSnapshot) {
        Card(pad = 12.dp) {
            Row(GlanceModifier.fillMaxWidth().clickable(openRoute(ctx, "today")), verticalAlignment = Alignment.CenterVertically) {
                Title("Today")
                Spacer(GlanceModifier.defaultWeight())
                Big("${d.done}/${d.total}", 18.sp, GlanceTheme.colors.primary)
            }
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = if (d.total == 0) 0f else d.done.toFloat() / d.total,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = GlanceTheme.colors.primary, backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
            Spacer(GlanceModifier.height(6.dp))
            LazyColumn {
                items(d.checklist, itemId = { it.key.hashCode().toLong() }) { item ->
                    Row(
                        GlanceModifier.fillMaxWidth().padding(vertical = 7.dp)
                            .clickable(actionRunCallback<ToggleHabit>(actionParametersOf(KEY to item.key, DATE to d.planDate.toString()))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (item.done) R.drawable.ic_w_check_circle else R.drawable.ic_w_circle,
                            if (item.done) GlanceTheme.colors.primary else GlanceTheme.colors.outline, 22.dp,
                            if (item.done) "Done" else "Not done")
                        Spacer(GlanceModifier.width(10.dp))
                        Text(com.example.tracker.ui.i18n.tr(item.label), maxLines = 1, style = TextStyle(fontSize = 14.sp,
                            color = if (item.done) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface))
                    }
                }
            }
        }
    }
}

// ======================================================================= 3. Dayline
class DaylineWidget : DinrekhaWidget() {
    @Composable override fun Content(ctx: Context, d: WidgetSnapshot) {
        Card(GlanceModifier.clickable(openRoute(ctx, "today"))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_w_sun, GlanceTheme.colors.tertiary)
                Spacer(GlanceModifier.width(8.dp))
                Big("${(d.dayFraction * 100).toInt()}%", 20.sp)
                Spacer(GlanceModifier.width(6.dp))
                Label("of your day")
                Spacer(GlanceModifier.defaultWeight())
                Label("Day ${d.dayNumber.coerceIn(1, d.planDays)}")
            }
            Spacer(GlanceModifier.defaultWeight())
            LinearProgressIndicator(
                progress = d.dayFraction,
                modifier = GlanceModifier.fillMaxWidth().height(10.dp).cornerRadius(5.dp),
                color = GlanceTheme.colors.primary, backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Label(d.time(d.wake))
                Spacer(GlanceModifier.defaultWeight())
                Label(d.time(d.bed))
            }
        }
    }
}

// ======================================================================= 4. Wallet
class WalletWidget : DinrekhaWidget() {
    @Composable override fun Content(ctx: Context, d: WidgetSnapshot) {
        Card(GlanceModifier.clickable(openRoute(ctx, "wallet"))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_w_wallet)
                Spacer(GlanceModifier.width(6.dp))
                Label("Spent today")
            }
            Big(Money.format(d.spentToday), 24.sp)
            Label(if (d.balance != null) "Balance ${Money.format(d.balance)}" else "This month ${Money.format(d.spentMonth)}")
            Spacer(GlanceModifier.defaultWeight())
            Row(
                GlanceModifier.fillMaxWidth().height(40.dp).cornerRadius(20.dp).background(GlanceTheme.colors.primary)
                    .clickable(openRoute(ctx, "wallet/add")),
                verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(R.drawable.ic_w_add, GlanceTheme.colors.onPrimary, 18.dp)
                Spacer(GlanceModifier.width(4.dp))
                Text(com.example.tracker.ui.i18n.tr("Expense"), style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 14.sp))
            }
        }
    }
}

// ======================================================================= 5. Focus
class FocusWidget : DinrekhaWidget() {
    @Composable override fun Content(ctx: Context, d: WidgetSnapshot) {
        val run = d.focus
        Card(GlanceModifier.clickable(openRoute(ctx, "focus"))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_w_timer)
                Spacer(GlanceModifier.width(6.dp))
                Label(if (run == null) "Focus" else if (run.isPaused) "Paused" else "Focusing")
            }
            Spacer(GlanceModifier.defaultWeight())
            if (run == null) {
                Row(GlanceModifier.fillMaxWidth()) {
                    StartChip(ctx, 25); Spacer(GlanceModifier.width(8.dp)); StartChip(ctx, 45)
                }
            } else {
                Title(run.title)
                Label("ends ${d.time(run.endsAt(d.now))}")
                Spacer(GlanceModifier.height(8.dp))
                Row {
                    RoundButton(if (run.isPaused) R.drawable.ic_w_play else R.drawable.ic_w_pause,
                        if (run.isPaused) "Resume" else "Pause", actionRunCallback<FocusControl>(actionParametersOf(OP to if (run.isPaused) "resume" else "pause")), filled = true)
                    Spacer(GlanceModifier.width(8.dp))
                    RoundButton(R.drawable.ic_w_stop, "Stop", actionRunCallback<FocusControl>(actionParametersOf(OP to "stop")))
                }
            }
        }
    }

    @Composable private fun StartChip(ctx: Context, min: Int) {
        Box(
            GlanceModifier.height(44.dp).width(64.dp).cornerRadius(22.dp).background(GlanceTheme.colors.primaryContainer)
                .clickable(actionRunCallback<FocusControl>(actionParametersOf(OP to "start", MIN to min))),
            contentAlignment = Alignment.Center,
        ) { Text("${min}m", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 15.sp)) }
    }
}

// ======================================================================= 6. Streak
class StreakWidget : DinrekhaWidget() {
    @Composable override fun Content(ctx: Context, d: WidgetSnapshot) {
        Card(GlanceModifier.clickable(openRoute(ctx, "stats"))) {
            Icon(R.drawable.ic_w_flame, GlanceTheme.colors.tertiary, 28.dp)
            Spacer(GlanceModifier.defaultWeight())
            Big("${d.streak}", 34.sp)
            Label(if (d.streak == 1) "day streak" else "days streak")
            Label(when {
                d.dayNumber < 1 -> "Plan starts soon"
                d.dayNumber > d.planDays -> "Plan done"
                else -> "Day ${d.dayNumber} of ${d.planDays}"
            }, GlanceTheme.colors.primary)
        }
    }
}

// ======================================================================= 7. Sleep & mood
private val FACES = listOf("😞", "🙁", "😐", "🙂", "😄")

class SleepMoodWidget : DinrekhaWidget() {
    @Composable override fun Content(ctx: Context, d: WidgetSnapshot) {
        Card {
            Row(GlanceModifier.fillMaxWidth().clickable(openRoute(ctx, if (d.lastNightMin == null) "sleep?log=true" else "sleep?log=false")),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_w_moon, GlanceTheme.colors.secondary)
                Spacer(GlanceModifier.width(8.dp))
                if (d.lastNightMin != null) { Big(SleepMath.formatDuration(d.lastNightMin), 20.sp); Spacer(GlanceModifier.width(6.dp)); Label("slept") }
                else Title("Log last night")
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FACES.forEachIndexed { i, face ->
                    val v = i + 1
                    Box(
                        GlanceModifier.defaultWeight().height(44.dp).cornerRadius(22.dp)
                            .background(if (d.mood == v) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.widgetBackground)
                            .clickable(actionRunCallback<SetMood>(actionParametersOf(MOOD to v))),
                        contentAlignment = Alignment.Center,
                    ) { Text(face, style = TextStyle(fontSize = 22.sp)) }
                }
            }
        }
    }
}

// ======================================================================= 8. Screen time & reels
class ScreenTimeWidget : DinrekhaWidget() {
    @Composable override fun Content(ctx: Context, d: WidgetSnapshot) {
        Card(GlanceModifier.clickable(openRoute(ctx, "blocker"))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_w_reels, GlanceTheme.colors.error)
                Spacer(GlanceModifier.width(6.dp))
                Big("${d.reels}", 22.sp)
                Spacer(GlanceModifier.width(4.dp))
                Label("reels")
            }
            Spacer(GlanceModifier.height(8.dp))
            if (d.topApps.isEmpty()) Label("No app time yet")
            d.topApps.forEach { (name, min) ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(com.example.tracker.ui.i18n.tr(name), GlanceModifier.defaultWeight(), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
                    Label(if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m", GlanceTheme.colors.primary)
                }
            }
        }
    }
}

// ======================================================================= 9. Quick actions
class QuickActionsWidget : DinrekhaWidget() {
    @Composable override fun Content(ctx: Context, d: WidgetSnapshot) {
        Card(pad = 10.dp) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Action(ctx, R.drawable.ic_w_add, "Expense", "wallet/add")
                Action(ctx, R.drawable.ic_w_timer, "Focus", "focus")
                Action(ctx, R.drawable.ic_w_moon, "Sleep", "sleep?log=true")
                Action(ctx, R.drawable.ic_w_camera, "Note", "notes/capture")
            }
        }
    }

    @Composable private fun androidx.glance.layout.RowScope.Action(ctx: Context, icon: Int, label: String, route: String) {
        Column(GlanceModifier.defaultWeight().clickable(openRoute(ctx, route)), horizontalAlignment = Alignment.CenterHorizontally) {
            RoundButton(icon, label, openRoute(ctx, route), 46.dp)
            Spacer(GlanceModifier.height(4.dp))
            Label(label, GlanceTheme.colors.onSurface, 12.sp)
        }
    }
}

// ======================================================================= actions

private val KEY = ActionParameters.Key<String>("key")
private val DATE = ActionParameters.Key<String>("date")
private val MOOD = ActionParameters.Key<Int>("mood")
private val OP = ActionParameters.Key<String>("op")
private val MIN = ActionParameters.Key<Int>("min")

class ToggleHabit : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = context.container
        val key = parameters[KEY] ?: return
        val date = parameters[DATE]?.let(LocalDate::parse) ?: LocalDate.now(Schedule.ZONE)
        val done = key in c.habits.observeDay(date).first().doneKeys
        c.habits.setHabit(date, key, !done)
        c.scheduler.refreshStatus() // also refreshes every widget
    }
}

class SetMood : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val v = parameters[MOOD] ?: return
        val c = context.container
        val today = LocalDate.now(Schedule.ZONE)
        c.habits.setMood(today, if (c.db.dayLogDao().get(today)?.mood == v) null else v)
        Widgets.refresh(context)
    }
}

class FocusControl : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val fc = context.container.focusController
        when (parameters[OP]) {
            "start" -> fc.start(FocusType.STUDY, parameters[MIN] ?: 25, dnd = false)
            "pause" -> fc.pause()
            "resume" -> fc.resume()
            "stop" -> fc.finish(fromAlarm = false)
        }
        Widgets.refresh(context)
    }
}

// ======================================================================= receivers + refresh

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NextUpWidget() }
class ChecklistWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ChecklistWidget() }
class DaylineWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DaylineWidget() }
class WalletWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WalletWidget() }
class FocusWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = FocusWidget() }
class StreakWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StreakWidget() }
class SleepMoodWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SleepMoodWidget() }
class ScreenTimeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ScreenTimeWidget() }
class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget() }

object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val all: List<GlanceAppWidget> by lazy {
        listOf(NextUpWidget(), ChecklistWidget(), DaylineWidget(), WalletWidget(), FocusWidget(),
            StreakWidget(), SleepMoodWidget(), ScreenTimeWidget())
        // Quick actions has no data, so it never needs a refresh.
    }

    /** Redraw every placed widget with fresh data. Cheap when none are placed. */
    suspend fun refresh(ctx: Context) {
        all.forEach { runCatching { it.updateAll(ctx.applicationContext) } }
    }

    fun refreshAsync(ctx: Context) { scope.launch { refresh(ctx) } }
}
