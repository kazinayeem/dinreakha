package com.example.tracker.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhonelinkOff
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tracker.container
import com.example.tracker.data.db.AppUsageEntity
import com.example.tracker.data.db.BlockRuleEntity
import com.example.tracker.data.finance.Money
import com.example.tracker.data.schedule.Habits
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.PlanCalendar.EventType
import com.example.tracker.data.schedule.PlanCalendar.PlanEvent
import com.example.tracker.ui.components.CheckRow
import com.example.tracker.ui.components.DAY_TITLE
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.Pill
import com.example.tracker.ui.theme.Expense
import com.example.tracker.ui.theme.Income
import java.time.Duration
import java.time.LocalDate

@Composable
fun TodayScreen(
    date: LocalDate?,
    onOpenReview: (Int) -> Unit,
    onAddExpense: () -> Unit,
    onOpenWallet: () -> Unit,
    onStartFocus: (type: String, minutes: Long) -> Unit = { _, _ -> },
    onOpenPlan: () -> Unit = {},
    onCaptureNote: (Long?) -> Unit = {},
    onOpenCourses: () -> Unit = {},
    onOpenBlocker: () -> Unit = {},
    onOpenDailyReport: (LocalDate) -> Unit = {},
    onLogSleep: () -> Unit = {},
) {
    val c = LocalContext.current.container
    val vm: TodayViewModel = viewModel(key = "day_$date") { TodayViewModel(c, date) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val weatherState by vm.weather.collectAsStateWithLifecycle()
    val s = ui ?: return

    var activeClass by remember { mutableStateOf<com.example.tracker.data.repo.ActiveClassResult?>(null) }
    LaunchedEffect(s.now) {
        if (s.isToday) {
            activeClass = c.notes.detectCurrentClass(s.now)
        }
    }

    val blockRules by remember { c.blocker.observeRules() }.collectAsStateWithLifecycle(emptyList())
    val todayUsage by remember(s.date) { c.blocker.observeAppUsage(s.date) }.collectAsStateWithLifecycle(emptyList())
    val totalReelsCount by remember(s.date) { c.blocker.observeTotalReelsCount(s.date) }.collectAsStateWithLifecycle(0)
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val limitedRules = blockRules.filter {
        it.enabled &&
            it.type in listOf(com.example.tracker.data.db.BlockRuleType.DAILY_LIMIT, com.example.tracker.data.db.BlockRuleType.APP_GROUP) &&
            it.limitMinutes > 0
    }

    val expected = s.checklist.filter { it.expected }
    val doneCount = expected.count { it.key in s.day.doneKeys }
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    var showReflection by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- 1. Glanceable Header (Date + Milestone) ----
        item(key = "header") {
            DayHeader(s = s, onOpenReview = onOpenReview, onOpenPlan = onOpenPlan, onOpenReport = { onOpenDailyReport(s.date) })
        }

        // Active Class Banner (When in class or class ended recently)
        if (s.isToday && activeClass != null) {
            item(key = "active_class_card") {
                val ac = activeClass!!
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (ac.isHappeningNow) "Class in Session" else "Recent Class",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "${ac.course.code}: ${ac.classTitle}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Room ${ac.room}, ${HM.format(ac.startTime)} – ${HM.format(ac.endTime)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onCaptureNote(ac.course.id) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Capture note", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ---- 2. Unified Signature Ambient Dayline Card (Wake-to-Bed + Weather & AQI) ----
        if (s.isToday) {
            item(key = "ambient_dayline_card") {
                AmbientDaylineCard(
                    wake = PlanCalendar.wakeDateTime(s.date),
                    bed = PlanCalendar.bedDateTime(s.date),
                    now = s.now,
                    events = s.timeline,
                    doneKeys = s.day.doneKeys,
                    weatherState = weatherState,
                    onRefreshWeather = { vm.refreshWeather() },
                )
            }
        } else {
            item(key = "dayline_history") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Dayline(
                            wake = PlanCalendar.wakeDateTime(s.date),
                            bed = PlanCalendar.bedDateTime(s.date),
                            now = s.now,
                            events = s.timeline,
                            doneKeys = s.day.doneKeys,
                        )
                    }
                }
            }
        }

        // ---- 3. Actionable "Up Next" Focus Bar ----
        if (s.isToday) {
            nextUp(s)?.let { nextEvent ->
                item(key = "next_action") {
                    NextActionBar(
                        s = s,
                        e = nextEvent,
                        vm = vm,
                        onStartFocus = onStartFocus,
                    )
                }
            }
        }

        // ---- Checklist: what you act on all day, so it comes right after "up next" ----
        item(key = "checklist") {
            ChecklistCard(
                s = s,
                vm = vm,
                expected = expected,
                doneCount = doneCount,
            )
        }

        // ---- At a glance: habits · spent · screen (each opens its page) ----
        item(key = "glance") {
            GlanceTiles(
                doneCount = doneCount,
                totalExpected = expected.size,
                spentToday = s.spentToday,
                limitedRules = limitedRules,
                todayUsage = todayUsage,
                reels = totalReelsCount,
                reelsLimit = settings?.reelsDailyLimit ?: 0,
                onOpenWallet = onOpenWallet,
                onAddExpense = onAddExpense,
                onOpenBlocker = onOpenBlocker,
            )
        }

        // ---- Morning: log last night's sleep (until 3 PM, only if not logged yet) ----
        if (s.isToday && s.now.hour < 15) {
            item(key = "sleep_prompt") {
                val c2 = LocalContext.current.container
                val logged by remember(s.date) { c2.db.sleepDao().observeAll() }.collectAsStateWithLifecycle(null)
                if (logged != null && logged!!.none { it.night == s.date.minusDays(1) }) {
                    FoldedRow(Icons.Rounded.Bedtime, "Log last night's sleep", "+", onOpen = onLogSleep)
                }
            }
        }

        // ---- 6. Schedule Timeline Card ----
        item(key = "timeline") {
            if (showSchedule || !s.isToday) TimelineCard(s = s, onCaptureNote = onCaptureNote)
            else FoldedRow(Icons.Rounded.Timeline, "Today's schedule", "${s.timeline.size}") { showSchedule = true }
        }

        // ---- Reflection & mood: an evening task, so folded earlier in the day ----
        item(key = "reflection") {
            val evening = !s.isToday || s.now.hour >= 18 || s.day.log?.mood != null
            if (evening || showReflection) ReflectionCard(s = s, vm = vm)
            else FoldedRow(Icons.Rounded.EditNote, "Evening reflection & mood", "+") { showReflection = true }
        }
    }
}

