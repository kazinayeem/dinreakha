package com.example.tracker.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.container
import com.example.tracker.data.report.DailyReport
import com.example.tracker.data.report.ScoreWeights
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.TimeFormat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(
    date: LocalDate = LocalDate.now(Schedule.ZONE),
    onOpenCardStudio: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<DailyReport?>(null) }
    var weights by remember { mutableStateOf(ScoreWeights()) }
    var showWeightsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(date) {
        weights = c.reports.getScoreWeights()
        report = c.reports.computeDailyReport(date)
    }

    val dateTitle = remember(date) {
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH).format(date)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Daily Summary",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showWeightsDialog = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Score Weights")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenCardStudio("DAILY") },
                icon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                text = { Text("Export Share Card", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        val r = report
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Computing daily summary...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Date Header & Improvement Score Card
                item {
                    DailyScoreHeroCard(
                        report = r,
                        dateTitle = dateTitle,
                        onOpenWeights = { showWeightsDialog = true },
                    )
                }

                // 2. Sleep & Wake Rhythm
                item {
                    DailyRhythmCard(report = r)
                }

                // 3. Deep Focus Sessions
                item {
                    DailyFocusCard(report = r)
                }

                // 4. Habits & Prayers Grid
                item {
                    DailyHabitsPrayersRow(report = r)
                }

                // 5. Screen Time & App Limits
                item {
                    DailyScreenTimeCard(report = r)
                }

                // 6. Academics & Tasks (Class Notes & Deadlines)
                if (r.classNotesCaptured > 0 || r.deadlinesDone > 0) {
                    item {
                        DailyAcademicsCard(report = r)
                    }
                }

                // 7. Journal & Mood (Private)
                if (r.mood != null || r.journalNote.isNotBlank()) {
                    item {
                        DailyJournalCard(report = r)
                    }
                }
            }
        }
    }

    if (showWeightsDialog) {
        ScoreWeightsEditorDialog(
            currentWeights = weights,
            onDismiss = { showWeightsDialog = false },
            onSave = { updated ->
                scope.launch {
                    c.reports.setScoreWeights(updated)
                    weights = updated
                    report = c.reports.computeDailyReport(date)
                    showWeightsDialog = false
                }
            },
        )
    }
}

