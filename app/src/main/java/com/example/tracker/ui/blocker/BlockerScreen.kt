package com.example.tracker.ui.blocker

import com.example.tracker.ui.theme.readable
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.blocker.TrackerAccessibilityService
import com.example.tracker.container
import com.example.tracker.data.db.AppUsageEntity
import com.example.tracker.data.db.BlockRuleEntity
import com.example.tracker.data.db.BlockRuleType
import com.example.tracker.data.db.ReelsUsageEntity
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.TimeFormat
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerScreen(
    onOpenRule: (Long?) -> Unit,
    onOpenStats: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now(Schedule.ZONE) }

    val rules by remember { c.blocker.observeRules() }.collectAsStateWithLifecycle(emptyList())
    val todayUsage by remember(today) { c.blocker.observeAppUsage(today) }.collectAsStateWithLifecycle(emptyList())
    val blockCountToday by remember(today) { c.blocker.observeBlockCountToday(today) }.collectAsStateWithLifecycle(0)
    val reelsList by remember(today) { c.blocker.observeReelsUsage(today) }.collectAsStateWithLifecycle(emptyList())
    val totalReelsCount by remember(today) { c.blocker.observeTotalReelsCount(today) }.collectAsStateWithLifecycle(0)
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val isAccessibilityRunning by TrackerAccessibilityService.isRunning.collectAsStateWithLifecycle()

    var showEmergencyDialog by remember { mutableStateOf(false) }
    var isEmergencyActive by remember { mutableStateOf(false) }
    var remainingEmergencyMin by remember { mutableStateOf(0) }

    // Check permissions
    var hasUsageAccess by remember { mutableStateOf(checkUsageAccess(ctx)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var hasBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptimization(ctx)) }

    LaunchedEffect(Unit) {
        hasUsageAccess = checkUsageAccess(ctx)
        hasOverlayPermission = Settings.canDrawOverlays(ctx)
        hasBatteryOptIgnored = checkBatteryOptimization(ctx)
        isEmergencyActive = c.blocker.isEmergencyUnlockActive(today)
        remainingEmergencyMin = c.blocker.getRemainingEmergencyUnlockMinutes(today)
    }

    val missingPermissionsCount = listOf(isAccessibilityRunning, hasUsageAccess, hasOverlayPermission).count { !it }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("App limits", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Rounded.BarChart, contentDescription = "Usage Stats")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenRule(null) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Block Rule")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Permissions Banner (if any missing)
            if (missingPermissionsCount > 0) {
                item {
                    PermissionsGuideCard(
                        isAccessibilityRunning = isAccessibilityRunning,
                        hasUsageAccess = hasUsageAccess,
                        hasOverlayPermission = hasOverlayPermission,
                        onOpenAccessibility = {
                            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        onOpenUsage = {
                            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        onOpenOverlay = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                        },
                    )
                }
            }

            // Emergency Unlock Active Status
            if (isEmergencyActive) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF2A93B).copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = Color(0xFFF2A93B).readable(), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Emergency Unlock Active", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFF2A93B).readable())
                                Text("All app rules are bypassed for ~$remainingEmergencyMin more min.", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF2A93B).readable())
                            }
                        }
                    }
                }
            }

            // Overview Glance Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Active Rules", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${rules.count { it.enabled }} of ${rules.size}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Blocks Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "$blockCountToday times",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (blockCountToday > 0) Color(0xFFE53935).readable() else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Shorts & Reels Counter Card (FB, YT Shorts, Insta Reels)
            item {
                ReelsCounterCard(
                    totalCount = totalReelsCount,
                    reelsList = reelsList,
                    trackingEnabled = settings?.reelsTrackingEnabled ?: true,
                    dailyLimit = settings?.reelsDailyLimit ?: 0,
                    onToggleTracking = { enabled ->
                        scope.launch {
                            c.settings.setReelsTrackingEnabled(enabled)
                        }
                    },
                    onSetDailyLimit = { limit ->
                        scope.launch {
                            c.settings.setReelsDailyLimit(limit)
                        }
                    },
                    onResetToday = {
                        scope.launch {
                            c.blocker.resetReelsUsage(today)
                            Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Reels count reset for today"), Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }

            // Side counter overlay toggle
            item {
                val rows by remember { c.db.settingsDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
                val overlayOn = rows.firstOrNull { it.key == com.example.tracker.blocker.UsageOverlay.KEY }?.value != "0"
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        com.example.tracker.ui.components.SwitchRow(
                            title = "Side counter",
                            subtitle = "Small time + reels pill in Facebook, Instagram, YouTube",
                            checked = overlayOn,
                        ) { on ->
                            scope.launch { c.db.settingsDao().put(com.example.tracker.data.db.Setting(com.example.tracker.blocker.UsageOverlay.KEY, if (on) "1" else "0")) }
                        }
                    }
                }
            }

            // Limited Apps Progress Carousel
            val limitRules = rules.filter { it.enabled && it.type in listOf(BlockRuleType.DAILY_LIMIT, BlockRuleType.APP_GROUP) && it.limitMinutes > 0 }
            if (limitRules.isNotEmpty()) {
                item {
                    Text(
                        text = "Today's app limits",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(limitRules) { rule ->
                            val pkgs = rule.targetPackages.split(",").map { it.trim().lowercase() }
                            val usedMinutes = todayUsage.filter { it.packageName.lowercase() in pkgs }.sumOf { (it.foregroundMs / 60000L).toInt() }
                            val limit = rule.limitMinutes
                            val progress = (usedMinutes.toFloat() / limit.toFloat()).coerceIn(0f, 1f)

                            val progressColor = when {
                                usedMinutes >= limit -> Color(0xFFE53935)
                                progress >= 0.8f -> Color(0xFFF2A93B)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.width(160.dp).clickable { onOpenRule(rule.id) },
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "$usedMinutes / $limit min",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = progressColor,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = progressColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        drawStopIndicator = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Rules List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "BLOCK RULES (${rules.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showEmergencyDialog = true }) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Emergency unlock")
                    }
                }
            }

            // Rules Cards
            items(rules) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { enabled ->
                        scope.launch {
                            val isStrictActive = c.blocker.isStrictModeActive(rule)
                            if (isStrictActive && !enabled) {
                                Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Anti-cheat strict mode is active. Cannot disable this rule right now."), Toast.LENGTH_LONG).show()
                            } else {
                                c.blocker.setRuleEnabled(rule.id, enabled)
                            }
                        }
                    },
                    onClick = { onOpenRule(rule.id) },
                )
            }
        }
    }

    if (showEmergencyDialog) {
        EmergencyUnlockDialog(
            onDismiss = { showEmergencyDialog = false },
            onUnlocked = {
                showEmergencyDialog = false
                isEmergencyActive = true
            },
        )
    }
}