/** Clean, open greeting and milestone header. */
@Composable
private fun DayHeader(s: TodayUi, onOpenReview: (Int) -> Unit, onOpenPlan: () -> Unit, onOpenReport: () -> Unit) {
    val day = PlanCalendar.dayNumber(s.date)
    val week = PlanCalendar.weekNumber(s.date)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = DAY_TITLE.format(s.date),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = onOpenReport) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "Daily summary and share card", tint = MaterialTheme.colorScheme.primary)
                }
                PlanCalendar.holidayName(s.date)?.let { holiday ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = holiday,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                if (s.startAgain) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Fresh start",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenPlan)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = when {
                    day in 1..PlanCalendar.planDays -> "Day $day of ${PlanCalendar.planDays}"
                    day == 0 -> "Starts tomorrow"
                    day < 0 && day >= -7 -> "Starts in ${-day + 1} days"
                    day > PlanCalendar.planDays -> "Plan Completed 🏁"
                    else -> "Today's Rhythm"
                },
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (day in 1..PlanCalendar.planDays) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${day * 100 / PlanCalendar.planDays}% done ➔",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Review alert banner if applicable
        if (PlanCalendar.isReviewDay(s.date)) {
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = { onOpenReview(week) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Week $week Review is ready",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Actionable "Up Next" focus banner: clean, high contrast, direct 1-tap action. */
@Composable
private fun NextActionBar(
    s: TodayUi,
    e: PlanEvent,
    vm: TodayViewModel,
    onStartFocus: (String, Long) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val mins = Duration.between(s.now, e.start).toMinutes()
    val isHappeningNow = mins <= 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = eventIcon(e),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isHappeningNow) "NOW" else "IN ${formatMins(mins)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = if (isHappeningNow) Income else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "• ${HM.format(e.start)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = e.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            val key = e.habitKey
            val focusType = when (key) {
                Habits.COURSE_STUDY -> "STUDY"
                Habits.RESEARCH -> "RESEARCH"
                Habits.CODE_PROJECT -> "CODE"
                else -> null
            }

            if (e.type == EventType.FOCUS && focusType != null && key !in s.day.doneKeys) {
                FilledTonalButton(
                    onClick = {
                        val durationMins = e.end?.let { Duration.between(e.start, it).toMinutes() } ?: 45
                        onStartFocus(focusType, durationMins)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Timer, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Focus")
                }
            } else if (key != null && s.checklist.any { it.key == key }) {
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.toggle(s.date, key, true)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Done")
                }
            }
        }
    }
}

