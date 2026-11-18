package com.example.tracker.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
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

private val ICONS = listOf("✅", "📖", "🏃", "💧", "🧘", "🙏", "📞", "🧹", "🧺", "💪", "🥗", "💊", "📝", "💸")

/** One row on this page: dimmed when switched off. */
@Composable
private fun ItemCard(active: Boolean, onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val color = MaterialTheme.colorScheme.surfaceContainer
    val row: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(start = 14.dp, end = 6.dp)
            .then(if (active) Modifier else Modifier.alpha(0.6f)), verticalAlignment = Alignment.CenterVertically, content = content)
    }
    if (onClick != null) Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = color) { row() }
    else Surface(shape = MaterialTheme.shapes.medium, color = color) { row() }
}

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

    val feedback = com.example.tracker.ui.components.LocalFeedback.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf<Routine?>(null) }

    fun delete(r: Routine) {
        c.scheduler.forgetRoutine(r.id)
        change { c.schedule.deleteRoutine(r.id) }
        // Same id on undo, so its past ticks still count.
        feedback?.show("Deleted ${r.title}", "Undo") { change { c.schedule.saveRoutine(r) } }
    }

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            listOf("Daily habits", "My routines").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label, maxLines = 1) }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tab == 0) {
                item {
                    Text("These are on your checklist every day. Turn off what you don't do; rename any of them. Past ticks are kept.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(Habits.BUILT_IN, key = { it }) { key ->
                    val shown = key !in cfg.hiddenHabits
                    val renamed = cfg.habitNames[key]
                    ItemCard(active = shown) {
                        Icon(habitIcon(key), contentDescription = null, modifier = Modifier.size(24.dp),
                            tint = if (shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(renamed ?: Habits.defaultLabel(key), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(if (!shown) "Off" else if (renamed != null) "Renamed from ${Habits.defaultLabel(key)}" else "Every day",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { renaming = key }) {
                            Icon(Icons.Rounded.Edit, contentDescription = com.example.tracker.ui.i18n.tr("Rename"))
                        }
                        Switch(shown, { on -> change { c.schedule.setHabitHidden(key, !on) } })
                    }
                }
            } else {
                item {
                    Text("Your own habits: daily, on chosen weekdays, or once a month, with an optional reminder.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(cfg.routines, key = { it.id }) { r ->
                    ItemCard(active = r.active, onClick = { editing = r }) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(r.icon, style = MaterialTheme.typography.headlineSmall) }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(r.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(if (r.active) r.describe { HM.format(it) } else "Off", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = r }) { Icon(Icons.Rounded.Edit, contentDescription = com.example.tracker.ui.i18n.tr("Edit")) }
                        IconButton(onClick = { confirmDelete = r }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = com.example.tracker.ui.i18n.tr("Delete"), tint = Expense)
                        }
                        Switch(r.active, { on -> change { c.schedule.saveRoutine(r.copy(active = on)) } })
                    }
                }
                if (cfg.routines.size < 4) item {
                    // Ideas stay visible until the list has a few entries; one tap fills the form.
                    SectionCard("Ideas") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TEMPLATES.filter { t -> cfg.routines.none { it.title == t.title } }.forEach { t ->
                                FilterChip(false, { editing = t }, { Text("${t.icon} ${t.title}") }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (tab == 1) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
        ExtendedFloatingActionButton(
            onClick = { editing = Routine(title = "") },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New routine") },
        )
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${r.title}?") },
            text = { Text("It leaves your checklist and its reminder stops. You can undo right after.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; delete(r) }) { Text("Delete", color = Expense) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
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
                    // Quick icon picks — typing your own emoji in the box still works.
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ICONS.forEach { e ->
                            FilterChip(r.icon == e, { r = r.copy(icon = e) }, { Text(e, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                                modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    Gap(8.dp)
                    Text("Repeats", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Repeat.entries.forEach { rep -> FilterChip(r.repeat == rep, { r = r.copy(repeat = rep) }, { Text(rep.label, maxLines = 1) }) }
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
                    if (r0.id != 0L) TextButton(onClick = { editing = null; delete(r0) }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
        if (pickTime) TimePickerDialog("Routine time", r.time ?: LocalTime.of(9, 0),
            onConfirm = { r = r.copy(time = it); pickTime = false }, onDismiss = { pickTime = false })
    }
}
