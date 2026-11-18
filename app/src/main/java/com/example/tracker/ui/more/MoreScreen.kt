package com.example.tracker.ui.more

import com.example.tracker.ui.theme.readable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.stats.HabitStats
import com.example.tracker.ui.today.currentDateFlow
import java.time.LocalDate

private data class Tile(val icon: ImageVector, val tint: Color, val title: String, val value: String, val route: String)

/**
 * More: everything that isn't a tab. Plan card first (the thing you check most), then a 2-column grid of
 * big tiles — icon, name, one live number — then settings as a plain list. Short words, large type.
 */
@Composable
fun MoreScreen(nav: (String) -> Unit) {
    val c = LocalContext.current.container
    val today by remember { currentDateFlow() }.collectAsStateWithLifecycle(LocalDate.now(Schedule.ZONE))
    val days by remember { c.habits.observePlan() }.collectAsStateWithLifecycle(emptyMap())
    val blocks by remember(today) { c.blocker.observeBlockCountToday(today) }.collectAsStateWithLifecycle(0)
    val reels by remember(today) { c.blocker.observeTotalReelsCount(today) }.collectAsStateWithLifecycle(0)
    val sleep by remember { c.db.sleepDao().observeAll() }.collectAsStateWithLifecycle(emptyList())

    val dayNumber = PlanCalendar.dayNumber(today)
    val streak = HabitStats.currentStreak(days, today)
    val lastNight = sleep.firstOrNull()?.takeIf { !it.night.isBefore(today.minusDays(1)) }

    val cs = MaterialTheme.colorScheme
    val tiles = listOf(
        Tile(Icons.Rounded.Insights, com.example.tracker.ui.theme.StatusColors.warn, "Progress", "$streak-day streak", "progress?tab=DAY"),
        Tile(Icons.Rounded.Bedtime, Color(0xFF818CF8), "Sleep & mood",
            lastNight?.let { "${it.minutes / 60}h ${it.minutes % 60}m slept" } ?: "Log last night", "sleep?log=false"),
        Tile(Icons.Rounded.Shield, com.example.tracker.ui.theme.StatusColors.bad, "App limits", "$reels reels, $blocks blocks", "blocker"),
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Me", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)) }

        // Plan progress — the one number you check most
        item {
            Surface(onClick = { nav("progress?tab=PLAN") }, shape = RoundedCornerShape(22.dp), color = cs.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    dayNumber < 1 -> "Starts in ${1 - dayNumber}d"
                                    dayNumber > PlanCalendar.planDays -> "Plan complete"
                                    else -> "Day $dayNumber / ${PlanCalendar.planDays}"
                                },
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = cs.onPrimaryContainer,
                            )
                            Text("30-day plan", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = 0.75f))
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(cs.surface.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, tint = com.example.tracker.ui.theme.StatusColors.warn.readable(), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("$streak", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = cs.onSurface)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (dayNumber.toFloat() / PlanCalendar.planDays).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = cs.primary,
                        trackColor = cs.surface.copy(alpha = 0.6f),
                        drawStopIndicator = {},
                    )
                }
            }
        }

        // Feature tiles, two per row
        tiles.chunked(2).forEach { pair ->
            item(key = pair.first().route) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { t -> FeatureTile(t, Modifier.weight(1f)) { nav(t.route) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // Settings
        item {
            Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow) {
                Column {
                    val rows = listOf(
                        // One row per destination: Settings itself is a short list of pages.
                        Triple(Icons.Rounded.Settings, "Settings", "settings"),
                        Triple(Icons.Rounded.Info, "About", "about"),
                    )
                    rows.forEachIndexed { i, (icon, title, route) ->
                        SettingsRow(icon, title) { nav(route) }
                        if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(start = 60.dp), color = cs.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(16.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(t.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(t.icon, null, tint = t.tint.readable(3f), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(t.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