/** Three equal tiles: habits done, spent today, tightest app limit. Numbers first, one word of label. */
@Composable
private fun GlanceTiles(
    doneCount: Int,
    totalExpected: Int,
    spentToday: Long,
    limitedRules: List<BlockRuleEntity>,
    todayUsage: List<AppUsageEntity>,
    reels: Int,
    reelsLimit: Int,
    onOpenWallet: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenBlocker: () -> Unit,
) {
    // The limit closest to (or over) its cap is the one worth seeing.
    val worst = limitedRules.map { rule ->
        val pkgs = rule.targetPackages.split(",").map { it.trim().lowercase() }.toSet()
        val used = (todayUsage.filter { it.packageName.lowercase() in pkgs }.sumOf { it.foregroundMs } / 60_000L).toInt()
        Triple(rule.name, used, rule.limitMinutes)
    }.maxByOrNull { (_, used, limit) -> used.toFloat() / limit.coerceAtLeast(1) }
    val habitFrac = if (totalExpected > 0) doneCount.toFloat() / totalExpected else 0f

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlanceTile(Icons.Rounded.TaskAlt, MaterialTheme.colorScheme.primary, "$doneCount/$totalExpected", "habits",
            progress = habitFrac, onClick = null, modifier = Modifier.weight(1f))
        GlanceTile(Icons.Rounded.AccountBalanceWallet, if (spentToday > 0) Expense else MaterialTheme.colorScheme.secondary,
            Money.format(spentToday), "spent", progress = null, onClick = onOpenWallet, onLongClick = onAddExpense, modifier = Modifier.weight(1f))
        when {
            worst != null -> {
                val (name, used, limit) = worst
                val frac = used.toFloat() / limit.coerceAtLeast(1)
                GlanceTile(Icons.Rounded.Shield, if (frac >= 1f) Expense else if (frac >= 0.8f) com.example.tracker.ui.theme.Warn else MaterialTheme.colorScheme.tertiary,
                    "$used/${limit}m", name, progress = frac, onClick = onOpenBlocker, modifier = Modifier.weight(1f))
            }
            reelsLimit > 0 || reels > 0 -> GlanceTile(Icons.Rounded.Shield, MaterialTheme.colorScheme.tertiary,
                if (reelsLimit > 0) "$reels/$reelsLimit" else "$reels", "reels", progress = if (reelsLimit > 0) reels.toFloat() / reelsLimit else null,
                onClick = onOpenBlocker, modifier = Modifier.weight(1f))
            else -> GlanceTile(Icons.Rounded.Shield, MaterialTheme.colorScheme.tertiary, "Set", "app limits", progress = null,
                onClick = onOpenBlocker, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GlanceTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    value: String,
    label: String,
    progress: Float?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val p by androidx.compose.animation.core.animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), label = "tile")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value" },
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                    color = tint, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
            }
        }
    }
}

/** A closed section you open with one tap (keeps Home short). */
@Composable
private fun FoldedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, badge: String, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleSmall)
            Pill(badge)
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Show", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Interactive Checklist with category filter tabs. */
@Composable
private fun ChecklistCard(
    s: TodayUi,
    vm: TodayViewModel,
    expected: List<PlanCalendar.ChecklistItem>,
    doneCount: Int,
) {
    var selectedCategory by rememberSaveable { mutableStateOf("All") }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Today's checklist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "$doneCount of ${expected.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (doneCount == expected.size && expected.size > 0) Income else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Category filter chips
            val categories = listOf("All", "Habits", "Prayers", "Classes", "Routines")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Filter items based on selected category
            val visible = s.checklist.filter { it.expected || !it.key.startsWith("prayer_") || it.key in s.day.doneKeys }
            val filtered = when (selectedCategory) {
                "Habits" -> visible.filter { !it.key.startsWith("prayer_") && !it.key.startsWith("class_") && !it.key.startsWith("routine_") }
                "Prayers" -> visible.filter { it.key.startsWith("prayer_") }
                "Classes" -> visible.filter { it.key.startsWith("class_") }
                "Routines" -> visible.filter { it.key.startsWith("routine_") }
                else -> visible
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (visible.isEmpty()) "No checklist items for today." else "No items in this category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column {
                    filtered.sortedBy { !it.expected }.forEachIndexed { i, item ->
                        CheckRow(
                            label = item.label,
                            checked = item.key in s.day.doneKeys,
                            onChange = { vm.toggle(s.date, item.key, it) },
                            trailing = s.day.done[item.key]?.let { HM.format(it) },
                            optional = !item.expected,
                            leading = com.example.tracker.ui.components.habitIcon(item.key),
                        )
                        if (i < filtered.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                modifier = Modifier.padding(start = 48.dp),
                            )
                        }
                    }
                }
            }

            // Completion Banner
            AnimatedVisibility(expected.isNotEmpty() && doneCount == expected.size) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Income.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Income, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "All checklist items completed! 👏",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Income,
                        )
                    }
                }
            }
        }
    }
}

