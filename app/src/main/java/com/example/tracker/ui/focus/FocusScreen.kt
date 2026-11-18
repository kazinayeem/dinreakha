package com.example.tracker.ui.focus

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.focus.FocusMath
import com.example.tracker.data.focus.FocusRun
import com.example.tracker.data.focus.FocusSessionInfo
import com.example.tracker.data.focus.FocusType
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.SHORT_DATE
import com.example.tracker.ui.components.Space
import com.example.tracker.ui.components.SwitchRow
import com.example.tracker.ui.settings.Stepper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

@Composable
fun FocusScreen(noteForSession: Long? = null, preset: Pair<FocusType, Int>? = null) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val run by c.focus.active.collectAsStateWithLifecycle(null)
    val sessions by c.focus.sessions.collectAsStateWithLifecycle(emptyList())
    var noteId by remember { mutableStateOf(noteForSession) }

    var now by remember { mutableStateOf(LocalDateTime.now(Schedule.ZONE)) }
    LaunchedEffect(run) {
        while (true) {
            now = LocalDateTime.now(Schedule.ZONE)
            delay(1000)
        }
    }

    val today = LocalDate.now(Schedule.ZONE)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY))
    val todays = sessions.filter { it.start.toLocalDate() == today }
    val weekMinutes = sessions.filter { !it.start.toLocalDate().isBefore(weekStart) }.sumOf { it.minutes }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The Study tab draws the page title; only a running session shows its own name here.
        if (run != null) item {
            Column(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = run!!.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = if (run == null) "Deep work & focused study sessions" else "Session in progress",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Active Timer / Setup Panel
        item {
            val r = run
            if (r == null) {
                StartPanel(preset) { type, minutes, dnd, title ->
                    scope.launch { c.focusController.start(type, minutes, dnd, title) }
                }
            } else {
                RunningPanel(
                    r = r,
                    now = now,
                    onPause = { scope.launch { c.focusController.pause() } },
                    onResume = { scope.launch { c.focusController.resume() } },
                    onExtend = { scope.launch { c.focusController.extend(5) } },
                    onStop = { scope.launch { c.focusController.finish(fromAlarm = false) } },
                )
            }
        }

        // Summary Stats Row (Today vs This Week)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Today, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = FocusMath.formatHm(todays.sumOf { it.minutes }),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DateRange, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("This week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = FocusMath.formatHm(weekMinutes),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }

        // Past Sessions Section
        if (todays.isNotEmpty()) {
            item {
                Text(
                    text = "Today's sessions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        todays.forEachIndexed { i, s ->
                            SessionRow(s = s) { noteId = s.id }
                            if (i < todays.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        val earlier = sessions.filter { it.start.toLocalDate() != today }.take(10)
        if (earlier.isNotEmpty()) {
            item {
                Text(
                    text = "Recent sessions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        earlier.forEachIndexed { i, s ->
                            SessionRow(s = s, showDate = true) { noteId = s.id }
                            if (i < earlier.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(start = 54.dp, end = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Note Dialog
    noteId?.let { id ->
        val s = sessions.firstOrNull { it.id == id }
        var text by remember(id, s?.note) { mutableStateOf(s?.note ?: "") }
        AlertDialog(
            onDismissRequest = { noteId = null },
            title = { Text("Session note") },
            text = {
                Column {
                    s?.let {
                        Text(
                            text = "${it.type.label}, ${FocusMath.formatHm(it.minutes)} at ${HM.format(it.start)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        placeholder = { Text("What did you accomplish?") },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { c.focus.setNote(id, text) }
                    noteId = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch { c.focus.delete(id) }
                        noteId = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { noteId = null }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}

@Composable
private fun StartPanel(
    preset: Pair<FocusType, Int>?,
    onStart: (FocusType, Int, Boolean, String) -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    var type by rememberSaveable { mutableStateOf(preset?.first ?: FocusType.STUDY) }
    var minutes by rememberSaveable { mutableIntStateOf(preset?.second ?: 45) }
    var dnd by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now(Schedule.ZONE)
    val blocks = PlanCalendar.focusBlocks(today)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // If scheduled focus blocks exist for today, show them as 1-tap quick start tiles
            if (blocks.isNotEmpty()) {
                Column {
                    Text(
                        text = "Scheduled for today",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        blocks.forEach { fb ->
                            val b = fb.block
                            val len = Duration.between(b.start, b.end).toMinutes().toInt()
                            val t = when (b.type) {
                                Schedule.BlockType.STUDY -> FocusType.STUDY
                                Schedule.BlockType.RESEARCH -> FocusType.RESEARCH
                                Schedule.BlockType.CODE -> FocusType.CODE
                            }
                            Surface(
                                onClick = { onStart(t, len, dnd, b.type.label + if (b.place.isNotBlank()) ", ${b.place}" else "") },
                                shape = RoundedCornerShape(14.dp),
                                color = if (fb.expected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = when (t) {
                                            FocusType.STUDY -> Icons.AutoMirrored.Rounded.MenuBook
                                            FocusType.RESEARCH -> Icons.Rounded.Science
                                            FocusType.CODE -> Icons.Rounded.Code
                                            FocusType.OTHER -> Icons.Rounded.Timer
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = b.type.label,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        )
                                        Text(
                                            text = "${FocusMath.formatHm(len)} at ${HM.format(b.start)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }

            // Mode Selector
            Column {
                Text(
                    text = "Focus mode",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        FocusType.STUDY to Icons.AutoMirrored.Rounded.MenuBook,
                        FocusType.RESEARCH to Icons.Rounded.Science,
                        FocusType.CODE to Icons.Rounded.Code,
                    ).forEach { (t, icon) ->
                        val selected = type == t
                        Surface(
                            onClick = { type = t },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = t.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            // Preset Durations
            Column {
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 25, 45, 60, 90).forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { minutes = m },
                            label = { Text("$m min") },
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }
            }

            // Length Stepper
            Stepper(
                label = "Adjust Length",
                value = "$minutes min",
                onMinus = { minutes = (minutes - 5).coerceAtLeast(5) },
                onPlus = { minutes = (minutes + 5).coerceAtMost(240) },
            )

            // DND Switch
            val hasAccess = c.focusController.hasDndAccess()
            SwitchRow(
                title = "Do Not Disturb",
                subtitle = if (dnd && !hasAccess) "Tap to allow DND permission" else null,
                checked = dnd,
                onChange = { on ->
                    if (on && !hasAccess) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        dnd = on
                    }
                },
            )

            // Start Button
            Button(
                onClick = { onStart(type, minutes, dnd && hasAccess, type.label) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Start ${minutes} Min Focus", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun RunningPanel(
    r: FocusRun,
    now: LocalDateTime,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExtend: () -> Unit,
    onStop: () -> Unit,
) {
    val left = r.remaining(now)
    val frac by animateFloatAsState(
        targetValue = (r.elapsed(now).seconds.toFloat() / (r.plannedMinutes * 60f)).coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "dial",
    )
    val mm = left.toMinutes()
    val ss = left.seconds % 60
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Circular countdown dial
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .semantics {
                        contentDescription = "$mm minutes $ss seconds left${if (r.isPaused) ", paused" else ""}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(230.dp)) {
                    val s = 12.dp.toPx()
                    val d = size.minDimension - s
                    drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), Size(d, d), style = Stroke(s))
                    drawArc(fg, -90f, 360f * frac, false, Offset(s / 2, s / 2), Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%d:%02d".format(mm, ss),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = if (r.isPaused) "Paused" else "until ${HM.format(r.endsAt(now))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Controls: round icon buttons that never get clipped on narrow screens.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundControl(Icons.Rounded.Add, "+5 min", size = 56.dp,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurface, onClick = onExtend)
                RoundControl(
                    if (r.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    if (r.isPaused) "Resume" else "Pause",
                    size = 76.dp,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    onClick = if (r.isPaused) onResume else onPause,
                )
                RoundControl(Icons.Rounded.Stop, "Stop", size = 56.dp,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer, onClick = onStop)
            }
        }
    }
}

@Composable
private fun RoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                onClick()
            },
            shape = CircleShape,
            color = container,
            contentColor = content,
            modifier = Modifier.size(size).semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(size * 0.45f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionRow(s: FocusSessionInfo, showDate: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (s.type) {
                    FocusType.STUDY -> Icons.AutoMirrored.Rounded.MenuBook
                    FocusType.RESEARCH -> Icons.Rounded.Science
                    FocusType.CODE -> Icons.Rounded.Code
                    FocusType.OTHER -> Icons.Rounded.Timer
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = s.type.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "• ${FocusMath.formatHm(s.minutes)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val subtitle = (if (showDate) "${SHORT_DATE.format(s.start)} at " else "") +
                HM.format(s.start) +
                if (s.note.isNotBlank()) ", ${s.note}" else ""
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.Rounded.EditNote,
            contentDescription = "Edit note",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}