@Composable
private fun DailyScoreHeroCard(
    report: DailyReport,
    dateTitle: String,
    onOpenWeights: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dateTitle,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Edit Weights",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenWeights() },
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Score Circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                report.improvementScore >= 85 -> Color(0xFF10B981).copy(alpha = 0.2f)
                                report.improvementScore >= 70 -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color(0xFFF2A93B).copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${report.improvementScore}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                            color = when {
                                report.improvementScore >= 85 -> Color(0xFF10B981)
                                report.improvementScore >= 70 -> MaterialTheme.colorScheme.primary
                                else -> Color(0xFFF2A93B)
                            },
                        )
                        Text(
                            text = "/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val statusTitle = when {
                        report.improvementScore >= 90 -> "Mastery Day 🌟"
                        report.improvementScore >= 80 -> "High Momentum ⚡"
                        report.improvementScore >= 70 -> "Solid Consistency 👍"
                        else -> "Progress in Motion 🔄"
                    }
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (report.wokeOnTime) "Woke on time • Disciplined morning" else "Late by ${report.wakeLateMinutes}m • Room to recover tomorrow",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyRhythmCard(report: DailyReport) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sleep & wake rhythm",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Wake Tile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WbSunny, null, tint = Color(0xFFF2A93B), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Wake Target", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = TimeFormat.format(report.wakeTarget),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = if (report.wokeOnTime) "On time ✓" else "Late by ${report.wakeLateMinutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (report.wokeOnTime) Color(0xFF10B981) else Color(0xFFE53935),
                        )
                    }
                }

                // Sleep Tile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NightsStay, null, tint = Color(0xFF6366F1), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bedtime", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (report.actualBed != null) TimeFormat.format(report.actualBed) else TimeFormat.format(report.bedTarget),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "${"%.1f".format(report.hoursSlept)}h slept",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyFocusCard(report: DailyReport) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Deep work & focus",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val hours = report.focusMinutes / 60
                val mins = report.focusMinutes % 60
                Text(
                    text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))

            val fraction = (report.focusMinutes.toFloat() / 90f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Study: ${report.studyMinutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Research: ${report.researchMinutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Code: ${report.codeMinutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DailyHabitsPrayersRow(report: DailyReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Habits
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Habits Done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${report.habitPercent}%",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF10B981),
                )
                Text(
                    text = "${report.habitsCompleted} of ${report.habitsTotal} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Prayers
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Daily Prayers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${report.prayersCompleted}/5",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFD97706),
                )
                Text(
                    text = if (report.prayersCompleted == 5) "Full consistency ✓" else "Keep offering",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DailyScreenTimeCard(report: DailyReport) {
    val sHours = report.screenTimeMinutes / 60
    val sMins = report.screenTimeMinutes % 60
    val sVal = if (sHours > 0) "${sHours}h ${sMins}m" else "${sMins}m"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Screen time & limits",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = sVal,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            Spacer(Modifier.height(8.dp))

            val diffText = if (report.screenTimeDiffMinutes <= 0) {
                "↓ ${-report.screenTimeDiffMinutes}m below your 7-day average"
            } else {
                "↑ +${report.screenTimeDiffMinutes}m above your 7-day average"
            }
            Text(
                text = diffText,
                style = MaterialTheme.typography.bodySmall,
                color = if (report.screenTimeDiffMinutes <= 0) Color(0xFF10B981) else Color(0xFFF2A93B),
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("FB: ${report.facebookMinutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("YouTube: ${report.youtubeMinutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Reels: ${report.reelsCount} watched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DailyAcademicsCard(report: DailyReport) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Academics & tasks",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (report.classNotesCaptured > 0) {
                    Text("📷 ${report.classNotesCaptured} class notes captured", style = MaterialTheme.typography.bodyMedium)
                }
                if (report.deadlinesDone > 0) {
                    Text("📌 ${report.deadlinesDone} events scheduled", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DailyJournalCard(report: DailyReport) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Journal & mood (private)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (report.mood != null) {
                    val moodEmojis = listOf("😞", "😕", "😐", "🙂", "😄")
                    val moodEmoji = moodEmojis.getOrElse(report.mood - 1) { "🙂" }
                    Text(
                        text = "$moodEmoji ${report.mood}/5",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            if (report.journalNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\"${report.journalNote}\"",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ScoreWeightsEditorDialog(
    currentWeights: ScoreWeights,
    onDismiss: () -> Unit,
    onSave: (ScoreWeights) -> Unit,
) {
    var wake by remember { mutableStateOf(currentWeights.wake.toFloat()) }
    var sleep by remember { mutableStateOf(currentWeights.sleep.toFloat()) }
    var focus by remember { mutableStateOf(currentWeights.focus.toFloat()) }
    var habits by remember { mutableStateOf(currentWeights.habits.toFloat()) }
    var prayers by remember { mutableStateOf(currentWeights.prayers.toFloat()) }
    var screen by remember { mutableStateOf(currentWeights.screenTime.toFloat()) }

    val total = (wake + sleep + focus + habits + prayers + screen).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Improvement Score Weights") },
        text = {
            Column {
                Text("Customize how heavily each pillar influences your 0–100 daily score (Total: $total pts):", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))

                WeightSliderItem(label = "Wake on time", value = wake.toInt()) { wake = it.toFloat() }
                WeightSliderItem(label = "Sleep before target", value = sleep.toInt()) { sleep = it.toFloat() }
                WeightSliderItem(label = "Deep focus minutes", value = focus.toInt()) { focus = it.toFloat() }
                WeightSliderItem(label = "Daily habits completed", value = habits.toInt()) { habits = it.toFloat() }
                WeightSliderItem(label = "Daily prayers", value = prayers.toInt()) { prayers = it.toFloat() }
                WeightSliderItem(label = "Screen time limit", value = screen.toInt()) { screen = it.toFloat() }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ScoreWeights(
                            wake = wake.toInt(),
                            sleep = sleep.toInt(),
                            focus = focus.toInt(),
                            habits = habits.toInt(),
                            prayers = prayers.toInt(),
                            screenTime = screen.toInt(),
                        )
                    )
                }
            ) {
                Text("Save Weights")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun WeightSliderItem(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text("$value pts", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..40f,
            steps = 39,
        )
    }
}

