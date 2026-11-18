package com.example.tracker.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.repo.AppSettings
import com.example.tracker.data.schedule.QuietHours
import com.example.tracker.data.schedule.QuietMode
import com.example.tracker.data.schedule.ReminderCategory
import com.example.tracker.data.schedule.ReminderPref
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.SoundMode
import com.example.tracker.data.schedule.Timing
import com.example.tracker.ui.components.DatePickerDialogCompat
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.SHORT_DATE
import com.example.tracker.ui.components.SectionCard
import com.example.tracker.ui.components.SwitchRow
import com.example.tracker.ui.components.TimePickerDialog
import com.example.tracker.ui.components.ValueRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun NotificationSettingsScreen(onOpenLog: () -> Unit = {}) {
    val ctx = LocalContext.current
    val c = ctx.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(Schedule.ZONE)
    var editing by remember { mutableStateOf<ReminderCategory?>(null) }
    var pickQuiet by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var pickHoliday by remember { mutableStateOf<String?>(null) } // "from" | "to"
    var holidayFrom by remember { mutableStateOf(s.holidayFrom ?: today) }
    var holidayTo by remember { mutableStateOf(s.holidayTo ?: today.plusDays(6)) }

    fun change(block: suspend () -> Unit) = scope.launch { block(); c.scheduler.rescheduleAll() }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                SwitchRow("All reminders", if (s.masterEnabled) "On" else "Everything is off — including the wake-up alarm",
                    s.masterEnabled) { on -> change { c.settings.setMaster(on) } }
                SwitchRow("Status bar countdown", "Silent always-on notification: next reminder + time left",
                    s.statusNotification, enabled = s.masterEnabled) { on -> change { c.settings.setStatusNotification(on) } }
            }
        }

        item {
            SectionCard("Pause") {
                SwitchRow("Mute today", "Silences everything except the wake-up alarm until midnight",
                    s.mutedDate == today, enabled = s.masterEnabled) { on -> change { c.settings.setMutedDate(if (on) today else null) } }
                val holidayOn = s.holidayFrom != null && s.holidayTo != null
                SwitchRow("Holiday mode", if (holidayOn) "No class & bus reminders ${SHORT_DATE.format(s.holidayFrom)} – ${SHORT_DATE.format(s.holidayTo)}"
                    else "Pause class & bus reminders for a date range (e.g. semester break)", holidayOn, enabled = s.masterEnabled) { on ->
                    change { c.settings.setHolidayMode(if (on) holidayFrom else null, if (on) holidayTo else null) }
                }
                if (holidayOn) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickHoliday = "from" }) { Text("From ${SHORT_DATE.format(s.holidayFrom)}") }
                    OutlinedButton(onClick = { pickHoliday = "to" }) { Text("To ${SHORT_DATE.format(s.holidayTo)}") }
                }
            }
        }

        item {
            SectionCard("Quiet hours") {
                androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    QuietMode.entries.forEach { m ->
                        FilterChip(s.quiet.mode == m, { change { c.settings.setQuiet(s.quiet.copy(mode = m)) } }, { Text(m.label, maxLines = 1) },
                            modifier = Modifier.heightIn(min = 44.dp))
                    }
                }
                if (s.quiet.mode == QuietMode.CUSTOM) {
                    ValueRow("Starts", HM.format(s.quiet.start)) { pickQuiet = "start" }
                    ValueRow("Ends", HM.format(s.quiet.end)) { pickQuiet = "end" }
                }
            }
        }

        item {
            SectionCard("Wake-up snooze") {
                Text("Snooze length", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { m ->
                        FilterChip(s.snoozeMin == m, { change { c.settings.setSnooze(m, s.snoozeMax) } }, { Text("$m min", maxLines = 1) },
                            modifier = Modifier.heightIn(min = 44.dp))
                    }
                }
                Text("Max snoozes per morning", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2, 3, 5).forEach { n ->
                        FilterChip(s.snoozeMax == n, { change { c.settings.setSnooze(s.snoozeMin, n) } }, { Text(if (n == 0) "None" else "$n") },
                            modifier = Modifier.heightIn(min = 44.dp))
                    }
                }
            }
        }

        item {
            SectionCard("Reminder types") {
                ReminderCategory.entries.forEachIndexed { i, cat ->
                    if (i > 0) HorizontalDivider()
                    CategoryRow(cat, s, enabled = s.masterEnabled, onClick = { editing = cat }) { on ->
                        change { c.settings.setPref(cat, s.pref(cat).copy(enabled = on)) }
                    }
                }
            }
        }
        // The log belongs with the reminders it records (it used to be a separate Settings row).
        item {
            SectionCard {
                NavRow(Icons.Rounded.History, "Notification log", "What was sent, and when") { onOpenLog() }
            }
        }
    }

    editing?.let { cat -> CategoryDialog(cat, s.pref(cat), onDismiss = { editing = null }, onSave = { p ->
        change { c.settings.setPref(cat, p) }; editing = null
    }, onTest = {
        scope.launch { c.settings.setPref(cat, it); c.scheduler.rescheduleAll(); c.scheduler.scheduleTest(cat) }
        com.example.tracker.ui.components.showMessage(ctx, "Test arrives in ~5 s")
    }) }

    pickQuiet?.let { which ->
        TimePickerDialog(if (which == "start") "Quiet hours start" else "Quiet hours end",
            if (which == "start") s.quiet.start else s.quiet.end,
            onConfirm = { t ->
                val q: QuietHours = if (which == "start") s.quiet.copy(start = t) else s.quiet.copy(end = t)
                change { c.settings.setQuiet(q) }; pickQuiet = null
            }, onDismiss = { pickQuiet = null })
    }
    pickHoliday?.let { which ->
        DatePickerDialogCompat(if (which == "from") holidayFrom else holidayTo, onConfirm = { d ->
            if (which == "from") holidayFrom = d else holidayTo = d
            change { c.settings.setHolidayMode(if (which == "from") d else s.holidayFrom ?: d, if (which == "to") d else s.holidayTo ?: d) }
            pickHoliday = null
        }, onDismiss = { pickHoliday = null })
    }
}

