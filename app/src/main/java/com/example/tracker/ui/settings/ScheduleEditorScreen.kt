package com.example.tracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Close
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

    var tab by rememberSaveable { mutableStateOf(0) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now(Schedule.ZONE).dayOfWeek) }
    var addMenu by remember { mutableStateOf(false) }
    var editRoute by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Three short pages instead of one long scroll of eight cards.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            listOf("Week", "Sleep", "Prayers").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label, maxLines = 1) }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> weekPage(
                    cfg = cfg, first = first, day = day, onDay = { day = it },
                    onWake = {
                        val t = cfg.wakeOverrides[day]
                        timeAsk = TimeAsk("${day.long()} wake-up", t ?: LocalTime.of(7, 0)) { nt -> change { c.schedule.setWakeOverride(day, nt) } }
                    },
                    onClearWake = { change { c.schedule.setWakeOverride(day, null) } },
                    onClass = { editClass = it }, onBus = { editBus = it }, onBlock = { editBlock = it },
                    onRoute = { editRoute = true },
                )
                1 -> sleepPage(
                    cfg = cfg,
                    onStart = { pickStart = true },
                    onDays = { n -> change { c.schedule.setPlanDays(n) } },
                    onScreensOff = { n -> change { c.schedule.setScreensOffLead(n) } },
                    onPhase = { editPhase = it },
                    onNewPhase = {
                        val last = cfg.phases.maxByOrNull { it.number }
                        val startDay = (last?.days?.last ?: 0) + 1
                        editPhase = Phase((last?.number ?: 0) + 1, startDay..(startDay + 6), last?.wake ?: LocalTime.of(7, 0),
                            last?.bed ?: LocalTime.of(23, 0), last?.prayers ?: setOf(Prayer.MAGHRIB), last?.expectedFocusBlocks ?: 1)
                    },
                    onReset = { confirmReset = true },
                )
                else -> prayersPage(
                    prayerTime = { p -> s.prayerTimes[p] ?: p.defaultTime },
                    holidays = cfg.holidays,
                    onPrayer = { p -> timeAsk = TimeAsk("${p.label} time", s.prayerTimes[p] ?: p.defaultTime) { t -> change { c.settings.setPrayerTime(p, t) } } },
                    onAddHoliday = { addHoliday = true },
                    onDeleteHoliday = { d -> change { c.schedule.deleteHoliday(d) } },
                )
            }
        }
    }

    // "Add" on the Week page: one button, then pick what to add — prefilled with the selected day.
    if (tab == 0) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
        ExtendedFloatingActionButton(
            onClick = { addMenu = true },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Add to ${day.long()}") },
        )
        DropdownMenu(addMenu, { addMenu = false }) {
            DropdownMenuItem({ Text("Class") }, { addMenu = false; editClass = ClassSession(day, LocalTime.of(10, 0), LocalTime.of(11, 30), "", "") },
                leadingIcon = { Icon(Icons.Rounded.School, null) })
            DropdownMenuItem({ Text("Bus") }, { addMenu = false; editBus = Bus(day, BusDirection.TO_CAMPUS, LocalTime.of(8, 0)) },
                leadingIcon = { Icon(Icons.Rounded.DirectionsBus, null) })
            DropdownMenuItem({ Text("Study / research / code block") }, { addMenu = false; editBlock = Block(BlockType.STUDY, LocalTime.of(16, 0), LocalTime.of(17, 30), day = day) },
                leadingIcon = { Icon(Icons.Rounded.Timer, null) })
        }
    }

    if (editRoute) {
        var name by remember { mutableStateOf(cfg.busRoute) }
        AlertDialog(
            onDismissRequest = { editRoute = false },
            title = { Text("Bus route") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Route name") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { change { c.schedule.setBusRoute(name) }; editRoute = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editRoute = false }) { Text("Cancel") } },
        )
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
                    androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..3).forEach { n -> FilterChip(p.expectedFocusBlocks == n, { p = p.copy(expectedFocusBlocks = n) }, { Text("$n", maxLines = 1) }) }
                    }
                    Text("Prayer goal (reminded)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Prayer.entries.forEach { pr ->
                            FilterChip(pr in p.prayers, { p = p.copy(prayers = if (pr in p.prayers) p.prayers - pr else p.prayers + pr) }, { Text(pr.label, maxLines = 1) })
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
                    androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BusDirection.entries.forEach { d -> FilterChip(b.direction == d, { b = b.copy(direction = d) }, { Text(d.label, maxLines = 1) }) }
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
                    androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BlockType.entries.forEach { t -> FilterChip(b.type == t, { b = b.copy(type = t) }, { Text(t.label, maxLines = 1) }) }
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
                com.example.tracker.ui.components.showMessage(ctx, "Schedule reset")
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
                modifier = Modifier.size(48.dp),
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                modifier = Modifier.size(48.dp),
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// ---------------- pages ----------------

/** One entry on the selected day's timeline. */
private data class DayItem(val time: LocalTime, val end: LocalTime?, val icon: ImageVector, val title: String, val detail: String?, val onClick: () -> Unit)

private fun androidx.compose.foundation.lazy.LazyListScope.weekPage(
    cfg: com.example.tracker.data.schedule.ScheduleConfig, first: java.time.DayOfWeek, day: java.time.DayOfWeek,
    onDay: (java.time.DayOfWeek) -> Unit, onWake: () -> Unit, onClearWake: () -> Unit,
    onClass: (ClassSession) -> Unit, onBus: (Bus) -> Unit, onBlock: (Block) -> Unit, onRoute: () -> Unit,
) {
    item("days") {
        // Day picker: a dot under days that have classes, so the week's shape shows at a glance.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekFrom(first).forEach { d ->
                val sel = d == day
                val busy = cfg.classesOn(d).isNotEmpty()
                Surface(
                    onClick = { onDay(d) }, shape = RoundedCornerShape(14.dp),
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(d.short(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        Box(Modifier.padding(top = 4.dp).size(6.dp).background(
                            if (busy) (if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) else Color.Transparent,
                            CircleShape))
                    }
                }
            }
        }
    }
    item("wake") {
        val t = cfg.wakeOverrides[day]
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ValueRow("Wake-up on ${day.long()}", t?.let { HM.format(it) } ?: "From sleep plan",
                        supporting = if (t != null) "Earlier than usual, for this weekday only" else "Tap to set an earlier time for this day",
                        onClick = onWake)
                }
                if (t != null) TextButton(onClick = onClearWake) { Text("Clear") }
            }
        }
    }
    val items = buildList {
        cfg.buses.filter { it.day == day }.forEach { b ->
            add(DayItem(b.time, null, Icons.Rounded.DirectionsBus, if (b.direction == BusDirection.TO_CAMPUS) "Bus to campus" else "Bus home", cfg.busRoute) { onBus(b) })
        }
        cfg.classesOn(day).forEach { cl -> add(DayItem(cl.start, cl.end, Icons.Rounded.School, cl.title, cl.room.ifBlank { null }) { onClass(cl) }) }
        BlockType.entries.forEach { type ->
            cfg.blockOn(day, type)?.let { b ->
                add(DayItem(b.start, b.end, Icons.Rounded.Timer, type.label,
                    listOfNotNull(if (b.day == null) "Every day" else null, b.place.ifBlank { null }).joinToString(", ").ifBlank { null }) { onBlock(b) })
            }
        }
    }.sortedBy { it.time }
    item("timeline") {
        SectionCard(day.long()) {
            if (items.isEmpty()) {
                Text("Nothing planned on ${day.long()}. Use Add to put a class, bus or study block here.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            items.forEachIndexed { i, it ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Surface(onClick = it.onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(76.dp)) {
                            Text(HM.format(it.time), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            it.end?.let { e -> Text(HM.format(e), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Icon(it.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(it.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            it.detail?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Icon(Icons.Rounded.Edit, contentDescription = com.example.tracker.ui.i18n.tr("Edit"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
    item("route") {
        SectionCard {
            ValueRow("Bus route", cfg.busRoute, supporting = "Shown on bus reminders", onClick = onRoute)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sleepPage(
    cfg: com.example.tracker.data.schedule.ScheduleConfig,
    onStart: () -> Unit, onDays: (Int) -> Unit, onScreensOff: (Long) -> Unit,
    onPhase: (Phase) -> Unit, onNewPhase: () -> Unit, onReset: () -> Unit,
) {
    item("plan") {
        SectionCard("Plan") {
            ValueRow("Starts (day 1)", SHORT_DATE.format(cfg.planStart) + " ${cfg.planStart.year}",
                supporting = "Ends ${SHORT_DATE.format(cfg.planEnd)} ${cfg.planEnd.year}", onClick = onStart)
            Stepper("Length", "${cfg.planDays} days", onMinus = { onDays(cfg.planDays - 1) }, onPlus = { onDays(cfg.planDays + 1) })
            Stepper("Screens off", "${cfg.screensOffLeadMin} min before bed",
                onMinus = { onScreensOff(cfg.screensOffLeadMin - 15) }, onPlus = { onScreensOff(cfg.screensOffLeadMin + 15) })
        }
    }
    item("phases") {
        SectionCard("Sleep phases", trailing = { TextButton(onClick = onNewPhase) { Text("Add phase") } }) {
            Text("Each phase sets your wake-up and bedtime for a run of plan days.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            cfg.phases.sortedBy { it.number }.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Surface(onClick = { onPhase(p) }, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp)) {
                        Text("Days ${p.days.first}–${p.days.last}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WbSunny, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(" ${HM.format(p.wake)}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(16.dp))
                            Icon(Icons.Rounded.Bedtime, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(" ${HM.format(p.bed)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("${p.expectedFocusBlocks} focus ${if (p.expectedFocusBlocks == 1) "block" else "blocks"} a day, prayer goal: " +
                            p.prayers.sortedBy { it.ordinal }.joinToString { it.label }.ifBlank { "none" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    item("reset") {
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Reset schedule to my original plan", color = Expense)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.prayersPage(
    prayerTime: (Prayer) -> LocalTime, holidays: Map<LocalDate, String>,
    onPrayer: (Prayer) -> Unit, onAddHoliday: () -> Unit, onDeleteHoliday: (LocalDate) -> Unit,
) {
    item("prayers") {
        SectionCard("Prayer times") {
            Prayer.entries.forEach { p -> ValueRow(p.label, HM.format(prayerTime(p))) { onPrayer(p) } }
        }
    }
    item("holidays") {
        SectionCard("Holidays", trailing = { TextButton(onClick = onAddHoliday) { Text("Add holiday") } }) {
            Text("No classes or buses on these days.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            if (holidays.isEmpty()) Text("No holidays added.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            holidays.toSortedMap().forEach { (d, name) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("${SHORT_DATE.format(d)} ${d.year}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDeleteHoliday(d) }) {
                        Icon(Icons.Rounded.Close, contentDescription = com.example.tracker.ui.i18n.tr("Remove holiday"))
                    }
                }
            }
        }
    }
}
