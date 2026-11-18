package com.example.tracker.ui.report

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.container
import com.example.tracker.data.report.ThirtyDayReport
import com.example.tracker.ui.components.SHORT_DATE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThirtyDayReportScreen(
    onOpenCardStudio: (String) -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false,
    onOpenGrid: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val c = ctx.container
    var report by remember { mutableStateOf<ThirtyDayReport?>(null) }

    LaunchedEffect(Unit) {
        report = c.reports.computeThirtyDayReport()
    }

    Scaffold(
        topBar = {
            // Inside the Progress screen the container draws the title and back arrow.
            if (embedded) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onOpenGrid) { Text("Day-by-day grid", fontWeight = FontWeight.Bold) }
                }
            } else CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Your 30 days",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = com.example.tracker.ui.i18n.tr("Back"))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenCardStudio("THIRTY_DAY") },
                icon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                text = { Text("Share card", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        val r = report
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Analyzing 30-day journey...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Transformation Hero Card
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Before & after (30 days)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${SHORT_DATE.format(r.planStart)} → ${SHORT_DATE.format(r.planEnd)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("Day 1", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = "${r.day1Score} pts",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Text("→", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Day 30", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = "${r.day30Score} pts",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = com.example.tracker.ui.theme.StatusColors.good.readable(),
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            val sign = if (r.scoreGrowthPercent >= 0) "+" else ""
                            Text(
                                text = "Score $sign${r.scoreGrowthPercent} points",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = com.example.tracker.ui.theme.StatusColors.good.readable(),
                            )
                        }
                    }
                }

                // 2. Side by Side Metrics Comparison
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Core pillar comparison",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(Modifier.height(12.dp))

                            ComparisonRow(
                                title = "⚡ Deep Focus / Day",
                                day1 = "${r.day1FocusMinutes}m",
                                day30 = "${r.day30FocusMinutes}m",
                            )
                            Spacer(Modifier.height(10.dp))
                            ComparisonRow(
                                title = "✅ Habit Consistency",
                                day1 = "${r.day1HabitPercent}%",
                                day30 = "${r.day30HabitPercent}%",
                            )
                            Spacer(Modifier.height(10.dp))
                            ComparisonRow(
                                title = "📱 Screen Time / Day",
                                day1 = "${r.day1ScreenTimeMinutes}m",
                                day30 = "${r.day30ScreenTimeMinutes}m",
                            )
                        }
                    }
                }

                // 3. Totals Milestones Card
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Total plan achievements",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Focus logged", style = MaterialTheme.typography.labelSmall)
                                        Text("${"%.1f".format(r.totalFocusHours)}h", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Prayers offered", style = MaterialTheme.typography.labelSmall)
                                        Text("${r.totalPrayersCompleted}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = com.example.tracker.ui.theme.StatusColors.warn.readable())
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Class notes", style = MaterialTheme.typography.labelSmall)
                                        Text("${r.totalNotesCaptured}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = com.example.tracker.ui.theme.StatusColors.good.readable())
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            r.milestones.forEach { m ->
                                Text("• $m", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }

                // 4. All Earned Badges
                if (r.allEarnedBadges.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "30-Day badges unlocked",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Spacer(Modifier.height(12.dp))

                                r.allEarnedBadges.chunked(2).forEach { rowBadges ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        rowBadges.forEach { b ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(b.iconEmoji, fontSize = 20.sp)
                                                    Spacer(Modifier.width(8.dp))
                                                    Column {
                                                        Text(b.title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                        Text(b.category, style = MaterialTheme.typography.labelSmall, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                        if (rowBadges.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(title: String, day1: String, day30: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(day1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text("→", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(day30, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = com.example.tracker.ui.theme.StatusColors.good.readable())
        }
    }
}
