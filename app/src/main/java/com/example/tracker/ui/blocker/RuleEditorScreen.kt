package com.example.tracker.ui.blocker

import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.tracker.container
import com.example.tracker.data.db.BlockRuleEntity
import com.example.tracker.data.db.BlockRuleType
import com.example.tracker.data.repo.BlockerRepository
import com.example.tracker.data.schedule.TimeFormat
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime

data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(
    ruleId: Long? = null,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()

    var existingRule by remember { mutableStateOf<BlockRuleEntity?>(null) }
    var isStrictLocked by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(BlockRuleType.DAILY_LIMIT) }
    val selectedPackages = remember { mutableStateListOf<String>() }
    var targetDomains by remember { mutableStateOf("") }
    var limitMinutes by remember { mutableIntStateOf(30) }
    var sessionLimitMinutes by remember { mutableIntStateOf(0) }
    var cooldownMinutes by remember { mutableIntStateOf(0) }
    var startTime by remember { mutableStateOf(LocalTime.of(23, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(5, 5)) }
    val selectedDays = remember { mutableStateListOf<Int>() }
    var isStrict by remember { mutableStateOf(false) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showAppPickerSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    // Load installed launcher apps
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    if (pkg in BlockerRepository.SYSTEM_ALLOW_LIST) return@mapNotNull null
                    val appLabel = resolveInfo.loadLabel(pm).toString()
                    val icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                    AppItem(pkg, appLabel, icon)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
            installedApps = list
        }
    }

    // Load existing rule if editing
    LaunchedEffect(ruleId) {
        if (ruleId != null && ruleId > 0L) {
            val r = c.blocker.getRule(ruleId)
            if (r != null) {
                existingRule = r
                name = r.name
                selectedType = r.type
                selectedPackages.clear()
                selectedPackages.addAll(r.targetPackages.split(",").filter { it.isNotBlank() })
                targetDomains = r.targetDomains
                limitMinutes = r.limitMinutes
                sessionLimitMinutes = r.sessionLimitMinutes
                cooldownMinutes = r.cooldownMinutes
                startTime = r.startTime ?: LocalTime.of(23, 0)
                endTime = r.endTime ?: LocalTime.of(5, 5)
                selectedDays.clear()
                selectedDays.addAll(r.days.split(",").mapNotNull { it.trim().toIntOrNull() })
                isStrict = r.strict

                isStrictLocked = c.blocker.isStrictModeActive(r)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (ruleId != null && ruleId > 0L) "Edit Rule" else "New Block Rule",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (ruleId != null && ruleId > 0L) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (isStrictLocked) {
                                Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Anti-cheat strict mode is active. This rule cannot be changed until tomorrow."), Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (name.isBlank()) {
                                Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Please give this rule a name"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedPackages.isEmpty()) {
                                Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Please select at least one app to restrict"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            scope.launch {
                                val rule = BlockRuleEntity(
                                    id = ruleId ?: 0L,
                                    type = selectedType,
                                    name = name.trim(),
                                    targetPackages = selectedPackages.joinToString(","),
                                    targetDomains = targetDomains.trim(),
                                    limitMinutes = if (selectedType in listOf(BlockRuleType.DAILY_LIMIT, BlockRuleType.APP_GROUP)) limitMinutes else 0,
                                    sessionLimitMinutes = sessionLimitMinutes,
                                    cooldownMinutes = cooldownMinutes,
                                    startTime = if (selectedType == BlockRuleType.SCHEDULE) startTime else null,
                                    endTime = if (selectedType == BlockRuleType.SCHEDULE) endTime else null,
                                    days = selectedDays.joinToString(","),
                                    strict = isStrict,
                                    enabled = true,
                                )
                                c.blocker.saveRule(rule)
                                Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Rule saved"), Toast.LENGTH_SHORT).show()
                                onDone()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save Rule", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Strict Lock Warning
            if (isStrictLocked) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF2A93B).copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFFF2A93B), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Anti-cheat strict mode is currently active. Changes to this rule are disabled until outside restricted hours.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFF2A93B),
                        )
                    }
                }
            }

            // Rule Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Rule name") },
                placeholder = { Text("e.g. Facebook & Insta, Sleep Time Block") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isStrictLocked,
            )

            // Rule Type Selector
            SectionCard(title = "Rule type") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BlockRuleType.entries.forEach { type ->
                            val label = when (type) {
                                BlockRuleType.DAILY_LIMIT -> "Daily limit"
                                BlockRuleType.SCHEDULE -> "Schedule / Sleep"
                                BlockRuleType.FOCUS -> "Focus timer"
                                BlockRuleType.CLASS -> "Class hours"
                                BlockRuleType.APP_GROUP -> "App group"
                            }
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { if (!isStrictLocked) selectedType = type },
                                label = { Text(label) },
                                leadingIcon = if (selectedType == type) {
                                    { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }

                    Text(
                        text = when (selectedType) {
                            BlockRuleType.DAILY_LIMIT -> "Set maximum minutes allowed per day with warning notifications."
                            BlockRuleType.SCHEDULE -> "Block apps during specific hours (e.g. night bedtime to wake)."
                            BlockRuleType.FOCUS -> "Automatically blocks these apps whenever a Focus timer session is active."
                            BlockRuleType.CLASS -> "Automatically blocks these apps during your scheduled class periods."
                            BlockRuleType.APP_GROUP -> "Share a combined daily limit across all apps in this group."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Target Apps Selection
            SectionCard(title = "RESTRICTED APPS (${selectedPackages.size} SELECTED)") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (selectedPackages.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            selectedPackages.forEach { pkg ->
                                val app = installedApps.firstOrNull { it.packageName == pkg }
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (app?.icon != null) {
                                            val bmp = remember(app.icon) { app.icon.toBitmap(48, 48) }
                                            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(app?.appName ?: pkg.substringAfterLast('.'), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                        if (!isStrictLocked) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(16.dp).clickable { selectedPackages.remove(pkg) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showAppPickerSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isStrictLocked,
                    ) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (selectedPackages.isEmpty()) "Select Apps to Block" else "Add / Change Apps")
                    }
                }
            }

            // Website domains (Anti-bypass)
            OutlinedTextField(
                value = targetDomains,
                onValueChange = { targetDomains = it },
                label = { Text("Website domains to block (Chrome)") },
                placeholder = { Text("e.g. facebook.com, youtube.com, instagram.com") },
                supportingText = { Text("Blocks matching domains in Chrome so you can't bypass via browser") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isStrictLocked,
            )

            // Limits Configuration (for DAILY_LIMIT or APP_GROUP)
            if (selectedType in listOf(BlockRuleType.DAILY_LIMIT, BlockRuleType.APP_GROUP)) {
                SectionCard(title = "Time limits") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Daily Limit", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                text = if (limitMinutes == 0) "Completely blocked (0 min)" else "$limitMinutes minutes",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Slider(
                            value = limitMinutes.toFloat(),
                            onValueChange = { limitMinutes = it.toInt() },
                            valueRange = 0f..180f,
                            steps = 35,
                            enabled = !isStrictLocked,
                        )

                        // Quick presets
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(0, 15, 30, 45, 60, 90).forEach { m ->
                                FilterChip(
                                    selected = limitMinutes == m,
                                    onClick = { if (!isStrictLocked) limitMinutes = m },
                                    label = { Text(if (m == 0) "Blocked" else "${m}m") },
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Session Limit & Cooldown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Session Limit", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("Kick out after continuous use", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = if (sessionLimitMinutes == 0) "Off" else "$sessionLimitMinutes min",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        if (sessionLimitMinutes > 0) {
                            Slider(
                                value = sessionLimitMinutes.toFloat(),
                                onValueChange = { sessionLimitMinutes = it.toInt() },
                                valueRange = 5f..60f,
                                steps = 10,
                                enabled = !isStrictLocked,
                            )
                        } else {
                            TextButton(onClick = { if (!isStrictLocked) { sessionLimitMinutes = 10; cooldownMinutes = 15 } }) {
                                Text("+ Enable session limit")
                            }
                        }
                    }
                }
            }

            // Schedule Configuration (for SCHEDULE)
            if (selectedType == BlockRuleType.SCHEDULE) {
                SectionCard(title = "Time window") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f).clickable(enabled = !isStrictLocked) { showStartTimePicker = true },
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(TimeFormat.format(startTime), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f).clickable(enabled = !isStrictLocked) { showEndTimePicker = true },
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Until", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(TimeFormat.format(endTime), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }

                        // Day of week selector
                        Text("Active Days (empty = every day)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            listOf(
                                DayOfWeek.MONDAY to "M",
                                DayOfWeek.TUESDAY to "T",
                                DayOfWeek.WEDNESDAY to "W",
                                DayOfWeek.THURSDAY to "T",
                                DayOfWeek.FRIDAY to "F",
                                DayOfWeek.SATURDAY to "S",
                                DayOfWeek.SUNDAY to "S",
                            ).forEach { (dow, initial) ->
                                val sel = dow.value in selectedDays
                                Surface(
                                    shape = CircleShape,
                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable(enabled = !isStrictLocked) {
                                            if (sel) selectedDays.remove(dow.value) else selectedDays.add(dow.value)
                                        },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = initial,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Strict Mode (Anti-Cheat)
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
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFFF2A93B), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Anti-Cheat Strict Mode", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Lock settings during active hours", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = isStrict,
                            onCheckedChange = { isStrict = it },
                            enabled = !isStrictLocked,
                        )
                    }

                    if (isStrict) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "When active, this rule cannot be disabled or edited. Bypassing requires a 60-second cooldown challenge (max 2 per day).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // App Picker Sheet
    if (showAppPickerSheet) {
        AlertDialog(
            onDismissRequest = { showAppPickerSheet = false },
            title = { Text("Select Apps to Restrict") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search apps…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                    )
                    val filtered = installedApps.filter {
                        it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered) { app ->
                            val isSelected = app.packageName in selectedPackages
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedPackages.remove(app.packageName) else selectedPackages.add(app.packageName)
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (app.icon != null) {
                                        val bmp = remember(app.icon) { app.icon.toBitmap(64, 64) }
                                        Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape))
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAppPickerSheet = false }) {
                    Text("Done (${selectedPackages.size})")
                }
            },
        )
    }

    // Time Pickers
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = startTime.hour, initialMinute = startTime.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Select Start Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    startTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") } },
        )
    }

    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = endTime.hour, initialMinute = endTime.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("Select End Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    endTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") } },
        )
    }

    // Delete Confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this rule?") },
            text = { Text("This rule and all its app limits will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    if (ruleId != null) {
                        scope.launch {
                            c.blocker.deleteRule(ruleId)
                            onDone()
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}