@Composable
private fun RuleCard(
    rule: BlockRuleEntity,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val (typeLabel, icon) = when (rule.type) {
        BlockRuleType.DAILY_LIMIT -> "Daily limit" to Icons.Rounded.Timer
        BlockRuleType.SCHEDULE -> "Schedule" to Icons.Rounded.Schedule
        BlockRuleType.FOCUS -> "Focus timer" to Icons.Rounded.Shield
        BlockRuleType.CLASS -> "Class hours" to Icons.Rounded.Security
        BlockRuleType.APP_GROUP -> "App group" to Icons.Rounded.Block
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (rule.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (rule.strict) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Strict mode",
                            tint = Color(0xFFF2A93B).readable(),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                val detail = when (rule.type) {
                    BlockRuleType.DAILY_LIMIT -> "${rule.limitMinutes} min limit"
                    BlockRuleType.SCHEDULE -> "${TimeFormat.format(rule.startTime ?: LocalTime.MIDNIGHT)} – ${TimeFormat.format(rule.endTime ?: LocalTime.MIDNIGHT)}"
                    BlockRuleType.FOCUS -> "During focus sessions"
                    BlockRuleType.CLASS -> "During scheduled classes"
                    BlockRuleType.APP_GROUP -> "${rule.limitMinutes} min shared limit"
                }

                val appCount = rule.targetPackages.split(",").filter { it.isNotBlank() }.size
                Text(
                    text = "$typeLabel • $detail • $appCount apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun PermissionsGuideCard(
    isAccessibilityRunning: Boolean,
    hasUsageAccess: Boolean,
    hasOverlayPermission: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenOverlay: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Blocker Setup Required",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "To accurately detect and block distracting apps, please grant the following Android permissions:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            // Accessibility Item
            PermissionRow(
                title = "Accessibility Service",
                subtitle = "Detects foreground app & Chrome URLs instantly",
                isGranted = isAccessibilityRunning,
                onGrant = onOpenAccessibility,
            )

            // Usage Access Item
            PermissionRow(
                title = "Usage Access",
                subtitle = "Tracks daily screen time accurately",
                isGranted = hasUsageAccess,
                onGrant = onOpenUsage,
            )

            // Overlay Item
            PermissionRow(
                title = "Display Over Other Apps",
                subtitle = "Shows block screen over restricted apps",
                isGranted = hasOverlayPermission,
                onGrant = onOpenOverlay,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isGranted) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Granted", tint = Color(0xFF4CAF50).readable(), modifier = Modifier.size(20.dp))
        } else {
            Button(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun checkUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = if (android.os.Build.VERSION.SDK_INT >= 29) appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName,
    ) else @Suppress("DEPRECATION") appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun ReelsCounterCard(
    totalCount: Int,
    reelsList: List<ReelsUsageEntity>,
    trackingEnabled: Boolean,
    dailyLimit: Int,
    onToggleTracking: (Boolean) -> Unit,
    onSetDailyLimit: (Int) -> Unit,
    onResetToday: () -> Unit,
) {
    val instaCount = reelsList.find { it.platform.equals("instagram", ignoreCase = true) }?.count ?: 0
    val ytCount = reelsList.find { it.platform.equals("youtube", ignoreCase = true) }?.count ?: 0
    val fbCount = reelsList.find { it.platform.equals("facebook", ignoreCase = true) }?.count ?: 0

    val estMinutes = (totalCount * 30) / 60
    val isOverLimit = dailyLimit > 0 && totalCount >= dailyLimit
    val isNearLimit = dailyLimit > 0 && totalCount >= (dailyLimit * 0.8f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title + Tracking Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE1306C).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFFE1306C).readable(),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Shorts & Reels Counter",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "FB Reels • YT Shorts • Insta Reels",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Switch(
                    checked = trackingEnabled,
                    onCheckedChange = onToggleTracking,
                )
            }

            if (!trackingEnabled) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Short-form video tracking is paused. Turn on to count reels and receive mindful interval alerts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(14.dp))

                // Hero Counter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$totalCount",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = if (isOverLimit) Color(0xFFE53935).readable() else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "reels watched today",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = if (estMinutes > 0) "~$estMinutes min scroll" else "<1 min scroll",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Platform Breakdown Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlatformChip(
                        name = "Instagram",
                        count = "$instaCount",
                        color = Color(0xFFE1306C).readable(),
                        modifier = Modifier.weight(1f),
                    )
                    PlatformChip(
                        name = "YouTube",
                        count = "$ytCount",
                        color = Color(0xFFFF0000).readable(),
                        modifier = Modifier.weight(1f),
                    )
                    PlatformChip(
                        name = "Facebook",
                        count = "$fbCount",
                        color = Color(0xFF1877F2).readable(),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Daily Limit Selector
                Text(
                    text = "Daily reel limit",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val limitOptions = listOf(0 to "Off", 25 to "25", 50 to "50", 75 to "75", 100 to "100")
                    limitOptions.forEach { (limit, label) ->
                        FilterChip(
                            selected = dailyLimit == limit,
                            onClick = { onSetDailyLimit(limit) },
                            label = {
                                Text(
                                    text = if (limit == 0) label else "$label reels",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }

                // Progress Bar (if limit is set)
                if (dailyLimit > 0) {
                    Spacer(Modifier.height(10.dp))
                    val progress = (totalCount.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f)
                    val progressColor = when {
                        isOverLimit -> Color(0xFFE53935)
                        isNearLimit -> Color(0xFFF2A93B)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isOverLimit) "⚠️ Daily limit reached! Time to focus." else "$totalCount / $dailyLimit reels",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = progressColor,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = progressColor,
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Footer Row: Reset action + Mindful hint
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Alerts every 25 reels to stay mindful",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onResetToday) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset count", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformChip(
    name: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
