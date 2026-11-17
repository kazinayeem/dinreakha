package com.example.tracker.ui.settings

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.Schedule.Block
import com.example.tracker.data.schedule.Schedule.BlockType
import com.example.tracker.data.schedule.Schedule.Bus
import com.example.tracker.data.schedule.Schedule.BusDirection
import com.example.tracker.data.schedule.Schedule.ClassSession
import com.example.tracker.data.schedule.Schedule.Phase
import com.example.tracker.data.schedule.Schedule.Prayer
import com.example.tracker.ui.components.DatePickerDialogCompat
import com.example.tracker.ui.components.DayChips
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.SHORT_DATE
import com.example.tracker.ui.components.SectionCard
import com.example.tracker.ui.components.TimePickerDialog
import com.example.tracker.ui.components.ValueRow
import com.example.tracker.ui.components.long
import com.example.tracker.ui.components.short
import com.example.tracker.ui.components.weekFrom
import com.example.tracker.ui.theme.Expense
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Small helper so every dialog can open a time picker. */
private class TimeAsk(val title: String, val initial: LocalTime, val onPick: (LocalTime) -> Unit)

@Composable
fun ScheduleEditorScreen() {
    val ctx = LocalContext.current
    val c = ctx.container
    val cfg by c.scheduleState.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val first = s.firstDayOfWeek

    var timeAsk by remember { mutableStateOf<TimeAsk?>(null) }
    var pickStart by remember { mutableStateOf(false) }
    var editPhase by remember { mutableStateOf<Phase?>(null) }
    var editClass by remember { mutableStateOf<ClassSession?>(null) }
    var editBus by remember { mutableStateOf<Bus?>(null) }
    var editBlock by remember { mutableStateOf<Block?>(null) }
    var addHoliday by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    /** Save, then reschedule every alarm immediately. */
    fun change(block: suspend () -> Unit) = scope.launch { block(); c.scheduler.rescheduleAll() }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
        }

        item {
            SectionCard("Plan") {
                ValueRow("Start (Day 1)", SHORT_DATE.format(cfg.planStart) + " ${cfg.planStart.year}") { pickStart = true }
                Stepper("Length", "${cfg.planDays} days", onMinus = { change { c.schedule.setPlanDays(cfg.planDays - 1) } },
                    onPlus = { change { c.schedule.setPlanDays(cfg.planDays + 1) } })
                Stepper("Screens off", "${cfg.screensOffLeadMin} min before bed",
                    onMinus = { change { c.schedule.setScreensOffLead(cfg.screensOffLeadMin - 15) } },
                    onPlus = { change { c.schedule.setScreensOffLead(cfg.screensOffLeadMin + 15) } })
                Text("Ends ${SHORT_DATE.format(cfg.planEnd)} ${cfg.planEnd.year}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SectionCard("Sleep phases & goals", trailing = {
                TextButton(onClick = {
                    val last = cfg.phases.maxByOrNull { it.number }
                    val startDay = (last?.days?.last ?: 0) + 1
                    editPhase = Phase((last?.number ?: 0) + 1, startDay..(startDay + 6), last?.wake ?: LocalTime.of(7, 0),
                        last?.bed ?: LocalTime.of(23, 0), last?.prayers ?: setOf(Prayer.MAGHRIB), last?.expectedFocusBlocks ?: 1)
                }) { Text("+ Phase") }
            }) {
                cfg.phases.sortedBy { it.number }.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider()
                    ValueRow("Phase ${p.number}, days ${p.days.first}–${p.days.last}",
                        "☀ ${HM.format(p.wake)}  🌙 ${HM.format(p.bed)}",
                        supporting = "${p.expectedFocusBlocks} focus block(s); prayers: ${p.prayers.sortedBy { it.ordinal }.joinToString { it.label }}") {
                        editPhase = p
                    }
                }
            }
        }

        item {
            SectionCard("Class-day wake overrides") {
                weekFrom(first).forEach { d ->
                    val t = cfg.wakeOverrides[d]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            ValueRow(d.long(), t?.let { HM.format(it) } ?: "—") {
                                timeAsk = TimeAsk("${d.long()} wake", t ?: LocalTime.of(7, 0)) { nt -> change { c.schedule.setWakeOverride(d, nt) } }
                            }
                        }
                        if (t != null) TextButton(onClick = { change { c.schedule.setWakeOverride(d, null) } }) { Text("Clear") }
                    }
                }
            }
        }

        item {
            SectionCard("Classes", trailing = {
                TextButton(onClick = { editClass = ClassSession(first, LocalTime.of(10, 0), LocalTime.of(11, 30), "", "") }) { Text("+ Class") }
            }) {
                weekFrom(first).forEach { d ->
                    val list = cfg.classesOn(d)
                    if (list.isNotEmpty()) {
                        Text(d.long(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp))
                        list.forEach { cl ->
                            ValueRow(cl.title, "${HM.format(cl.start)}–${HM.format(cl.end)}", supporting = cl.room.ifBlank { null }) { editClass = cl }
                        }
                    }
                }
                if (cfg.classes.isEmpty()) Text("No classes. Tap + Class.")
            }
        }

        item {
            SectionCard("Bus (${Schedule.BUS_ROUTE})", trailing = {
                TextButton(onClick = { editBus = Bus(first, BusDirection.TO_CAMPUS, LocalTime.of(8, 0)) }) { Text("+ Bus") }
            }) {
                weekFrom(first).forEach { d ->
                    val list = cfg.buses.filter { it.day == d }.sortedBy { it.time }
                    if (list.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.short(), Modifier.padding(end = 12.dp), fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.forEach { b ->
                                FilterChip(false, { editBus = b }, { Text("${if (b.direction == BusDirection.TO_CAMPUS) "→ campus" else "→ home"} ${HM.format(b.time)}") })
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Study / research / code blocks", trailing = {
                TextButton(onClick = { editBlock = Block(BlockType.RESEARCH, LocalTime.of(9, 0), LocalTime.of(10, 30), day = first) }) { Text("+ Block") }
            }) {
                BlockType.entries.forEach { type ->
                    val list = cfg.blocks.filter { it.type == type }.sortedBy { it.day?.let { d -> weekFrom(first).indexOf(d) } ?: -1 }
                    if (list.isNotEmpty()) {
                        Text(type.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp))
                        list.forEach { b ->
                            ValueRow(b.day?.long() ?: "Every day", "${HM.format(b.start)}–${HM.format(b.end)}",
                                supporting = b.place.ifBlank { null }) { editBlock = b }
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Prayer times") {
                Prayer.entries.forEach { p ->
                    ValueRow(p.label, HM.format(s.prayerTimes[p] ?: p.defaultTime)) {
                        timeAsk = TimeAsk("${p.label} time", s.prayerTimes[p] ?: p.defaultTime) { t -> change { c.settings.setPrayerTime(p, t) } }
                    }
                }
            }
        }

        item {
            SectionCard("Holidays", trailing = { TextButton(onClick = { addHoliday = true }) { Text("+ Holiday") } }) {
                cfg.holidays.toSortedMap().forEach { (d, name) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${SHORT_DATE.format(d)} ${d.year}", Modifier.weight(0.45f), fontWeight = FontWeight.SemiBold)
                        Text(name, Modifier.weight(0.45f))
                        TextButton(onClick = { change { c.schedule.deleteHoliday(d) } }) { Text("✕") }
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Reset schedule to my original plan", color = Expense)
            }
        }
    }

    // ---------------- dialogs ----------------

    timeAsk?.let { a -> TimePickerDialog(a.title, a.initial, onConfirm = { a.onPick(it); timeAsk = null }, onDismiss = { timeAsk = null }) }

    if (pickStart) DatePickerDialogCompat(cfg.planStart, onConfirm = { d -> change { c.schedule.setPlanStart(d) }; pickStart = false },
        onDismiss = { pickStart = false })

    editPhase?.let { p0 ->
        var p by remember(p0) { mutableStateOf(p0) }
        val exists = cfg.phases.any { it.number == p0.number }
        AlertDialog(
            onDismissRequest = { editPhase = null },
            title = { Text("Phase ${p.number}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Stepper("First day", "${p.days.first}", { p = p.copy(days = (p.days.first - 1).coerceAtLeast(1)..p.days.last) },
                        { p = p.copy(days = (p.days.first + 1).coerceAtMost(p.days.last)..p.days.last) })
                    Stepper("Last day", "${p.days.last}", { p = p.copy(days = p.days.first..(p.days.last - 1).coerceAtLeast(p.days.first)) },
                        { p = p.copy(days = p.days.first..(p.days.last + 1)) })
                    ValueRow("Wake", HM.format(p.wake)) { timeAsk = TimeAsk("Wake time", p.wake) { t -> p = p.copy(wake = t) } }
                    ValueRow("Bed by", HM.format(p.bed), supporting = "Before noon = after midnight") {
                        timeAsk = TimeAsk("Bedtime", p.bed) { t -> p = p.copy(bed = t) }
                    }
                    Text("Focus blocks expected", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..3).forEach { n -> FilterChip(p.expectedFocusBlocks == n, { p = p.copy(expectedFocusBlocks = n) }, { Text("$n") }) }
                    }
                    Text("Prayer goal (reminded)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Prayer.entries.forEach { pr ->
                            FilterChip(pr in p.prayers, { p = p.copy(prayers = if (pr in p.prayers) p.prayers - pr else p.prayers + pr) }, { Text(pr.label) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { change { c.schedule.savePhase(p) }; editPhase = null }) { Text("Save") } },
            dismissButton = {
                Row {
                    if (exists && cfg.phases.size > 1) TextButton(onClick = { change { c.schedule.deletePhase(p.number) }; editPhase = null }) {
                        Text("Delete", color = Expense)
                    }
                    TextButton(onClick = { editPhase = null }) { Text("Cancel") }
                }
            },
        )
    }

    editClass?.let { c0 ->
        var cl by remember(c0) { mutableStateOf(c0) }
        AlertDialog(
            onDismissRequest = { editClass = null },
            title = { Text(if (c0.id == 0L) "New class" else "Edit class") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(cl.title, { cl = cl.copy(title = it) }, label = { Text("Course (e.g. AI (SE333))") }, singleLine = true)
                    OutlinedTextField(cl.room, { cl = cl.copy(room = it) }, label = { Text("Room") }, singleLine = true)
                    Gap(6.dp)
                    DayChips(cl.day, { cl = cl.copy(day = it) }, first)
                    ValueRow("Starts", HM.format(cl.start)) { timeAsk = TimeAsk("Class starts", cl.start) { t -> cl = cl.copy(start = t) } }
                    ValueRow("Ends", HM.format(cl.end)) { timeAsk = TimeAsk("Class ends", cl.end) { t -> cl = cl.copy(end = t) } }
                }
            },
            confirmButton = {
                TextButton(enabled = cl.title.isNotBlank() && cl.end.isAfter(cl.start),
                    onClick = { change { c.schedule.saveClass(cl) }; editClass = null }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (c0.id != 0L) TextButton(onClick = { change { c.schedule.deleteClass(c0.id) }; editClass = null }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editClass = null }) { Text("Cancel") }
                }
            },
        )
    }

    editBus?.let { b0 ->
        var b by remember(b0) { mutableStateOf(b0) }
        AlertDialog(
            onDismissRequest = { editBus = null },
            title = { Text(if (b0.id == 0L) "New bus" else "Edit bus") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BusDirection.entries.forEach { d -> FilterChip(b.direction == d, { b = b.copy(direction = d) }, { Text(d.label) }) }
                    }
                    DayChips(b.day, { b = b.copy(day = it) }, first)
                    ValueRow("Departs", HM.format(b.time)) { timeAsk = TimeAsk("Bus time", b.time) { t -> b = b.copy(time = t) } }
                }
            },
            confirmButton = { TextButton(onClick = { change { c.schedule.saveBus(b) }; editBus = null }) { Text("Save") } },
            dismissButton = {
                Row {
                    if (b0.id != 0L) TextButton(onClick = { change { c.schedule.deleteBus(b0.id) }; editBus = null }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editBus = null }) { Text("Cancel") }
                }
            },
        )
    }

    editBlock?.let { b0 ->
        var b by remember(b0) { mutableStateOf(b0) }
        AlertDialog(
            onDismissRequest = { editBlock = null },
            title = { Text(if (b0.id == 0L) "New block" else "Edit block") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BlockType.entries.forEach { t -> FilterChip(b.type == t, { b = b.copy(type = t) }, { Text(t.label) }) }
                    }
                    DayChips(b.day, { b = b.copy(day = it) }, first, includeEveryDay = true, onEveryDay = { b = b.copy(day = null) })
                    ValueRow("Starts", HM.format(b.start)) { timeAsk = TimeAsk("Block starts", b.start) { t -> b = b.copy(start = t) } }
                    ValueRow("Ends", HM.format(b.end)) { timeAsk = TimeAsk("Block ends", b.end) { t -> b = b.copy(end = t) } }
                    OutlinedTextField(b.place, { b = b.copy(place = it) }, label = { Text("Place / note (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = b.end.isAfter(b.start), onClick = { change { c.schedule.saveBlock(b) }; editBlock = null }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (b0.id != 0L) TextButton(onClick = { change { c.schedule.deleteBlock(b0.id) }; editBlock = null }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editBlock = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (addHoliday) {
        var date by remember { mutableStateOf(LocalDate.now(Schedule.ZONE)) }
        var name by remember { mutableStateOf("") }
        var pick by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { addHoliday = false },
            title = { Text("Add holiday") },
            text = {
                Column {
                    ValueRow("Date", "${SHORT_DATE.format(date)} ${date.year}") { pick = true }
                    OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Victory Day)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { change { c.schedule.saveHoliday(date, name) }; addHoliday = false }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { addHoliday = false }) { Text("Cancel") } },
        )
        if (pick) DatePickerDialogCompat(date, onConfirm = { date = it; pick = false }, onDismiss = { pick = false })
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset the schedule?") },
        text = { Text("Back to your original classes, buses, blocks, phases and holidays. " +
            "Your ticks, mood, routines and money are not touched.") },
        confirmButton = {
            TextButton(onClick = {
                confirmReset = false
                change { c.schedule.resetToDefaults() }
                Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Schedule reset"), Toast.LENGTH_SHORT).show()
            }) { Text("Reset", color = Expense) }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onMinus()
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(38.dp),
            ) {
                Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onPlus()
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(38.dp),
            ) {
                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

