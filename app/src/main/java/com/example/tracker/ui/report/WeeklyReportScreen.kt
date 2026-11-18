package com.example.tracker.ui.report

import com.example.tracker.ui.theme.readable
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
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.container
import com.example.tracker.data.report.DailyReport
import com.example.tracker.data.report.WeeklyReport
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.SHORT_DATE
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    initialWeek: Int = PlanCalendar.weekNumber(LocalDate.now(Schedule.ZONE)),
    onOpenCardStudio: (String, Int) -> Unit,
    onOpenThirtyDayReport: () -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false,
    onWriteReview: (Int) -> Unit = {},
) {
    val ctx = LocalContext.current
    val c = ctx.container
    var selectedWeek by remember { mutableIntStateOf(initialWeek.coerceIn(1, PlanCalendar.WEEK_COUNT)) }
    var report by remember { mutableStateOf<WeeklyReport?>(null) }

    LaunchedEffect(selectedWeek) {
        report = c.reports.computeWeeklyReport(selectedWeek)
    }

    Scaffold(
        topBar = {
            // Inside the Progress screen the container draws the title and back arrow.
            if (embedded) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onWriteReview(selectedWeek) }) {
                        Text("Write this week's review", fontWeight = FontWeight.Bold)
                    }
                }
            } else CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "This week",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenThirtyDayReport) {
                        Text("30-day plan", fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenCardStudio("WEEKLY", selectedWeek) },
                icon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                text = { Text("Share card", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Week Tabs (1..5)
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedWeek - 1,
                edgePadding = 16.dp,
                divider = {},
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                (1..PlanCalendar.WEEK_COUNT).forEach { w ->
                    val selected = selectedWeek == w
                    Tab(
                        selected = selected,
                        onClick = { selectedWeek = w },
                        text = {
                            Text(
                                text = "Week $w",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            val r = report
            if (r == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Calculating weekly statistics & trends...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Week Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Week $selectedWeek Overview",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "${SHORT_DATE.format(r.startDate)} – ${SHORT_DATE.format(r.endDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "Score: ${r.avgImprovementScore}/100",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    // 2. Week vs Last Week Metrics Carousel/Grid
                    item {
                        WeekComparisonCards(report = r)
                    }

                    // 3. Chart 1: Focus Hours per Day (7 Days)
                    item {
                        WeeklyFocusChartCard(report = r)
                    }

                    // 4. Chart 2: Bedtime & Sleep Trend
                    item {
                        WeeklySleepTrendCard(report = r)
                    }

                    // 5. Chart 3: Habit Consistency Heatmap
                    item {
                        WeeklyHabitMatrixCard(report = r)
                    }

                    // 6. Highlights & Streaks
                    item {
                        WeeklyStreaksCard(report = r)
                    }

                    // 7. Plain Language Constructive Insights
                    if (r.insights.isNotEmpty()) {
                        item {
                            WeeklyInsightsCard(report = r)
                        }
                    }

                    // 8. Earned Badges
                    if (r.earnedBadges.isNotEmpty()) {
                        item {
                            WeeklyBadgesCard(report = r)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekComparisonCards(report: WeeklyReport) {
    val fHours = report.thisWeekFocusMinutes / 60
    val fMins = report.thisWeekFocusMinutes % 60
    val focusDisplay = if (fHours > 0) "${fHours}h ${fMins}m" else "${fMins}m"
    val fArrow = if (report.focusDiffPercent >= 0) "↑" else "↓"

    val hArrow = if (report.habitDiffPercent >= 0) "↑" else "↓"
    val sArrow = if (report.screenTimeDiffPercent <= 0) "↓" else "↑"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Focus Tile
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Focus time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(focusDisplay, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "$fArrow${kotlin.math.abs(report.focusDiffPercent)}% vs last week",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (report.focusDiffPercent >= 0) Color(0xFF10B981).readable() else Color(0xFFF2A93B).readable(),
                    )
                }
            }

            // Habits Tile
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Habit consistency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("${report.thisWeekHabitPercent}%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "$hArrow${kotlin.math.abs(report.habitDiffPercent)}% vs last week",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (report.habitDiffPercent >= 0) Color(0xFF10B981).readable() else Color(0xFFF2A93B).readable(),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Prayers Tile
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Prayers offered", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("${report.thisWeekPrayersCount}/35", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = if (report.thisWeekPrayersCount >= 30) "Exceptional consistency ✓" else "Regular rhythm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Screen Time Tile
            val dailyScreenAvgMin = report.thisWeekScreenTimeMinutes / 7
            val sh = dailyScreenAvgMin / 60
            val sm = dailyScreenAvgMin % 60
            val sDisplay = if (sh > 0) "${sh}h ${sm}m" else "${sm}m"

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Screen time per day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("$sDisplay/day", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "$sArrow${kotlin.math.abs(report.screenTimeDiffPercent)}% vs last week",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (report.screenTimeDiffPercent <= 0) Color(0xFF10B981).readable() else Color(0xFFF2A93B).readable(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyFocusChartCard(report: WeeklyReport) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Daily focus hours (7 days)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))

            val maxMinutes = report.dailyReports.maxOfOrNull { it.focusMinutes }?.coerceAtLeast(120) ?: 120

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                report.dailyReports.forEach { day ->
                    val fraction = (day.focusMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.06f, 1f)
                    val barHeight = 80.dp * fraction

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (day.focusMinutes >= 60) "${day.focusMinutes / 60}h" else "${day.focusMinutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (day.focusMinutes >= 90) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = day.date.dayOfWeek.name.take(2),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklySleepTrendCard(report: WeeklyReport) {
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
                    text = "Bedtime & sleep rhythm",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Avg: ${"%.1f".format(report.thisWeekAvgSleepHours)}h / night",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF6366F1).readable(),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                report.dailyReports.forEach { day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        val hours = day.hoursSlept
                        val isHealthy = hours in 7.0..9.0

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isHealthy) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF2A93B).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${hours.toInt()}h",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                fontSize = 12.sp,
                                color = if (isHealthy) Color(0xFF10B981).readable() else Color(0xFFF2A93B).readable(),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = day.date.dayOfWeek.name.take(2),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyHabitMatrixCard(report: WeeklyReport) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Daily habit completion heatmap",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                report.dailyReports.forEach { day ->
                    val pct = day.habitPercent
                    val tint = when {
                        pct >= 85 -> Color(0xFF10B981)
                        pct >= 70 -> Color(0xFF0D9488)
                        pct >= 50 -> Color(0xFFF2A93B)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                fontSize = 12.sp,
                                color = if (pct >= 50) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = day.date.dayOfWeek.name.take(2),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyStreaksCard(report: WeeklyReport) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Highlights & streaks",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Best Day
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, tint = Color(0xFFF2A93B).readable(), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Best day", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = report.bestDay?.dayOfWeek?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "–",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "Score: ${report.bestDayScore}/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Streaks
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color(0xFFE53935).readable(), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Longest Streaks", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Woke on time ${report.wakeStreak}d, studied ${report.studyStreak}d",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "${report.prayerStreak}d 5-Prayer streak",
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
private fun WeeklyInsightsCard(report: WeeklyReport) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.TrendingUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Auto insights (plain words)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            report.insights.forEach { ins ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = ins,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyBadgesCard(report: WeeklyReport) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Achievements earned",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                report.earnedBadges.forEach { badge ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(badge.iconEmoji, fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = badge.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        }
    }
}