private fun timingText(cat: ReminderCategory, p: ReminderPref): String = when (cat.timing) {
    Timing.OFFSET -> if (p.offsetMin == 0) "At the time" else "${p.offsetMin} min before"
    Timing.FIXED_TIME -> "At ${HM.format(p.time ?: cat.defaultTime ?: LocalTime.NOON)}"
    Timing.PLAN -> when (cat) {
        ReminderCategory.WAKE -> "At your wake target"
        ReminderCategory.SCREENS_OFF -> "Before bedtime (set in Schedule)"
        ReminderCategory.BEDTIME -> "At bedtime"
        ReminderCategory.ROUTINE -> "At each routine's time"
        ReminderCategory.CALENDAR -> "As set on each event"
        else -> ""
    }
}

@Composable
private fun CategoryRow(cat: ReminderCategory, s: AppSettings, enabled: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val p = s.pref(cat)
    Surface(onClick = onClick, color = Color.Transparent, enabled = enabled) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cat.label, style = MaterialTheme.typography.bodyLarge)
                Text("${timingText(cat, p)}, ${p.sound.label.lowercase()}${if (p.ringtone != null && p.sound == SoundMode.SOUND) ", custom tone" else ""}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(p.enabled, onToggle, enabled = enabled)
        }
    }
}

@Composable
private fun CategoryDialog(
    cat: ReminderCategory, initial: ReminderPref,
    onDismiss: () -> Unit, onSave: (ReminderPref) -> Unit, onTest: (ReminderPref) -> Unit,
) {
    val ctx = LocalContext.current
    var p by remember(cat) { mutableStateOf(initial) }
    var offset by remember(cat) { mutableFloatStateOf(initial.offsetMin.toFloat()) }
    var pickTime by remember { mutableStateOf(false) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            p = p.copy(ringtone = uri?.toString())
        }
    }
    val ringtoneName = remember(p.ringtone) {
        p.ringtone?.let { runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(it))?.getTitle(ctx) }.getOrNull() } ?: "Default"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(cat.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow("On", null, p.enabled) { p = p.copy(enabled = it) }
                when (cat.timing) {
                    Timing.OFFSET -> {
                        Text(if (offset.toInt() == 0) "At the time" else "${offset.toInt()} min before",
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        Slider(offset, { offset = (it / 5).toInt() * 5f }, valueRange = 0f..120f, steps = 23)
                    }
                    Timing.FIXED_TIME -> ValueRow("Time", HM.format(p.time ?: cat.defaultTime ?: LocalTime.NOON)) { pickTime = true }
                    Timing.PLAN -> Text(timingText(cat, p), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
                Gap(8.dp)
                Text("Sound", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoundMode.entries.forEach { m -> FilterChip(p.sound == m, { p = p.copy(sound = m) }, { Text(m.label, maxLines = 1) }) }
                }
                if (p.sound == SoundMode.SOUND) {
                    ValueRow("Ringtone", ringtoneName) {
                        val type = if (cat == ReminderCategory.WAKE) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
                        ringtoneLauncher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type or RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, p.ringtone?.let(Uri::parse)))
                    }
                }
                Gap(8.dp)
                FilledTonalButton(onClick = { onTest(p.copy(offsetMin = offset.toInt())) }) { Text("▶ Test this notification") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(p.copy(offsetMin = offset.toInt())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickTime) TimePickerDialog(cat.label, p.time ?: cat.defaultTime ?: LocalTime.NOON,
        onConfirm = { p = p.copy(time = it); pickTime = false }, onDismiss = { pickTime = false })
}
