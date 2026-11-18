package com.example.tracker.ui.calendar

import com.example.tracker.ui.theme.readable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tracker.container
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.Space
import com.example.tracker.ui.components.short
import com.example.tracker.ui.components.weekFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private const val CENTER = 5_000
private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val AGENDA_DAY = DateTimeFormatter.ofPattern("EEE d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(nav: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val vm: CalendarViewModel = viewModel { CalendarViewModel(c) }
    val st by vm.state.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val anchor by vm.anchor.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val firstDay = settings?.firstDayOfWeek ?: DayOfWeek.SATURDAY
    val s = st ?: return
    val today = LocalDate.now(Schedule.ZONE)
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<CalItem?>(null) }
    var noteFor by remember { mutableStateOf<LocalDate?>(null) }
    var menu by remember { mutableStateOf(false) }
    var layersOpen by remember { mutableStateOf(false) }

    val datesWithNotes by remember { c.notes.observeDatesWithNotes() }.collectAsStateWithLifecycle(emptyList())

    val exportIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = c.calendar.exportIcs()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
            }.onSuccess { com.example.tracker.ui.components.showMessage(ctx, "Calendar exported") }
                .onFailure { com.example.tracker.ui.components.showMessage(ctx, "Export failed: ${it.message}", long = true) }
        }
    }
    val importIcs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }
                c.calendar.importIcs(text).also { c.scheduler.rescheduleAll() }
            }.onSuccess { n -> com.example.tracker.ui.components.showMessage(ctx, "Added $n event${if (n == 1) "" else "s"}") }
                .onFailure { com.example.tracker.ui.components.showMessage(ctx, "Couldn't read that file: ${it.message}", long = true) }
        }
    }

    // One pager per view; page CENTER = the anchor when the view was opened.
    val base = remember(view) { anchor }
    val pager = rememberPagerState(initialPage = CENTER) { CENTER * 2 }
    fun pageDate(page: Int): LocalDate = when (view) {
        CalView.MONTH -> YearMonth.from(base).plusMonths((page - CENTER).toLong()).atDay(1)
        CalView.WEEK -> base.with(TemporalAdjusters.previousOrSame(firstDay)).plusWeeks((page - CENTER).toLong())
        CalView.DAY, CalView.AGENDA -> base.plusDays((page - CENTER).toLong())
    }
    fun pageOf(d: LocalDate): Int = CENTER + when (view) {
        CalView.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(base), YearMonth.from(d)).toInt()
        CalView.WEEK -> ChronoUnit.WEEKS.between(base.with(TemporalAdjusters.previousOrSame(firstDay)), d.with(TemporalAdjusters.previousOrSame(firstDay))).toInt()
        CalView.DAY, CalView.AGENDA -> ChronoUnit.DAYS.between(base, d).toInt()
    }
    LaunchedEffect(pager.settledPage, view) {
        val d = pageDate(pager.settledPage)
        val a = vm.anchor.value
        vm.anchor.value = when (view) {
            CalView.MONTH -> if (YearMonth.from(a) == YearMonth.from(d)) a else if (YearMonth.from(today) == YearMonth.from(d)) today else d
            CalView.WEEK -> if (!a.isBefore(d) && a.isBefore(d.plusDays(7))) a else d
            else -> d
        }
    }
    fun go(delta: Int) = scope.launch { pager.animateScrollToPage(pager.currentPage + delta) }
    fun goTo(d: LocalDate) = scope.launch { vm.anchor.value = d; pager.animateScrollToPage(pageOf(d)) }
    fun openDay(d: LocalDate) { vm.anchor.value = d; vm.view.value = CalView.DAY }

    val title = when (view) {
        CalView.MONTH -> MONTH_TITLE.format(pageDate(pager.currentPage))
        CalView.WEEK -> pageDate(pager.currentPage).let { w -> "${AGENDA_DAY.format(w)} – ${AGENDA_DAY.format(w.plusDays(6))}" }
        CalView.DAY -> DAY_TITLE.format(pageDate(pager.currentPage))
        CalView.AGENDA -> "From ${AGENDA_DAY.format(pageDate(pager.currentPage))}"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- header ----
            Row(Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.xs, top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button) { goTo(today) }.padding(vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { go(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous") }
                IconButton(onClick = { go(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next") }
                if (anchor != today) IconButton(onClick = { goTo(today) }) { Icon(Icons.Rounded.Today, "Go to today", tint = MaterialTheme.colorScheme.primary) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Show layers…") }, { menu = false; layersOpen = true })
                        DropdownMenuItem({ Text("Note for ${AGENDA_DAY.format(anchor)}") }, { menu = false; noteFor = anchor })
                        DropdownMenuItem({ Text("Import .ics file") }, { menu = false; importIcs.launch(arrayOf("text/calendar", "text/*", "*/*")) })
                        DropdownMenuItem({ Text("Export my events (.ics)") }, { menu = false; exportIcs.launch("dinrekha-calendar.ics") })
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm)) {
                CalView.entries.forEachIndexed { i, v ->
                    SegmentedButton(
                        selected = view == v,
                        onClick = { vm.view.value = v },
                        shape = SegmentedButtonDefaults.itemShape(i, CalView.entries.size),
                        icon = {},
                        modifier = Modifier.semantics { contentDescription = "${v.label} view" },
                    ) { Icon(viewIcon(v), null, Modifier.size(20.dp)) }
                }
            }

            val monthMode = view == CalView.MONTH
            HorizontalPager(pager, if (monthMode) Modifier.fillMaxWidth().height(MONTH_HEIGHT) else Modifier.weight(1f),
                beyondViewportPageCount = 1, key = { "${view}_$it" }) { page ->
                val d = pageDate(page)
                when (view) {
                    CalView.MONTH -> MonthGrid(vm, s, YearMonth.from(d), firstDay, anchor, today, datesWithNotes, onDay = { vm.anchor.value = it },
                        onNew = { nav("calendar/edit?date=$it") })
                    CalView.WEEK -> TimeGrid(vm, s, (0L..6L).map { d.plusDays(it) }, today, onItem = { selected = it },
                        onNew = { day, t -> nav("calendar/edit?date=$day&hour=${t.hour}&minute=${t.minute}") }, onDayHeader = { openDay(it) })
                    CalView.DAY -> Column {
                        DayExtras(vm, s, d, onNote = { noteFor = d })
                        TimeGrid(vm, s, listOf(d), today, onItem = { selected = it },
                            onNew = { day, t -> nav("calendar/edit?date=$day&hour=${t.hour}&minute=${t.minute}") }, onDayHeader = {})
                    }
                    CalView.AGENDA -> Agenda(vm, s, d, onItem = { selected = it }, onNote = { noteFor = it })
                }
            }
            if (monthMode) SelectedDayList(vm, s, anchor, today, Modifier.weight(1f),
                onItem = { selected = it }, onNote = { noteFor = anchor }, onOpenDay = { openDay(anchor) })
        }
        ExtendedFloatingActionButton(
            onClick = { nav("calendar/edit?date=$anchor") },
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Event") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Space.lg),
        )
    }

    selected?.let { item -> ItemSheet(item, vm, nav, onClose = { selected = null }) }

    noteFor?.let { day ->
        var text by remember(day) { mutableStateOf(s.data.notes[day] ?: "") }
        AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text("Note for ${AGENDA_DAY.format(day)}") },
            text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Anything about this day") }) },
            confirmButton = { TextButton(onClick = { vm.saveNote(day, text); noteFor = null }) { Text(if (text.isBlank()) "Remove note" else "Save note") } },
            dismissButton = { TextButton(onClick = { noteFor = null }) { Text("Cancel") } },
        )
    }

    if (layersOpen) AlertDialog(
        onDismissRequest = { layersOpen = false },
        title = { Text("Show on the calendar") },
        text = {
            Column {
                Layer.entries.forEach { l ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.toggleLayer(l) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(l in s.layers, { vm.toggleLayer(l) })
                        Text(l.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { layersOpen = false }) { Text("Done") } },
    )
}

// ---------------- month ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthGrid(vm: CalendarViewModel, s: CalState, month: YearMonth, firstDay: DayOfWeek, anchor: LocalDate, today: LocalDate,
                      datesWithNotes: List<LocalDate>, onDay: (LocalDate) -> Unit, onNew: (LocalDate) -> Unit) {
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))
    val days = (0L until 42L).map { start.plusDays(it) }
    val items = remember(s, month) { vm.itemsBetween(s, days.first(), days.last()) }
    Column(Modifier.fillMaxSize().padding(horizontal = Space.sm)) {
        Row(Modifier.fillMaxWidth()) {
            weekFrom(firstDay).forEach { d ->
                Text(d.short(), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { day ->
                    val inMonth = YearMonth.from(day) == month
                    val dayItems = items.filter { it.touches(day) }
                    val holiday = dayItems.any { it.kind == ItemKind.HOLIDAY }
                    val hasNote = vm.notesOn(s, day) != null
                    val hasClassNote = day in datesWithNotes
                    val label = "${AGENDA_DAY.format(day)}, ${dayItems.count { it.kind != ItemKind.HOLIDAY }} items" +
                        (if (holiday) ", holiday" else "") + (if (hasNote) ", has a note" else "") + (if (hasClassNote) ", has class notes" else "")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().padding(2.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (day == anchor) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                            .combinedClickable(onClick = { onDay(day) }, onLongClick = { onNew(day) }, onLongClickLabel = "New event")
                            .semantics(mergeDescendants = true) { contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.size(30.dp).clip(CircleShape)
                            .background(if (day == today) MaterialTheme.colorScheme.primary else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Text("${day.dayOfMonth}", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    day == today -> MaterialTheme.colorScheme.onPrimary
                                    holiday -> HOLIDAY_COLOR.readable()
                                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                })
                        }
                        Spacer(Modifier.height(3.dp))
                        // up to 3 dots: your events first, then schedule kinds
                        val dots = (dayItems.filter { it.kind == ItemKind.EVENT }.map { it.color } +
                            dayItems.filter { it.kind == ItemKind.SCHEDULE && it.scheduleType == com.example.tracker.data.schedule.PlanCalendar.EventType.CLASS }
                                .take(1).map { it.color } +
                            (if (hasNote || hasClassNote) listOf(MaterialTheme.colorScheme.outline) else emptyList())).distinct().take(3)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            dots.forEach { Box(Modifier.size(6.dp).clip(CircleShape).background(it)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- week / day time grid ----------------

private val HOUR: Dp = 52.dp
private val GUTTER: Dp = 52.dp

@Composable
private fun TimeGrid(vm: CalendarViewModel, s: CalState, days: List<LocalDate>, today: LocalDate,
                     onItem: (CalItem) -> Unit, onNew: (LocalDate, LocalTime) -> Unit, onDayHeader: (LocalDate) -> Unit) {
    val items = remember(s, days) { vm.itemsBetween(s, days.first(), days.last()) }
    val density = LocalDensity.current
    val scroll = rememberScrollState(with(density) { (HOUR * 7).roundToPx() })
    val now = LocalDateTime.now(Schedule.ZONE)
    Column(Modifier.fillMaxSize()) {
        if (days.size > 1) Row(Modifier.fillMaxWidth().padding(start = GUTTER)) {
            days.forEach { d ->
                Column(Modifier.weight(1f).clickable(role = androidx.compose.ui.semantics.Role.Button) { onDayHeader(d) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.short(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium,
                        color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        // all-day row
        val allDay = items.filter { it.allDay }
        if (allDay.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = GUTTER, bottom = 4.dp)) {
            days.forEach { d ->
                Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    allDay.filter { it.touches(d) }.take(2).forEach { ItemChip(it, compact = days.size > 1) { onItem(it) } }
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            // hour labels
            Column(Modifier.width(GUTTER)) {
                (0..23).forEach { h ->
                    Box(Modifier.height(HOUR).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        if (h > 0) Text(HM.format(LocalTime.of(h, 0)).replace(":00", ""), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp).offset(y = (-7).dp))
                    }
                }
            }
            days.forEach { d ->
                val dayItems = items.filter { !it.allDay && it.touches(d) }
                val lanes = assignLanes(dayItems, d)
                val laneCount = (lanes.values.maxOrNull() ?: 0) + 1
                Box(
                    Modifier.weight(1f).height(HOUR * 24)
                        .background(if (d == today && days.size > 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f) else Color.Transparent)
                        .pointerInput(d) {
                            detectTapGestures(onLongPress = { off ->
                                val minutes = (off.y / HOUR.toPx() * 60).toInt().coerceIn(0, 23 * 60 + 30)
                                onNew(d, LocalTime.of(minutes / 60, (minutes % 60) / 30 * 30))
                            })
                        },
                ) {
                    // hour lines
                    Column { (0..23).forEach { _ -> Box(Modifier.height(HOUR).fillMaxWidth()) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) } } }
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        val colW = maxWidth
                        dayItems.forEach { it ->
                            val segStart = maxOf(it.start, d.atStartOfDay())
                            val segEnd = minOf(it.end, d.plusDays(1).atStartOfDay()).let { e -> if (!e.isAfter(segStart)) segStart.plusMinutes(30) else e }
                            val top = HOUR * (Duration.between(d.atStartOfDay(), segStart).toMinutes() / 60f)
                            val h = (HOUR * (Duration.between(segStart, segEnd).toMinutes() / 60f)).coerceAtLeast(24.dp)
                            val lane = lanes[it.key] ?: 0
                            val w = colW / laneCount
                            Box(Modifier.offset(x = w * lane, y = top).width(w).height(h).padding(1.dp)) {
                                ItemBlock(it, showTime = days.size == 1) { onItem(it) }
                            }
                        }
                        if (d == today) {
                            val y = HOUR * (now.toLocalTime().toSecondOfDay() / 3600f)
                            Box(Modifier.offset(y = y).fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.error))
                            Box(Modifier.offset(x = (-4).dp, y = y - 4.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        }
                    }
                }
            }
        }
    }
}

/** Side-by-side lanes for overlapping items (greedy interval colouring). */
private fun assignLanes(items: List<CalItem>, day: LocalDate): Map<String, Int> {
    val laneEnds = mutableListOf<LocalDateTime>()
    val out = mutableMapOf<String, Int>()
    items.sortedBy { maxOf(it.start, day.atStartOfDay()) }.forEach { it ->
        val start = maxOf(it.start, day.atStartOfDay())
        val idx = laneEnds.indexOfFirst { e -> !e.isAfter(start) }
        val lane = if (idx >= 0) idx else { laneEnds += it.end; laneEnds.lastIndex }
        laneEnds[lane] = maxOf(it.end, start.plusMinutes(30))
        out[it.key] = lane
    }
    return out
}

@Composable
private fun ItemBlock(item: CalItem, showTime: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = item.color.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, item.color.copy(alpha = 0.7f)), modifier = Modifier.fillMaxSize()
            .semantics { contentDescription = "${item.title}, ${HM.format(item.start)} to ${HM.format(item.end)}" }) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(item.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            if (showTime) Text("${HM.format(item.start)}–${HM.format(item.end)}" + if (item.subtitle.isNotBlank()) ", ${item.subtitle}" else "",
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ItemChip(item: CalItem, compact: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(6.dp), color = item.color.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth()) {
        Text(item.title, Modifier.padding(horizontal = 6.dp, vertical = if (compact) 2.dp else 6.dp),
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Day view extras: holiday banner and the day's note. */
@Composable
private fun DayExtras(vm: CalendarViewModel, s: CalState, d: LocalDate, onNote: () -> Unit) {
    val note = vm.notesOn(s, d)
    Surface(onClick = onNote, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs)) {
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.StickyNote2, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(note ?: "Add a note for this day", Modifier.padding(start = Space.sm), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (note == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ---------------- agenda ----------------

@Composable
private fun Agenda(vm: CalendarViewModel, s: CalState, from: LocalDate, onItem: (CalItem) -> Unit, onNote: (LocalDate) -> Unit) {
    val to = from.plusDays(60)
    val items = remember(s, from) { vm.itemsBetween(s, from, to) }
    val days = (0L..60L).map { from.plusDays(it) }.mapNotNull { d ->
        val list = items.filter { it.touches(d) }
        val note = vm.notesOn(s, d)
        if (list.isEmpty() && note == null) null else Triple(d, list, note)
    }
    LazyColumn(contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = 96.dp)) {
        if (days.isEmpty()) item {
            com.example.tracker.ui.components.EmptyState(Icons.Rounded.Add, "Nothing in the next 60 days",
                "Add an event with the button below, or switch on more layers.")
        }
        days.forEach { (d, list, note) ->
            item(key = "h_$d") {
                Text(AGENDA_DAY.format(d), style = MaterialTheme.typography.titleMedium,
                    color = if (d == LocalDate.now(Schedule.ZONE)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Space.lg, bottom = Space.xs))
            }
            if (note != null) item(key = "n_$d") {
                Row(Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable(role = androidx.compose.ui.semantics.Role.Button) { onNote(d) }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.StickyNote2, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(note, Modifier.padding(start = Space.sm), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
            items(list, key = { "${d}_${it.key}" }) { AgendaRow(it) { onItem(it) } }
        }
    }
}


private val MONTH_HEIGHT: Dp = 22.dp + 50.dp * 6

private fun viewIcon(v: CalView) = when (v) {
    CalView.MONTH -> Icons.Rounded.CalendarViewMonth
    CalView.WEEK -> Icons.Rounded.CalendarViewWeek
    CalView.DAY -> Icons.Rounded.CalendarViewDay
    CalView.AGENDA -> Icons.AutoMirrored.Rounded.ViewList
}

@Composable
private fun AgendaRow(it: CalItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (it.allDay) "All day" else HM.format(it.start), Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(it.color))
        Column(Modifier.weight(1f).padding(start = Space.md)) {
            Text(it.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(if (!it.allDay) "until ${HM.format(it.end)}" else null, it.subtitle.ifBlank { null }).joinToString(", ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Month view: the selected day's items under the grid, so a tap never leaves the month. */
@Composable
private fun SelectedDayList(
    vm: CalendarViewModel, s: CalState, day: LocalDate, today: LocalDate, modifier: Modifier,
    onItem: (CalItem) -> Unit, onNote: () -> Unit, onOpenDay: () -> Unit,
) {
    val items = remember(s, day) { vm.itemsBetween(s, day, day).filter { it.touches(day) }.sortedWith(compareBy({ !it.allDay }, { it.start })) }
    val note = vm.notesOn(s, day)
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        LazyColumn(contentPadding = PaddingValues(start = Space.lg, end = Space.sm, top = Space.sm, bottom = 96.dp)) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (day == today) "Today" else AGENDA_DAY.format(day),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onNote) {
                        Icon(Icons.AutoMirrored.Rounded.StickyNote2, if (note == null) "Add note" else "Edit note",
                            tint = if (note == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onOpenDay) { Icon(Icons.Rounded.CalendarViewDay, "Open day view") }
                }
            }
            if (note != null) item(key = "note") {
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onNote).padding(vertical = 6.dp))
            }
            if (items.isEmpty() && note == null) item(key = "empty") {
                Row(Modifier.padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EventAvailable, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    Text("Free day", Modifier.padding(start = Space.sm), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(items, key = { it.key }) { AgendaRow(it) { onItem(it) } }
        }
    }
}
