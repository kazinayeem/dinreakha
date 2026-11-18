package com.example.tracker.ui.report

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.container
import com.example.tracker.data.report.CardFormat
import com.example.tracker.data.report.CardPrivacySettings
import com.example.tracker.data.report.CardTheme
import com.example.tracker.data.report.CardType
import com.example.tracker.data.report.DailyReport
import com.example.tracker.data.report.ThirtyDayReport
import com.example.tracker.data.report.WeeklyReport
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardStudioScreen(
    initialType: CardType = CardType.DAILY,
    date: LocalDate = LocalDate.now(Schedule.ZONE),
    week: Int = PlanCalendar.weekNumber(LocalDate.now(Schedule.ZONE)),
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()

    var cardType by remember { mutableStateOf(initialType) }
    var cardFormat by remember { mutableStateOf(CardFormat.STORY) }
    var cardTheme by remember { mutableStateOf(CardTheme.DARK_GRADIENT) }
    var privacy by remember { mutableStateOf(CardPrivacySettings()) }
    var fbAppId by remember { mutableStateOf("") }
    var showFbDialog by remember { mutableStateOf(false) }

    var dailyReport by remember { mutableStateOf<DailyReport?>(null) }
    var weeklyReport by remember { mutableStateOf<WeeklyReport?>(null) }
    var thirtyDayReport by remember { mutableStateOf<ThirtyDayReport?>(null) }

    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }

    // Initial load of data & saved name / fb id
    LaunchedEffect(Unit) {
        val savedName = c.reports.getUserCardName()
        fbAppId = c.reports.getFacebookAppId()
        if (savedName.isNotBlank()) {
            privacy = privacy.copy(userName = savedName, showName = true)
        }
    }

    LaunchedEffect(cardType, date, week) {
        when (cardType) {
            CardType.DAILY -> {
                if (dailyReport == null) dailyReport = c.reports.computeDailyReport(date)
            }
            CardType.WEEKLY -> {
                if (weeklyReport == null) weeklyReport = c.reports.computeWeeklyReport(week)
            }
            CardType.THIRTY_DAY, CardType.ACHIEVEMENT -> {
                if (thirtyDayReport == null) thirtyDayReport = c.reports.computeThirtyDayReport()
            }
        }
    }

    // Re-render card bitmap on any change
    LaunchedEffect(cardType, cardFormat, cardTheme, privacy, dailyReport, weeklyReport, thirtyDayReport) {
        isRendering = true
        withContext(Dispatchers.Default) {
            val bmp = when (cardType) {
                CardType.DAILY -> {
                    dailyReport?.let { CardRenderer.renderDailyCard(it, cardFormat, cardTheme, privacy) }
                }
                CardType.WEEKLY -> {
                    weeklyReport?.let { CardRenderer.renderWeeklyCard(it, cardFormat, cardTheme, privacy) }
                }
                CardType.THIRTY_DAY, CardType.ACHIEVEMENT -> {
                    thirtyDayReport?.let { CardRenderer.renderThirtyDayCard(it, cardFormat, cardTheme, privacy) }
                }
            }
            renderedBitmap = bmp
            isRendering = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Share card",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFbDialog = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Facebook App ID")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Card Type Selector Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardType.entries.forEach { type ->
                        FilterChip(
                            selected = cardType == type,
                            onClick = { cardType = type },
                            label = { Text(type.label, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // 2. Format Selector Tabs (Story 9:16 vs Square 1:1)
            item {
                TabRow(
                    selectedTabIndex = cardFormat.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                ) {
                    CardFormat.entries.forEach { fmt ->
                        Tab(
                            selected = cardFormat == fmt,
                            onClick = { cardFormat = fmt },
                            text = { Text(fmt.label, fontWeight = FontWeight.Bold) },
                        )
                    }
                }
            }

            // 3. Live Card Preview
            item {
                CardPreviewBox(
                    bitmap = renderedBitmap,
                    aspectRatio = cardFormat.aspectRatio,
                    isRendering = isRendering,
                )
            }

            // 4. Action Export Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Save to Gallery
                        Button(
                            onClick = {
                                renderedBitmap?.let { bmp ->
                                    ShareHelper.saveToGallery(ctx, bmp, "${cardType.label} - Dinrekha")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save to gallery", fontWeight = FontWeight.Bold)
                        }

                        // Share Sheet
                        Button(
                            onClick = {
                                renderedBitmap?.let { bmp ->
                                    ShareHelper.shareImage(ctx, bmp, "Share ${cardType.label}")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share card", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Direct Facebook Story Button
                    OutlinedButton(
                        onClick = {
                            renderedBitmap?.let { bmp ->
                                ShareHelper.shareToFacebookStory(ctx, bmp, fbAppId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Share to Facebook story", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 5. Theme Palette Selector
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Card theme", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CardTheme.entries.forEach { theme ->
                                val isSelected = cardTheme == theme
                                Surface(
                                    onClick = { cardTheme = theme },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(theme.previewColor),
                                    modifier = Modifier
                                        .size(width = 110.dp, height = 56.dp)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(14.dp),
                                        ),
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = theme.label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (theme == CardTheme.CLEAN_LIGHT) Color.Black else Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. User Name & Privacy Toggles
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Privacy & display controls", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                        }

                        Spacer(Modifier.height(12.dp))

                        // User Name Input
                        OutlinedTextField(
                            value = privacy.userName,
                            onValueChange = { newName ->
                                privacy = privacy.copy(userName = newName, showName = newName.isNotBlank())
                                scope.launch { c.reports.setUserCardName(newName) }
                            },
                            label = { Text("Your Name / Handle (Optional)") },
                            placeholder = { Text("e.g. Ali Nayeem") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        Spacer(Modifier.height(14.dp))

                        // Privacy Switches
                        PrivacyToggleRow(
                            label = "Show Deep Focus Minutes",
                            checked = privacy.showFocus,
                            onCheckedChange = { privacy = privacy.copy(showFocus = it) },
                        )
                        PrivacyToggleRow(
                            label = "Show Habit Completion",
                            checked = privacy.showHabits,
                            onCheckedChange = { privacy = privacy.copy(showHabits = it) },
                        )
                        PrivacyToggleRow(
                            label = "Show Daily Prayers",
                            checked = privacy.showPrayers,
                            onCheckedChange = { privacy = privacy.copy(showPrayers = it) },
                        )
                        PrivacyToggleRow(
                            label = "Show Screen Time",
                            checked = privacy.showScreenTime,
                            onCheckedChange = { privacy = privacy.copy(showScreenTime = it) },
                        )
                        PrivacyToggleRow(
                            label = "Show Earned Badges",
                            checked = privacy.showBadges,
                            onCheckedChange = { privacy = privacy.copy(showBadges = it) },
                        )

                        // Strict Private (OFF by default)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Strictly private (off by default)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(6.dp))
                        PrivacyToggleRow(
                            label = "Include Personal Mood (1-5)",
                            checked = privacy.showMood,
                            onCheckedChange = { privacy = privacy.copy(showMood = it) },
                        )
                        PrivacyToggleRow(
                            label = "Include Journal Note",
                            checked = privacy.showJournal,
                            onCheckedChange = { privacy = privacy.copy(showJournal = it) },
                        )
                        PrivacyToggleRow(
                            label = "Include Exact Sleep/Wake Times",
                            checked = privacy.showExactSleepTimes,
                            onCheckedChange = { privacy = privacy.copy(showExactSleepTimes = it) },
                        )
                    }
                }
            }
        }
    }

    if (showFbDialog) {
        FacebookAppIdDialog(
            currentId = fbAppId,
            onDismiss = { showFbDialog = false },
            onSave = { newId ->
                scope.launch {
                    c.reports.setFacebookAppId(newId)
                    fbAppId = newId
                    showFbDialog = false
                }
            },
        )
    }
}

@Composable
private fun CardPreviewBox(
    bitmap: Bitmap?,
    aspectRatio: Float,
    isRendering: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (aspectRatio < 1f) 0.65f else 0.95f)
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Share Card Live Preview",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (isRendering) {
                    Text(
                        text = "Rendering crisp 1080p card...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun FacebookAppIdDialog(
    currentId: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var idText by remember { mutableStateOf(currentId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Facebook App ID") },
        text = {
            Column {
                Text(
                    text = "If you have a Facebook Developer App ID, enter it here for enhanced direct story attribution. (Leave blank to use standard story sharing).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = idText,
                    onValueChange = { idText = it },
                    label = { Text("Facebook App ID") },
                    placeholder = { Text("e.g. 123456789012345") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(idText.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

