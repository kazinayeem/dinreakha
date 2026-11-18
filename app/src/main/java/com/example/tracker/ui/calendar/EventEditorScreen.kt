package com.example.tracker.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tracker.container
import com.example.tracker.data.calendar.CalEvent
import com.example.tracker.data.calendar.Freq
import com.example.tracker.data.calendar.Recurrence
import com.example.tracker.data.calendar.Recurrences
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.DatePickerDialogCompat
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.LocalFeedback
import com.example.tracker.ui.components.Space
import com.example.tracker.ui.components.SwitchRow
import com.example.tracker.ui.components.TimePickerDialog
import com.example.tracker.ui.components.short
import com.example.tracker.ui.components.weekFrom
import com.example.tracker.ui.settings.Stepper
import com.example.tracker.ui.theme.Expense
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val EVENT_COLORS = listOf(0xFF4FA8E8, 0xFF2FB38A, 0xFFF2A93B, 0xFFE0605E, 0xFF9B7BE8, 0xFFF07AAE, 0xFF3FB5C9, 0xFF7C8AA5)
private val REMINDER_CHOICES = listOf(0, 10, 30, 60, 120, 1440, 2880)
private enum class Ends(val label: String) { NEVER("Never"), ON("On a date"), AFTER("After a number of times") }

