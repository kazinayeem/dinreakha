package com.example.tracker.ui.review

import androidx.compose.material.icons.rounded.AutoAwesome
import com.example.tracker.ui.theme.readable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import com.example.tracker.ui.i18n.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.db.WeeklyReview
import com.example.tracker.data.schedule.Habits
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.ui.components.LocalFeedback
import com.example.tracker.ui.components.SHORT_DATE
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(
    initialWeek: Int,
    onOpenWeeklyReport: (Int) -> Unit = {},
) {
    val ctx = LocalContext.current
    val c = ctx.container
    var week by rememberSaveable { mutableIntStateOf(initialWeek.coerceIn(1, PlanCalendar.WEEK_COUNT)) }
    val plan by remember { c.habits.observePlan() }.collectAsStateWithLifecycle(emptyMap())
    val saved by remember(week) { c.habits.observeReview(week) }.collectAsStateWithLifecycle(null)
    val feedback = LocalFeedback.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var wentWell by remember(week) { mutableStateOf("") }
    var gotInWay by remember(week) { mutableStateOf("") }
    var nextChange by remember(week) { mutableStateOf("") }
    var justSaved by remember(week) { mutableStateOf(false) }

    LaunchedEffect(saved) {
        saved?.let {
            wentWell = it.wentWell
            gotInWay = it.gotInWay
            nextChange = it.nextChange
        }
    }

    val dates = PlanCalendar.weekDates(week)
    val wokeDays = dates.count { plan[it]?.doneKeys?.contains(Habits.WOKE_ON_TIME) == true }
    val moods = dates.mapNotNull { plan[it]?.log?.mood }
    val avgMood = if (moods.isEmpty()) "–" else "%.1f / 5".format(moods.average())

    Column(modifier = Modifier.fillMaxSize()) {
        // Week Tabs
        PrimaryScrollableTabRow(
            selectedTabIndex = week - 1,
            edgePadding = 16.dp,
            divider = {},
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            (1..PlanCalendar.WEEK_COUNT).forEach { w ->
                val selected = week == w
                Tab(
                    selected = selected,
                    onClick = { week = w },
                    text = {
                        Text(
                            text = "Week $w",
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Week Header & Date Range
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Week $week Reflection",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${SHORT_DATE.format(dates.first())} – ${SHORT_DATE.format(dates.last())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (saved != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Recorded",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            // Weekly Report & Story Card Banner
            item {
                Surface(
                    onClick = { onOpenWeeklyReport(week) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.AutoAwesome, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Weekly Progress & Story Card",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "See charts, trends vs last week & export image",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = "View",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Stat Cards Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ReviewStatCard(
                        icon = Icons.Rounded.Alarm,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Woke on Time",
                        value = "$wokeDays / ${dates.size} days",
                        modifier = Modifier.weight(1f),
                    )

                    ReviewStatCard(
                        icon = Icons.Rounded.Mood,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "Average mood",
                        value = avgMood,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Question 1: What went well?
            item {
                ReflectionCard(
                    icon = Icons.Rounded.Star,
                    iconTint = MaterialTheme.colorScheme.primary,
                    question = "What went well this week?",
                    hint = "Habits you sustained, deep focus blocks, or positive momentum.",
                    value = wentWell,
                    onValueChange = { wentWell = it },
                )
            }

            // Question 2: What got in the way?
            item {
                ReflectionCard(
                    icon = Icons.Rounded.Psychology,
                    iconTint = MaterialTheme.colorScheme.error,
                    question = "What got in the way?",
                    hint = "Distractions, low energy, poor sleep, or interruptions.",
                    value = gotInWay,
                    onValueChange = { gotInWay = it },
                )
            }

            // Question 3: One small change for next week
            item {
                ReflectionCard(
                    icon = Icons.Rounded.Flag,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    question = "One small change for next week",
                    hint = "E.g. put phone away 15 min earlier, prepare study desk at night.",
                    value = nextChange,
                    onValueChange = { nextChange = it },
                )
            }

            // Save Action
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        scope.launch {
                            c.habits.saveReview(
                                WeeklyReview(
                                    weekNo = week,
                                    wentWell = wentWell.trim(),
                                    gotInWay = gotInWay.trim(),
                                    nextChange = nextChange.trim(),
                                    createdAt = saved?.createdAt ?: System.currentTimeMillis(),
                                )
                            )
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            justSaved = true
                            feedback?.show("Week $week review saved")
                            delay(2000)
                            justSaved = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(
                        text = if (justSaved) "Review Saved ✓" else "Save Weekly Review",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStatCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint.readable(3f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun ReflectionCard(
    icon: ImageVector,
    iconTint: Color,
    question: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint.readable(3f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = question,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                placeholder = {
                    Text(
                        text = "Write your thoughts…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
            )
        }
    }
}
