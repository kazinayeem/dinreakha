package com.example.tracker.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.tracker.data.schedule.Habits
import com.example.tracker.ui.components.habitIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.schedule.Schedule.Repeat
import com.example.tracker.data.schedule.Schedule.Routine
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.SectionCard
import com.example.tracker.ui.components.SwitchRow
import com.example.tracker.ui.components.TimePickerDialog
import com.example.tracker.ui.components.ValueRow
import com.example.tracker.ui.components.short
import com.example.tracker.ui.components.weekFrom
import com.example.tracker.ui.theme.Expense
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Quick-start ideas — one tap fills the form. */
private val TEMPLATES = listOf(
    Routine(title = "Read Quran", icon = "📖", repeat = Repeat.DAILY, time = LocalTime.of(5, 30)),
    Routine(title = "Call family", icon = "📞", repeat = Repeat.WEEKLY, days = setOf(java.time.DayOfWeek.FRIDAY), time = LocalTime.of(14, 0), remind = true),
    Routine(title = "Laundry", icon = "🧺", repeat = Repeat.WEEKLY, days = setOf(java.time.DayOfWeek.FRIDAY)),
    Routine(title = "Clean room", icon = "🧹", repeat = Repeat.WEEKLY, days = setOf(java.time.DayOfWeek.FRIDAY)),
    Routine(title = "Gym", icon = "🏋️", repeat = Repeat.WEEKLY, days = setOf(java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.THURSDAY), time = LocalTime.of(18, 0), remind = true),
    Routine(title = "Pay mobile bill", icon = "📶", repeat = Repeat.MONTHLY, dayOfMonth = 1, time = LocalTime.of(10, 0), remind = true),
    Routine(title = "Haircut", icon = "💈", repeat = Repeat.MONTHLY, dayOfMonth = 15),
    Routine(title = "Plan next week", icon = "🗒️", repeat = Repeat.WEEKLY, days = setOf(java.time.DayOfWeek.FRIDAY), time = LocalTime.of(21, 0), remind = true),
)

@Composable
fun RoutinesScreen() {
    val ctx = LocalContext.current
    val c = ctx.container
    val cfg by c.scheduleState.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val first = settings?.firstDayOfWeek ?: java.time.DayOfWeek.SATURDAY
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Routine?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }

    fun change(block: suspend () -> Unit) = scope.launch { block(); c.scheduler.rescheduleAll() }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard("Daily habits") {
                Text("Switch off what you don't do. Tap a name to rename it — past ticks are kept.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(4.dp)
                Habits.BUILT_IN.forEach { key ->
                    val shown = key !in cfg.hiddenHabits
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(habitIcon(key), contentDescription = null, modifier = Modifier.size(22.dp),
                            tint = if (shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(onClick = { renaming = key }, color = Color.Transparent, shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Column(Modifier.heightIn(min = 48.dp).padding(horizontal = 6.dp), verticalArrangement = Arrangement.Center) {
                                Text(cfg.habitNames[key] ?: Habits.defaultLabel(key), style = MaterialTheme.typography.bodyLarge,
                                    color = if (shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (cfg.habitNames[key] != null) Text("Was: ${Habits.defaultLabel(key)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(shown, { on -> change { c.schedule.setHabitHidden(key, !on) } })
                    }
                }
            }
        }
        item {
            Text("Your own habits & routines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Button(onClick = { editing = Routine(title = "") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("+ New habit or routine", fontWeight = FontWeight.SemiBold)
            }
        }
        if (cfg.routines.isEmpty()) item {
            SectionCard("Quick start") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TEMPLATES.forEach { t -> FilterChip(false, { editing = t }, { Text("${t.icon} ${t.title}") }, modifier = Modifier.heightIn(min = 44.dp)) }
                }
            }
        }
        items(cfg.routines, key = { it.id }) { r ->
            Surface(onClick = { editing = r }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(r.icon, style = MaterialTheme.typography.headlineSmall) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(r.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(r.describe { HM.format(it) }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(r.active, { on -> change { c.schedule.saveRoutine(r.copy(active = on)) } })
                }
            }
        }
    }

    renaming?.let { key ->
        var name by remember(key) { mutableStateOf(cfg.habitNames[key] ?: Habits.defaultLabel(key)) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename habit") },
            text = {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val n = name.trim().takeIf { it != Habits.defaultLabel(key) }
                    change { c.schedule.setHabitName(key, n) }; renaming = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (cfg.habitNames[key] != null) TextButton(onClick = {
                        change { c.schedule.setHabitName(key, null) }; renaming = null
                    }) { Text("Reset name") }
                    TextButton(onClick = { renaming = null }) { Text("Cancel") }
                }
            },
        )
    }

    editing?.let { r0 ->
        var r by remember(r0) { mutableStateOf(r0) }
        var pickTime by remember { mutableStateOf(false) }
        val valid = r.title.isNotBlank() && (r.repeat != Repeat.WEEKLY || r.days.isNotEmpty())
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (r0.id == 0L) "New routine" else "Edit routine") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(r.icon, { r = r.copy(icon = it.take(4)) }, label = { Text("Icon") }, singleLine = true,
                            modifier = Modifier.weight(0.3f))
                        OutlinedTextField(r.title, { r = r.copy(title = it) }, label = { Text("Name") }, singleLine = true,
                            modifier = Modifier.weight(0.7f))
                    }
                    Gap(8.dp)
                    Text("Repeats", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Repeat.entries.forEach { rep -> FilterChip(r.repeat == rep, { r = r.copy(repeat = rep) }, { Text(rep.label) }) }
                    }
                    when (r.repeat) {
                        Repeat.WEEKLY -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            weekFrom(first).forEach { d ->
                                FilterChip(d in r.days, { r = r.copy(days = if (d in r.days) r.days - d else r.days + d) }, { Text(d.short()) })
                            }
                        }
                        Repeat.MONTHLY -> Stepper("Day of month", "${r.dayOfMonth}",
                            { r = r.copy(dayOfMonth = (r.dayOfMonth - 1).coerceAtLeast(1)) },
                            { r = r.copy(dayOfMonth = (r.dayOfMonth + 1).coerceAtMost(31)) })
                        Repeat.DAILY -> {}
                    }
                    ValueRow("Time (optional)", r.time?.let { HM.format(it) } ?: "Any time") { pickTime = true }
                    if (r.time != null) {
                        TextButton(onClick = { r = r.copy(time = null, remind = false) }) { Text("Clear time") }
                        SwitchRow("Remind me", "Notification at the time, with Done ✓", r.remind) { r = r.copy(remind = it) }
                    }
                    if (r.repeat == Repeat.MONTHLY && r.dayOfMonth > 28) Text("In shorter months it falls on the last day.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = { change { c.schedule.saveRoutine(r) }; editing = null }) { Text("Save") } },
            dismissButton = {
                Row {
                    if (r0.id != 0L) TextButton(onClick = {
                        c.scheduler.forgetRoutine(r0.id); change { c.schedule.deleteRoutine(r0.id) }; editing = null
                    }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
        if (pickTime) TimePickerDialog("Routine time", r.time ?: LocalTime.of(9, 0),
            onConfirm = { r = r.copy(time = it); pickTime = false }, onDismiss = { pickTime = false })
    }
}