/**
 * New / edit event. With [occurrenceDate] it edits ONLY that occurrence of a repeating event
 * (title, time, place, notes) — the repeat rule is hidden then.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditorScreen(eventId: Long?, date: LocalDate?, hour: Int?, minute: Int?, occurrenceDate: LocalDate?, onDone: () -> Unit) {
    val c = LocalContext.current.container
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val firstDay = DayOfWeek.SATURDAY
    val d0 = date ?: LocalDate.now(Schedule.ZONE)
    val t0 = if (hour != null) LocalTime.of(hour, minute ?: 0) else LocalTime.now(Schedule.ZONE).withMinute(0).withSecond(0).withNano(0).plusHours(1)

    var loaded by rememberSaveable { mutableStateOf(eventId == null) }
    var base by remember { mutableStateOf<CalEvent?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var allDay by rememberSaveable { mutableStateOf(false) }
    var startDate by rememberSaveable { mutableStateOf(d0.toString()) }
    var startTime by rememberSaveable { mutableStateOf(t0.toString()) }
    var endDate by rememberSaveable { mutableStateOf((if (t0.hour == 23) d0.plusDays(1) else d0).toString()) }
    var endTime by rememberSaveable { mutableStateOf(t0.plusHours(1).toString()) }
    var location by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(EVENT_COLORS.first()) }
    var freq by rememberSaveable { mutableStateOf(Freq.NONE) }
    var interval by rememberSaveable { mutableIntStateOf(1) }
    var byDays by rememberSaveable { mutableStateOf(emptySet<DayOfWeek>()) }
    var ends by rememberSaveable { mutableStateOf(Ends.NEVER) }
    var until by rememberSaveable { mutableStateOf(d0.plusMonths(1).toString()) }
    var count by rememberSaveable { mutableIntStateOf(10) }
    var reminders by rememberSaveable { mutableStateOf(setOf(30)) }
    var pick by remember { mutableStateOf<String?>(null) }
    // Saving is async; without this a quick second tap added the event twice.
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        if (eventId == null || loaded) return@LaunchedEffect
        val data = c.calendar.current()
        val ev = data.events.firstOrNull { it.id == eventId } ?: return@LaunchedEffect
        base = ev
        val occ = occurrenceDate?.let { od -> Recurrences.expand(ev, data.exceptions, od.minusDays(1), od.plusDays(1)).firstOrNull { it.originalDate == od } }
        val s = occ?.start ?: ev.start
        val e = occ?.end ?: ev.end
        title = occ?.title ?: ev.title; allDay = ev.allDay
        startDate = s.toLocalDate().toString(); startTime = s.toLocalTime().toString()
        endDate = (if (ev.allDay) e.toLocalDate().minusDays(1) else e.toLocalDate()).toString(); endTime = e.toLocalTime().toString()
        location = occ?.location ?: ev.location; notes = occ?.notes ?: ev.notes; color = ev.color
        freq = ev.recurrence.freq; interval = ev.recurrence.interval; byDays = ev.recurrence.byDays
        ends = when { ev.recurrence.until != null -> Ends.ON; ev.recurrence.count != null -> Ends.AFTER; else -> Ends.NEVER }
        ev.recurrence.until?.let { until = it.toString() }; ev.recurrence.count?.let { count = it }
        reminders = ev.reminders.toSet()
        loaded = true
    }
    if (!loaded) return

    val sDate = LocalDate.parse(startDate); val eDate = LocalDate.parse(endDate)
    val start = if (allDay) sDate.atStartOfDay() else sDate.atTime(LocalTime.parse(startTime))
    val end = if (allDay) eDate.plusDays(1).atStartOfDay() else eDate.atTime(LocalTime.parse(endTime))
    val timeOk = end.isAfter(start)
    val valid = title.isNotBlank() && timeOk
    val onlyThis = occurrenceDate != null

    fun doSave() = scope.launch {
        if (onlyThis) {
            c.calendar.saveOccurrence(base!!.id, occurrenceDate!!, title, start, end, location, notes)
        } else {
            val rec = if (freq == Freq.NONE) Recurrence.NONE else Recurrence(freq, interval, if (freq == Freq.WEEKLY) byDays else emptySet(),
                until = if (ends == Ends.ON) LocalDate.parse(until) else null, count = if (ends == Ends.AFTER) count else null)
            c.calendar.saveEvent(CalEvent(base?.id ?: 0, title.trim(), start, end, allDay, location, notes, color, rec, reminders.sorted()))
        }
        c.scheduler.rescheduleAll()
        feedback?.show(if (base == null) "Event added" else "Event saved")
        onDone()
    }

    fun save() {
        if (submitted) return
        submitted = true
        doSave()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onlyThis) {
                Text(
                    "Only ${DATE.format(occurrenceDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                placeholder = { Text("E.g. Study session, Project meeting") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )

            if (!onlyThis) SwitchRow("All day", null, allDay) { allDay = it }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateTimeRow(
                        "Starts",
                        DATE.format(sDate),
                        if (allDay) null else HM.format(LocalTime.parse(startTime)),
                        onDate = { pick = "sd" },
                        onTime = { pick = "st" },
                    )
                    DateTimeRow(
                        "Ends",
                        DATE.format(eDate),
                        if (allDay) null else HM.format(LocalTime.parse(endTime)),
                        onDate = { pick = "ed" },
                        onTime = { pick = "et" },
                    )
                    if (!timeOk) {
                        Text("The end has to be after the start.", color = Expense, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Place (optional)") },
                placeholder = { Text("E.g. Library, Room 402, Home") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes (optional)") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
            )

            if (!onlyThis) {
                Column {
                    Text("Colour", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        EVENT_COLORS.forEachIndexed { i, col ->
                            val sel = col == color
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { color = col }
                                    .semantics { role = Role.RadioButton; selected = sel; contentDescription = "Colour ${i + 1}" },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (sel) {
                                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Column {
                    Text("Repeats", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            Freq.NONE to "No",
                            Freq.DAILY to "Daily",
                            Freq.WEEKLY to "Weekly",
                            Freq.MONTHLY to "Monthly",
                            Freq.YEARLY to "Yearly",
                        ).forEach { (f, l) ->
                            FilterChip(
                                selected = freq == f,
                                onClick = {
                                    freq = f
                                    if (f == Freq.WEEKLY && byDays.isEmpty()) byDays = setOf(sDate.dayOfWeek)
                                },
                                label = { Text(l) },
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                }

                if (freq != Freq.NONE) {
                    if (freq == Freq.WEEKLY) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            weekFrom(firstDay).forEach { d ->
                                FilterChip(
                                    selected = d in byDays,
                                    onClick = { byDays = if (d in byDays) byDays - d else byDays + d },
                                    label = { Text(d.short()) },
                                    shape = RoundedCornerShape(10.dp),
                                )
                            }
                        }
                    }
                    val unit = when (freq) {
                        Freq.DAILY -> "day"
                        Freq.WEEKLY -> "week"
                        Freq.MONTHLY -> "month"
                        else -> "year"
                    }
                    Stepper(
                        "Every",
                        if (interval == 1) unit else "$interval ${unit}s",
                        onMinus = { interval = (interval - 1).coerceAtLeast(1) },
                        onPlus = { interval = (interval + 1).coerceAtMost(30) },
                    )
                    Text("Ends", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Ends.entries.forEach { e ->
                            FilterChip(
                                selected = ends == e,
                                onClick = { ends = e },
                                label = { Text(e.label) },
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                    when (ends) {
                        Ends.ON -> OutlinedButton(onClick = { pick = "until" }, Modifier.heightIn(min = Space.touch)) {
                            Text("Until ${DATE.format(LocalDate.parse(until))}")
                        }
                        Ends.AFTER -> Stepper(
                            "Times",
                            "$count",
                            onMinus = { count = (count - 1).coerceAtLeast(1) },
                            onPlus = { count = (count + 1).coerceAtMost(500) },
                        )
                        Ends.NEVER -> {}
                    }
                    if (freq == Freq.MONTHLY && sDate.dayOfMonth > 28) {
                        Text(
                            "Months without a ${sDate.dayOfMonth}th are skipped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column {
                    Text("Remind me", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        REMINDER_CHOICES.forEach { m ->
                            FilterChip(
                                selected = m in reminders,
                                onClick = { reminders = if (m in reminders) reminders - m else reminders + m },
                                label = { Text(reminderLabel(m).replaceFirstChar(Char::uppercase)) },
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = { save() },
            enabled = valid,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(54.dp),
        ) {
            Text(if (base == null) "Add Event" else "Save Changes", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }

    when (pick) {
        "sd" -> DatePickerDialogCompat(sDate, { nd -> val shift = java.time.temporal.ChronoUnit.DAYS.between(sDate, nd)
            startDate = nd.toString(); endDate = eDate.plusDays(shift).toString(); pick = null }, { pick = null })
        "ed" -> DatePickerDialogCompat(eDate, { endDate = it.toString(); pick = null }, { pick = null })
        "st" -> TimePickerDialog("Starts", LocalTime.parse(startTime), { nt ->
            val dur = java.time.Duration.between(start, end)
            startTime = nt.toString()
            val ne = sDate.atTime(nt).plus(dur); endDate = ne.toLocalDate().toString(); endTime = ne.toLocalTime().toString(); pick = null
        }, { pick = null })
        "et" -> TimePickerDialog("Ends", LocalTime.parse(endTime), { endTime = it.toString(); pick = null }, { pick = null })
        "until" -> DatePickerDialogCompat(LocalDate.parse(until), { until = it.toString(); pick = null }, { pick = null })
    }
}

@Composable
private fun DateTimeRow(label: String, date: String, time: String?, onDate: () -> Unit, onTime: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.22f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        OutlinedButton(onClick = onDate, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(0.50f)) { Text(date, maxLines = 1) }
        if (time != null) {
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = onTime, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(0.32f)) { Text(time, maxLines = 1) }
        }
    }
}

