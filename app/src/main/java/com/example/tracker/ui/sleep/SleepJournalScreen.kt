package com.example.tracker.ui.sleep

import com.example.tracker.ui.theme.readable
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.db.DayLog
import com.example.tracker.data.db.SleepLogEntity
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.sleep.MoodCheck
import com.example.tracker.data.sleep.SleepMath
import com.example.tracker.ui.components.DatePickerDialogCompat
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.TimePickerDialog
import com.example.tracker.ui.components.ValueRow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

/** Mood scale shared with the Home check-in: emoji is the user's own content, label is for screen readers. */
val MOOD_FACES = listOf("😞" to "Low", "🙁" to "Meh", "😐" to "Okay", "🙂" to "Good", "😄" to "Great")

@Composable
private fun moodColor(m: Int?): Color = when (m) {
    1 -> com.example.tracker.ui.theme.StatusColors.bad
    2 -> Color(0xFFF0883E)
    3 -> Color(0xFFB0A86A)
    4 -> Color(0xFF6CC28A)
    5 -> Color(0xFF2EA66B)
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
}

/**
 * Sleep log + mood journal in two tabs. The main action sits first on each tab
 * ("Log last night" / "Write today"), then one chart, then three numbers, then history.
 */
@Composable
fun SleepJournalScreen(openLogger: Boolean = false) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val sleep by remember { c.db.sleepDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember { c.db.dayLogDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now(Schedule.ZONE)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editNight by remember { mutableStateOf(if (openLogger) SleepMath.defaultNight(LocalDateTime.now(Schedule.ZONE)) else null) }
    var editDay by remember { mutableStateOf<LocalDate?>(null) }

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Sleep") }, icon = { Icon(Icons.Rounded.Bedtime, null) })
            Tab(tab == 1, { tab = 1 }, text = { Text("Mood & journal") }, icon = { Icon(Icons.Rounded.Favorite, null) })
        }
        if (tab == 0) SleepTab(sleep, today, onEdit = { editNight = it })
        else JournalTab(logs, today, onEdit = { editDay = it })
    }

    editNight?.let { night ->
        SleepEditor(
            night = night,
            existing = sleep.firstOrNull { it.night == night },
            onSave = { e -> scope.launch { c.db.sleepDao().upsert(e) }; editNight = null },
            onDelete = { scope.launch { c.db.sleepDao().delete(night) }; editNight = null },
            onDismiss = { editNight = null },
        )
    }
    editDay?.let { day ->
        JournalEditor(
            day = day,
            existing = logs.firstOrNull { it.date == day },
            onSave = { mood, note -> scope.launch { c.db.dayLogDao().upsert(DayLog(day, mood, note)) }; editDay = null },
            onDismiss = { editDay = null },
        )
    }
}

// ---------------------------------------------------------------- Sleep