/** Schedule Timeline card with clean node lines and event status. */
@Composable
private fun TimelineCard(s: TodayUi, onCaptureNote: (Long?) -> Unit = {}) {
    var showAll by rememberSaveable { mutableStateOf(!s.isToday) }
    val firstUpcoming = if (s.isToday) s.timeline.indexOfFirst { !(it.end ?: it.start).isBefore(s.now) } else -1
    val items = if (showAll || firstUpcoming < 0) s.timeline else s.timeline.drop((firstUpcoming - 1).coerceAtLeast(0))

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Schedule",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                if (s.isToday) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Upcoming only" else "Whole day")
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No scheduled events for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items.forEachIndexed { i, e ->
                val past = s.isToday && (e.end ?: e.start).isBefore(s.now)
                val done = e.habitKey != null && e.habitKey in s.day.doneKeys
                val col = eventColor(e.type)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${HM.format(e.start)}, ${e.title}${if (done) ", done" else ""}"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = HM.format(e.start),
                        modifier = Modifier.width(66.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    )

                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(48.dp)
                                .background(if (i == items.lastIndex) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (done) Icons.Rounded.Check else eventIcon(e),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (past && !done) col.copy(alpha = 0.45f) else col,
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = e.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val detail = listOfNotNull(
                            e.end?.let { "until ${HM.format(it)}" },
                            e.detail.takeIf { it.isNotBlank() },
                        ).joinToString(", ")
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (e.type == EventType.CLASS) {
                        IconButton(
                            onClick = { onCaptureNote(null) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Capture note for ${e.title}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    if (e.optional) {
                        Pill("optional")
                    }
                }
            }
        }
        }
    }
}

/** Daily Reflection & Mood card: clean emoji selector and note field. */
private val MOODS = listOf(
    "😞" to "Low",
    "🙁" to "Meh",
    "😐" to "Okay",
    "🙂" to "Good",
    "😄" to "Great",
)

@Composable
private fun ReflectionCard(s: TodayUi, vm: TodayViewModel) {
    val haptics = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Reflection",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "How was your day?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MOODS.forEachIndexed { i, (emoji, label) ->
                    val value = i + 1
                    val selected = s.day.log?.mood == value
                    val bg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "mood_bg",
                    )

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .semantics { contentDescription = "Mood: $label" }
                            .selectable(selected, role = Role.RadioButton) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                vm.setMood(s.date, if (selected) null else value)
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            var note by remember(s.date) { mutableStateOf(s.day.log?.note ?: "") }
            var loaded by remember(s.date) { mutableStateOf(false) }
            LaunchedEffect(s.day.log?.note) {
                if (!loaded && s.day.log != null) {
                    note = s.day.log.note
                    loaded = true
                }
            }
            val dirty = note != (s.day.log?.note ?: "")

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Write a reflection or thought about today...") },
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
            )

            AnimatedVisibility(dirty) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalButton(
                        onClick = { vm.setNote(s.date, note) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("Save note")
                    }
                }
            }
        }
    }
}

// ---------------- Helpers ----------------

private fun nextUp(s: TodayUi): PlanEvent? =
    s.timeline.firstOrNull { !(it.end ?: it.start).isBefore(s.now) && !(it.habitKey != null && it.habitKey in s.day.doneKeys) }

fun formatMins(m: Long) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

fun eventIcon(e: PlanEvent): ImageVector = when (e.type) {
    EventType.WAKE -> Icons.Rounded.WbSunny
    EventType.BEDTIME -> Icons.Rounded.Bedtime
    EventType.SCREENS_OFF -> Icons.Rounded.PhonelinkOff
    EventType.CLASS -> Icons.Rounded.School
    EventType.BUS_TO_CAMPUS, EventType.BUS_HOME -> Icons.Rounded.DirectionsBus
    EventType.PRAYER -> Icons.Rounded.NightsStay
    EventType.ROUTINE -> Icons.Rounded.TaskAlt
    EventType.FOCUS -> when (e.habitKey) {
        Habits.RESEARCH -> Icons.Rounded.Science
        Habits.CODE_PROJECT -> Icons.Rounded.Code
        else -> Icons.AutoMirrored.Rounded.MenuBook
    }
}

@Composable
private fun eventColor(t: EventType): Color {
    val cs = MaterialTheme.colorScheme
    return when (t) {
        EventType.WAKE, EventType.BEDTIME, EventType.SCREENS_OFF -> cs.secondary
        EventType.CLASS -> cs.tertiary
        EventType.BUS_TO_CAMPUS, EventType.BUS_HOME -> Color(0xFFC77DFF)
        EventType.PRAYER -> cs.primary
        EventType.FOCUS -> Color(0xFF4FA8E8)
        EventType.ROUTINE -> Color(0xFFF07AAE)
    }
}
