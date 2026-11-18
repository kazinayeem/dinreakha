package com.example.tracker.ui.stats

import com.example.tracker.ui.theme.readable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.repo.DayState
import com.example.tracker.data.schedule.Habits
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.EmptyState
import com.example.tracker.ui.components.Pill
import com.example.tracker.ui.components.habitIcon
import com.example.tracker.ui.today.currentDateFlow
import java.time.Duration
import java.time.LocalDate

object HabitStats {
    fun hours(
        days: Map<LocalDate, DayState>,
        sessions: List<com.example.tracker.data.focus.FocusSessionInfo>,
        week: Int,
        type: Schedule.BlockType,
    ): Double {
        val dates = PlanCalendar.weekDates(week).toSet()
        val ft = when (type) {
            Schedule.BlockType.STUDY -> com.example.tracker.data.focus.FocusType.STUDY
            Schedule.BlockType.RESEARCH -> com.example.tracker.data.focus.FocusType.RESEARCH
            Schedule.BlockType.CODE -> com.example.tracker.data.focus.FocusType.CODE
        }
        val real = sessions.filter { it.type == ft && it.start.toLocalDate() in dates }.sumOf { it.minutes }
        return if (real > 0) real / 60.0 else blockHours(days, week, type)
    }

    fun currentStreak(days: Map<LocalDate, DayState>, today: LocalDate): Int {
        var d = if (days[today]?.doneKeys?.contains(Habits.WOKE_ON_TIME) == true) today else today.minusDays(1)
        var n = 0
        while (days[d]?.doneKeys?.contains(Habits.WOKE_ON_TIME) == true) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun bestStreak(days: Map<LocalDate, DayState>): Int {
        var best = 0
        var cur = 0
        days.keys.sorted().forEach { d ->
            if (days[d]?.doneKeys?.contains(Habits.WOKE_ON_TIME) == true) {
                cur++
                best = maxOf(best, cur)
            } else {
                cur = 0
            }
        }
        return best
    }

    fun blockHours(days: Map<LocalDate, DayState>, week: Int, type: Schedule.BlockType): Double =
        PlanCalendar.weekDates(week).sumOf { date ->
            val st = days[date] ?: return@sumOf 0.0
            if (type.habitKey !in st.doneKeys) return@sumOf 0.0
            val block = PlanCalendar.focusBlocks(date).firstOrNull { it.block.type == type }?.block
            block?.let { Duration.between(it.start, it.end).toMinutes() / 60.0 } ?: 1.0
        }
}

@Composable
fun StatsScreen(onOpenPlan: () -> Unit = {}) {
    val c = LocalContext.current.container
    val days by remember { c.habits.observePlan() }.collectAsStateWithLifecycle(emptyMap())
    val today by remember { currentDateFlow() }.collectAsStateWithLifecycle(LocalDate.now(Schedule.ZONE))
    val sessions by c.focus.sessions.collectAsStateWithLifecycle(emptyList())

    val elapsed = (1..PlanCalendar.planDays).map(PlanCalendar::dateOfDay).filter { !it.isAfter(today) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Milestone link header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Habit & Focus Analytics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Calculated from Day 1 to current milestone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FilledTonalButton(
                    onClick = onOpenPlan,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("30-day plan", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Streak Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    title = "Wake-up streak",
                    value = "${HabitStats.currentStreak(days, today)} days",
                    icon = Icons.Rounded.LocalFireDepartment,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Best streak",
                    value = "${HabitStats.bestStreak(days)} days",
                    icon = Icons.Rounded.EmojiEvents,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Days in Plan",
                    value = "${elapsed.size}/${PlanCalendar.planDays}",
                    icon = Icons.Rounded.CalendarMonth,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Habits Completion Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.TaskAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Habit success rates",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    if (elapsed.isEmpty()) {
                        EmptyState(Icons.Rounded.HourglassTop, "Starts on Day 1", "")
                    } else {
                        val keys = Habits.visibleBuiltIns()

                        keys.forEachIndexed { i, key ->
                            val done = elapsed.count { days[it]?.doneKeys?.contains(key) == true }
                            IconBar(habitIcon(key), Habits.label(key), done.toFloat() / elapsed.size)
                            if (i < keys.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
        }

        // Focus hours per week Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Science,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Focus hours per week",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    val weeks = (1..PlanCalendar.WEEK_COUNT).map { w ->
                        Triple(
                            w,
                            HabitStats.hours(days, sessions, w, Schedule.BlockType.RESEARCH),
                            HabitStats.hours(days, sessions, w, Schedule.BlockType.CODE),
                        )
                    }
                    WeekBars(weeks)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint.readable(3f),
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconBar(icon: ImageVector, label: String, frac: Float) {
    val p by animateFloatAsState(frac.coerceIn(0f, 1f), label = "bar")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label ${(frac * 100).toInt()} percent"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier
                .weight(0.44f)
                .height(7.dp)
                .clip(RoundedCornerShape(50)),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
        Text(
            text = "${(frac * 100).toInt()}%",
            modifier = Modifier.weight(0.18f),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekBars(weeks: List<Triple<Int, Double, Double>>) {
    val max = weeks.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1.0)
    val research = MaterialTheme.colorScheme.primary
    val code = MaterialTheme.colorScheme.tertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics {
                contentDescription = weeks.joinToString("; ") { (w, r, c) ->
                    "Week $w: research %.1f hours, code %.1f hours".format(r, c)
                }
            },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        weeks.forEach { (w, r, c) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.height(110.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height((110 * (r / max)).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(research),
                    )
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height((110 * (c / max)).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(code),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "W$w",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Pill("Research", research.copy(alpha = 0.2f), MaterialTheme.colorScheme.onSurface)
            Pill("Code", code.copy(alpha = 0.2f), MaterialTheme.colorScheme.onSurface)
        }
    }
}