@Composable
private fun SleepTab(sleep: List<SleepLogEntity>, today: LocalDate, onEdit: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lastNightDate = today.minusDays(1)
    val lastNight = sleep.firstOrNull { it.night == lastNightDate }
    val sum = remember(sleep, today) { SleepMath.summary(sleep, today) { PlanCalendar.bedDateTime(it) } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Last night — or the button to log it
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    if (lastNight == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NightsStay, null, tint = Color(0xFF818CF8).readable(), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("How did you sleep?", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { onEdit(lastNightDate) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Log last night")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(SleepMath.formatDuration(lastNight.minutes),
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button) { onEdit(lastNightDate) }
                                .semantics { contentDescription = "Edit last night" }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Edit, null, tint = cs.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconText(Icons.Rounded.Bedtime, HM.format(lastNight.bedAt))
                            IconText(Icons.Rounded.WbTwilight, HM.format(lastNight.wakeAt))
                            lastNight.quality?.let { q -> QualityMoons(q) }
                        }
                    }
                }
            }
        }

        // 2. Two weeks of bars
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Last 14 nights", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(12.dp))
                    SleepBars(sleep, today, onTap = onEdit)
                }
            }
        }

        // 3. Three numbers
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Icons.Rounded.Schedule, if (sum.nights == 0) "–" else SleepMath.formatDuration(sum.avgMinutes), "Avg sleep", Modifier.weight(1f))
                Stat(Icons.Rounded.Bedtime, sum.avgBed?.let { HM.format(it) } ?: "–", "Avg bedtime", Modifier.weight(1f))
                Stat(Icons.Rounded.TaskAlt, if (sum.nights == 0) "–" else "${sum.onTarget}/${sum.nights}", "On time", Modifier.weight(1f))
            }
        }

        // 4. History
        if (sleep.isNotEmpty()) {
            item { Text("History", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp)) }
            items(sleep.take(30), key = { it.night.toString() }) { e ->
                Surface(onClick = { onEdit(e.night) }, shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(DAY.format(e.night), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            Text("${HM.format(e.bedAt)} – ${HM.format(e.wakeAt)}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        e.quality?.let { QualityMoons(it); Spacer(Modifier.width(12.dp)) }
                        Text(SleepMath.formatDuration(e.minutes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (e.minutes >= 7 * 60) cs.primary else Color(0xFFF0883E).readable())
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            FilledTonalButton(onClick = { onEdit(SleepMath.defaultNight(LocalDateTime.now(Schedule.ZONE))) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Log another night")
            }
        }
    }
}

@Composable
private fun SleepBars(sleep: List<SleepLogEntity>, today: LocalDate, onTap: (LocalDate) -> Unit) {
    val nights = (14 downTo 1).map { today.minusDays(it.toLong()) }
    val byNight = sleep.associateBy { it.night }
    val good = MaterialTheme.colorScheme.primary
    val short = Color(0xFFF0883E)
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val line = MaterialTheme.colorScheme.outline
    val maxMin = 10 * 60f
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics {
            contentDescription = "Sleep for the last 14 nights. " + nights.mapNotNull { n -> byNight[n]?.let { "${DAY.format(n)} ${SleepMath.formatDuration(it.minutes)}" } }.joinToString(", ")
        }) {
            val gap = 6.dp.toPx()
            val bw = (size.width - gap * 13) / 14
            nights.forEachIndexed { i, n ->
                val m = byNight[n]?.minutes?.toFloat()
                val h = if (m == null) 6.dp.toPx() else (m / maxMin).coerceIn(0.05f, 1f) * size.height
                drawRoundRect(
                    color = when { m == null -> empty; m >= 7 * 60 -> good; else -> short },
                    topLeft = Offset(i * (bw + gap), size.height - h), size = Size(bw, h), cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            val y7 = size.height - (7 * 60 / maxMin) * size.height
            drawLine(line, Offset(0f, y7), Offset(size.width, y7), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            nights.forEach { n ->
                Text(n.dayOfWeek.name.take(1), Modifier.weight(1f).clickable(role = androidx.compose.ui.semantics.Role.Button) { onTap(n) }, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(good)); Text(" 7h+", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(short)); Text(" Under 7h", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun QualityMoons(q: Int) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Quality $q of 5" }) {
        repeat(5) { i ->
            Icon(Icons.Rounded.NightsStay, null, Modifier.size(14.dp),
                tint = if (i < q) Color(0xFF818CF8).readable() else MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SleepEditor(night: LocalDate, existing: SleepLogEntity?, onSave: (SleepLogEntity) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(night) }
    var bed by remember { mutableStateOf(existing?.bedAt?.toLocalTime() ?: PlanCalendar.bedDateTime(night).toLocalTime()) }
    var wake by remember { mutableStateOf(existing?.wakeAt?.toLocalTime() ?: PlanCalendar.wakeDateTime(night.plusDays(1)).toLocalTime()) }
    var quality by remember { mutableStateOf(existing?.quality) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var pick by remember { mutableStateOf<String?>(null) }
    val (bedAt, wakeAt) = SleepMath.toDateTimes(n, bed, wake)
    val haptics = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bedtime, null) },
        title = { Text(SleepMath.formatDuration(SleepMath.minutesBetween(bedAt, wakeAt))) },
        text = {
            Column {
                ValueRow("Night of", DAY.format(n)) { pick = "night" }
                ValueRow("Went to bed", HM.format(bed)) { pick = "bed" }
                ValueRow("Woke up", HM.format(wake)) { pick = "wake" }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..5).forEach { q ->
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(if (quality == q) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .selectable(quality == q, role = Role.RadioButton) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    quality = if (quality == q) null else q
                                }
                                .semantics { contentDescription = "Sleep quality $q of 5" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.NightsStay, null,
                                tint = if ((quality ?: 0) >= q) Color(0xFF818CF8).readable() else MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), placeholder = { Text("Dreams, wake-ups…") }, maxLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(SleepLogEntity(n, bedAt, wakeAt, quality, note.trim())) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    when (pick) {
        "night" -> DatePickerDialogCompat(n, { n = it; pick = null }, { pick = null })
        "bed" -> TimePickerDialog("Went to bed", bed, { bed = it; pick = null }, { pick = null })
        "wake" -> TimePickerDialog("Woke up", wake, { wake = it; pick = null }, { pick = null })
    }
}

// ---------------------------------------------------------------- Mood & journal

@Composable
private fun JournalTab(logs: List<DayLog>, today: LocalDate, onEdit: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val todayLog = logs.firstOrNull { it.date == today }
    val low = remember(logs, today) { MoodCheck.lowStreak(logs, today) }
    val avg = remember(logs, today) { MoodCheck.average(logs, today) }
    val entries = logs.filter { it.note.isNotBlank() || it.mood != null }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Gentle check-in after a few heavy days
        if (low) item {
            Surface(shape = RoundedCornerShape(24.dp), color = cs.tertiaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Favorite, null, tint = cs.onTertiaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text("A few heavy days", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = cs.onTertiaryContainer)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Talking to someone you trust, a short walk outside, or writing it down can help.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            runCatching {
                                ctx.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Text("Message a friend") }
                        TextButton(onClick = { onEdit(today) }) { Text("Write it down", color = cs.onTertiaryContainer) }
                    }
                }
            }
        }

        // 2. Today — the main action
        item {
            Surface(onClick = { onEdit(today) }, shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(moodColor(todayLog?.mood).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Text(todayLog?.mood?.let { MOOD_FACES[it - 1].first } ?: "✍️", fontSize = 28.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Today", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Text(todayLog?.note?.ifBlank { null } ?: "Tap to write", style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // 3. Twelve-week heatmap
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("12 weeks", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                        avg?.let {
                            Text("7-day avg ", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            Text(MOOD_FACES[(it.toInt() - 1).coerceIn(0, 4)].first, fontSize = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    MoodHeatmap(logs, today, onTap = onEdit)
                }
            }
        }

        // 4. Entries
        if (entries.isNotEmpty()) {
            item { Text("Entries", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp)) }
            items(entries.take(60), key = { it.date.toString() }) { e ->
                Surface(onClick = { onEdit(e.date) }, shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                        Text(e.mood?.let { MOOD_FACES[it - 1].first } ?: "•", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(DAY.format(e.date), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodHeatmap(logs: List<DayLog>, today: LocalDate, onTap: (LocalDate) -> Unit) {
    val byDate = logs.associateBy { it.date }
    val firstCol = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).minusWeeks(11)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until 12).forEach { w ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d ->
                    val date = firstCol.plusWeeks(w.toLong()).plusDays(d.toLong())
                    val mood = byDate[date]?.mood
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp))
                            .background(if (date.isAfter(today)) Color.Transparent else moodColor(mood))
                            .then(if (date.isAfter(today)) Modifier else Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { onTap(date) })
                            .semantics { contentDescription = "${DAY.format(date)}: ${mood?.let { MOOD_FACES[it - 1].second } ?: "no mood"}" },
                    )
                }
            }
        }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Low ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (1..5).forEach { Box(Modifier.padding(horizontal = 2.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(moodColor(it))) }
        Text(" Great", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun JournalEditor(day: LocalDate, existing: DayLog?, onSave: (Int?, String) -> Unit, onDismiss: () -> Unit) {
    var mood by remember { mutableStateOf(existing?.mood) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(DAY.format(day)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MOOD_FACES.forEachIndexed { i, (face, label) ->
                        val v = i + 1
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(if (mood == v) moodColor(v).copy(alpha = 0.3f) else Color.Transparent)
                                .selectable(mood == v, role = Role.RadioButton) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    mood = if (mood == v) null else v
                                }
                                .semantics { contentDescription = "Mood: $label" },
                            contentAlignment = Alignment.Center,
                        ) { Text(face, fontSize = 24.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 4,
                    placeholder = { Text("What happened today? One line is enough.") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(mood, note.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- bits

@Composable
private fun IconText(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
