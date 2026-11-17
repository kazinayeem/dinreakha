package com.example.tracker.ui.blocker

import com.example.tracker.ui.theme.readable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.db.AppUsageEntity
import com.example.tracker.data.db.ReelsUsageEntity
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageStatsScreen(
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val today = remember { LocalDate.now(Schedule.ZONE) }

    val usageList by remember(today) { c.blocker.observeAppUsage(today) }.collectAsStateWithLifecycle(emptyList())
    val blockCountToday by remember(today) { c.blocker.observeBlockCountToday(today) }.collectAsStateWithLifecycle(0)
    val reelsList by remember(today) { c.blocker.observeReelsUsage(today) }.collectAsStateWithLifecycle(emptyList())
    val totalReelsCount by remember(today) { c.blocker.observeTotalReelsCount(today) }.collectAsStateWithLifecycle(0)

    var appLabels by remember { mutableStateOf<Map<String, Pair<String, Drawable?>>>(emptyMap()) }

    // Sync from UsageStatsManager once upon opening
    LaunchedEffect(Unit) {
        c.blocker.syncUsageFromSystem(today)
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val map = mutableMapOf<String, Pair<String, Drawable?>>()
            usageList.forEach { u ->
                runCatching {
                    val info = pm.getApplicationInfo(u.packageName, 0)
                    val label = pm.getApplicationLabel(info).toString()
                    val icon = pm.getApplicationIcon(info)
                    map[u.packageName] = label to icon
                }
            }
            appLabels = map
        }
    }

    val totalForegroundMs = usageList.sumOf { it.foregroundMs }
    val totalHours = totalForegroundMs / (1000 * 60 * 60)
    val totalMins = (totalForegroundMs / (1000 * 60)) % 60
    val maxUsageMs = usageList.maxOfOrNull { it.foregroundMs }?.coerceAtLeast(1L) ?: 1L

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("App Usage & Screen Time", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Metrics Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Total Screen Time
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Screen Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (totalHours > 0) "${totalHours}h ${totalMins}m" else "${totalMins}m",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }

                    // Blocks Enforced
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE53935).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Shield, null, tint = Color(0xFFE53935).readable(), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Distractions Blocked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "$blockCountToday times",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE53935).readable(),
                            )
                        }
                    }
                }
            }

            // Shorts & Reels Stats Card
            item {
                ReelsStatsCard(
                    totalCount = totalReelsCount,
                    reelsList = reelsList,
                )
            }

            // Per App Breakdown
            item {
                Text(
                    text = "Today's app activity",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (usageList.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No app activity recorded yet today.\nUsage updates as apps are opened.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                items(usageList) { usage ->
                    val (label, icon) = appLabels[usage.packageName] ?: (usage.packageName.substringAfterLast('.') to null)
                    val mins = (usage.foregroundMs / (1000 * 60)).toInt()
                    val fraction = (usage.foregroundMs.toFloat() / maxUsageMs.toFloat()).coerceIn(0f, 1f)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (icon != null) {
                                    val bmp = remember(icon) { icon.toBitmap(64, 64) }
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${usage.opens} opens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                drawStopIndicator = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReelsStatsCard(
    totalCount: Int,
    reelsList: List<ReelsUsageEntity>,
) {
    val instaCount = reelsList.find { it.platform.equals("instagram", ignoreCase = true) }?.count ?: 0
    val ytCount = reelsList.find { it.platform.equals("youtube", ignoreCase = true) }?.count ?: 0
    val fbCount = reelsList.find { it.platform.equals("facebook", ignoreCase = true) }?.count ?: 0

    val estMinutes = (totalCount * 30) / 60

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE1306C).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color(0xFFE1306C).readable(), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Shorts & Reels Watched", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Instagram • YouTube • Facebook", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    text = "$totalCount",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = if (totalCount > 50) Color(0xFFE53935).readable() else MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (totalCount == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4CAF50).readable(), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "No reels scrolled today. Focus is intact!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = "Estimated doomscroll time: ~$estMinutes min (avg. 30s per reel)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))

                ReelsPlatformBar(name = "Instagram Reels", count = instaCount, total = totalCount, color = Color(0xFFE1306C).readable())
                Spacer(Modifier.height(8.dp))
                ReelsPlatformBar(name = "YouTube Shorts", count = ytCount, total = totalCount, color = Color(0xFFFF0000).readable())
                Spacer(Modifier.height(8.dp))
                ReelsPlatformBar(name = "Facebook Reels", count = fbCount, total = totalCount, color = Color(0xFF1877F2).readable())
            }
        }
    }
}

@Composable
private fun ReelsPlatformBar(
    name: String,
    count: Int,
    total: Int,
    color: Color,
) {
    val fraction = if (total > 0) (count.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
            Text(
                text = "$count reels ($percent%)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    }
}
