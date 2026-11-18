package com.example.tracker.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import com.example.tracker.data.weather.BANGLADESH_CITIES
import com.example.tracker.data.weather.LocationHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.tracker.ui.components.IconBadge
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.quotes.MotivationQuotes
import com.example.tracker.data.repo.LockScope
import com.example.tracker.data.repo.QuoteCategory
import com.example.tracker.data.repo.QuoteLanguage
import com.example.tracker.data.repo.ThemeMode
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.TimeFormat
import com.example.tracker.data.schedule.ReminderCategory
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.SectionCard
import com.example.tracker.ui.components.SwitchRow
import com.example.tracker.ui.components.long
import com.example.tracker.ui.theme.PALETTES
import com.example.tracker.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Settings is a short hub; the long groups live on their own pages. */
enum class SettingsPage { HUB, LOOK, PRIVACY }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(page: SettingsPage = SettingsPage.HUB, nav: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val perms = rememberPermissionStatus(ctx)
    val today = LocalDate.now(Schedule.ZONE)
    // The picked backup, already read and checked: (file text, what's in it).
    var confirmImport by remember { mutableStateOf<Pair<String, com.example.tracker.data.backup.BackupCodec.Contents>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    fun change(block: suspend () -> Unit) = scope.launch { block(); c.scheduler.rescheduleAll() }
    fun toast(msg: String) = com.example.tracker.ui.components.showMessage(ctx, msg, long = true)

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = c.backup.exportJson()
                withContext(Dispatchers.IO) { writeTruncating(ctx, uri, text) }
            }.onSuccess { toast("Backup saved") }.onFailure { toast("Export failed: ${it.message}") }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = c.backup.exportTransactionsCsv()
                withContext(Dispatchers.IO) { writeTruncating(ctx, uri, text) }
            }.onSuccess { toast("CSV saved") }.onFailure { toast("Export failed: ${it.message}") }
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Read and check the whole file first: a bad file is reported here and nothing is erased.
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    (ctx.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")).bufferedReader().use { it.readText() }
                }
                text to withContext(Dispatchers.Default) { c.backup.inspect(text) }
            }.onSuccess { confirmImport = it }
                .onFailure { toast("Can't restore this file: ${it.message}") }
        }
    }
    val hasLocPerm = LocationHelper.hasLocationPermission(ctx)
    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.any { it }) {
            scope.launch { c.weather.refresh(force = true) }
        }
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        if (page == SettingsPage.HUB && (!perms.notifications || !perms.exactAlarms || !perms.batteryUnrestricted)) item {
            SectionCard("Reminders may not arrive", icon = Icons.Rounded.WarningAmber) {
                PermRow("Notifications", perms.notifications) { Permissions.open(ctx, Permissions.notificationSettings(ctx)) }
                PermRow("Exact alarms", perms.exactAlarms) { Permissions.open(ctx, Permissions.exactAlarmSettings(ctx)) }
                PermRow("Battery optimisation off", perms.batteryUnrestricted) { Permissions.open(ctx, Permissions.batteryOptimization(ctx)) }
            }
        }

        if (page == SettingsPage.HUB) item {
            // One short list: each row opens one page. Nothing else lives here.
            SectionCard {
                NavRow(Icons.Rounded.TaskAlt, "Habits & routines", "What's on your daily checklist") { nav("settings/routines") }
                NavRow(Icons.Rounded.EditCalendar, "Schedule & classes", "Plan, wake & bed times, classes, bus") { nav("settings/schedule") }
                val off = s.prefs.count { !it.value.enabled }
                NavRow(Icons.Rounded.NotificationsActive, "Notifications",
                    if (!s.masterEnabled) "All reminders are OFF" else if (off == 0) "All on" else "$off types off") { nav("settings/notifications") }
                NavRow(Icons.Rounded.Palette, "Look & language", "Theme, language, quotes, weather, time") { nav("settings/look") }
                NavRow(Icons.Rounded.Lock, "Privacy & data", "App lock, backup, reset") { nav("settings/privacy") }
                NavRow(Icons.Rounded.Info, "About", null) { nav("about") }
            }
        }

        if (page == SettingsPage.LOOK) {
            item {
                SectionCard("Language", icon = Icons.Rounded.Translate) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.example.tracker.ui.i18n.AppLanguage.entries.forEach { l ->
                            FilterChip(
                                selected = s.language == l,
                                onClick = { scope.launch { c.settings.setLanguage(l); c.rescheduleAsync() } },
                                label = { Text(l.label) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    if (s.language == com.example.tracker.ui.i18n.AppLanguage.BANGLA) {
                        SwitchRow("Bangla digits", "Show numbers as ১২৩", s.banglaDigits) { on ->
                            scope.launch { c.settings.setBanglaDigits(on) }
                        }
                    }
                }
            }

            item {
                SectionCard("Appearance", icon = Icons.Rounded.Palette) {
                    Text("Theme", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(s.themeMode == m, { scope.launch { c.settings.setThemeMode(m) } }, { Text(m.label) },
                                modifier = Modifier.heightIn(min = 44.dp))
                        }
                    }
                    Gap()
                    Text("Colour palette", style = MaterialTheme.typography.labelLarge)
                    Gap(6.dp)
                    // Two tiles per row, sized to the card (works at any font size / screen width).
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PALETTES.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { p ->
                                    Box(Modifier.weight(1f)) {
                                        PaletteTile(p, selected = s.palette == p.key) { scope.launch { c.settings.setPalette(p.key) } }
                                    }
                                }
                                if (row.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                SectionCard("Splash inspiration", icon = Icons.Rounded.AutoAwesome) {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuoteCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = s.quoteCategory == cat,
                                onClick = { scope.launch { c.settings.setQuoteCategory(cat); c.rescheduleAsync() } },
                                label = { Text(cat.label) },
                                modifier = Modifier.heightIn(min = 44.dp),
                            )
                        }
                    }
                    Gap(8.dp)
                    Text("Language", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuoteLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = s.quoteLanguage == lang,
                                onClick = { scope.launch { c.settings.setQuoteLanguage(lang); c.rescheduleAsync() } },
                                label = { Text(lang.label) },
                                modifier = Modifier.heightIn(min = 44.dp),
                            )
                        }
                    }
                    Gap(10.dp)
                    var previewQuote by remember(s.quoteCategory, s.quoteLanguage) {
                        mutableStateOf(MotivationQuotes.getRandomQuote(s.quoteCategory, s.quoteLanguage))
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Launch Preview",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = {
                                        previewQuote = MotivationQuotes.getRandomQuote(s.quoteCategory, s.quoteLanguage)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = com.example.tracker.ui.i18n.tr("Shuffle"), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Shuffle")
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "“${previewQuote.text}”",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "— ${previewQuote.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Daily motivation reminders: morning (15 min after waking), midday, evening.
                    Gap(10.dp)
                    val mot = s.pref(ReminderCategory.MOTIVATION)
                    var pickMot by remember { mutableStateOf<String?>(null) }
                    SwitchRow("Daily motivation", "3 a day: morning, midday, evening", mot.enabled) { on ->
                        scope.launch { c.settings.setEnabled(ReminderCategory.MOTIVATION, on); c.rescheduleAsync() }
                    }
                    if (mot.enabled) {
                        com.example.tracker.ui.components.ValueRow("Morning", "15 min after waking") {}
                        com.example.tracker.ui.components.ValueRow("Midday", com.example.tracker.data.schedule.TimeFormat.format(mot.time ?: java.time.LocalTime.of(13, 30))) { pickMot = "mid" }
                        com.example.tracker.ui.components.ValueRow("Evening", com.example.tracker.data.schedule.TimeFormat.format(s.motivationEvening)) { pickMot = "eve" }
                    }
                    when (pickMot) {
                        "mid" -> com.example.tracker.ui.components.TimePickerDialog("Midday quote", mot.time ?: java.time.LocalTime.of(13, 30), { t ->
                            scope.launch { c.settings.setPref(ReminderCategory.MOTIVATION, mot.copy(time = t)); c.rescheduleAsync() }; pickMot = null
                        }, { pickMot = null })
                        "eve" -> com.example.tracker.ui.components.TimePickerDialog("Evening quote", s.motivationEvening, { t ->
                            scope.launch { c.settings.setMotivationEvening(t); c.rescheduleAsync() }; pickMot = null
                        }, { pickMot = null })
                    }
                }
            }

            item {
                SectionCard("Weather & air index", icon = Icons.Rounded.WbSunny) {
                    SwitchRow(
                        "Show on home screen",
                        "Real-time temperature, condition, and AQI from Open-Meteo.",
                        s.weatherEnabled,
                    ) { on ->
                        scope.launch {
                            c.settings.setWeatherEnabled(on)
                            if (on) c.weather.refresh(force = true)
                        }
                    }
                    if (s.weatherEnabled) {
                        Gap(6.dp)
                        Text("Location / শহর", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = hasLocPerm && s.weatherCity !in BANGLADESH_CITIES.map { it.nameEn },
                                onClick = {
                                    if (!hasLocPerm) {
                                        locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    } else {
                                        scope.launch { c.weather.refresh(force = true) }
                                    }
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (hasLocPerm) "Auto GPS (${s.weatherCity})" else "Detect location")
                                    }
                                },
                                modifier = Modifier.heightIn(min = 44.dp),
                            )
                            BANGLADESH_CITIES.forEach { city ->
                                FilterChip(
                                    selected = s.weatherCity == city.nameEn,
                                    onClick = {
                                        scope.launch {
                                            c.settings.setWeatherLocation(city.nameEn, city.lat, city.lon)
                                            c.weather.refresh(force = true)
                                        }
                                    },
                                    label = { Text("${city.nameEn} • ${city.nameBn}") },
                                    modifier = Modifier.heightIn(min = 44.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard("Time & calendar", icon = Icons.Rounded.Schedule) {
                    SwitchRow("24-hour time",
                        "Off = 12-hour with AM/PM. Applies to every screen, notification and time picker.", s.use24h) { on ->
                        change { c.settings.setUse24h(on) }
                    }
                    Text("First day of week", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY).forEach { d ->
                            FilterChip(s.firstDayOfWeek == d, { scope.launch { c.settings.setFirstDayOfWeek(d) } }, { Text(d.long()) },
                                modifier = Modifier.heightIn(min = 44.dp))
                        }
                    }
                }
            }
        }

        if (page == SettingsPage.PRIVACY) {
            item {
                SectionCard("App lock", icon = Icons.Rounded.Lock) {
                    LockScope.entries.forEach { sc ->
                        SwitchRow("Protect ${sc.label.lowercase()}", null, sc in s.lockScopes) { on ->
                            scope.launch { c.settings.setLockScope(sc, on) }
                        }
                    }
                }
            }

            item {
                SectionCard("Backup", icon = Icons.Rounded.Backup) {
                    Gap(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { exportJson.launch("dinrekha-backup-$today.json") }) { Text("Export JSON") }
                        OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }
                    }
                    Gap(8.dp)
                    OutlinedButton(onClick = { exportCsv.launch("dinrekha-transactions-$today.csv") }) { Text("Export transactions CSV") }
                }
            }

            item {
                SectionCard("Danger zone", icon = Icons.Rounded.DeleteForever) {
                    Text(
                        "Factory Reset / সম্পূর্ণ রিসেট",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Gap(4.dp)
                    Text(
                        "Wipes all habit logs, wallet transactions, accounts, custom routines, calendar events, focus history, and preferences. Returns the app to a completely fresh, blank state as if newly installed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Gap(12.dp)
                    OutlinedButton(
                        onClick = { confirmReset = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset full app (clear all)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    confirmImport?.let { (text, contents) ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "Everything in Dinrekha is replaced by this backup" +
                        (contents.exportedAt?.let { " from ${it.take(16).replace('T', ' ')}" } ?: "") +
                        ":\n" + contents.summary() + ".\n\nClass-note photos are not inside backups; they stay on this phone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    // App scope: a restore can change the screen under us; the reschedule must still finish.
                    c.appScope.launch {
                        runCatching {
                            c.backup.importJson(text)
                            c.scheduler.rescheduleAll()
                        }.let { r -> withContext(Dispatchers.Main) { r.onSuccess { toast("Backup restored") }.onFailure { toast("Import failed: ${it.message}") } } }
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancel") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = { Text("Reset to completely blank app?") },
            text = {
                Text(
                    "This permanently erases ALL data from Dinrekha:\n\n" +
                    "• All habit checks & daily logs\n" +
                    "• All wallet transactions, recurring & budgets\n" +
                    "• All calendar events & notes\n" +
                    "• All custom routines & schedules\n" +
                    "• All settings, theme & inspiration preferences\n\n" +
                    "The app will return to the initial onboarding screen as a fresh, blank install. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        // App scope: the reset sends the app to onboarding, which disposes this screen
                        // and used to cancel the reschedule — alarms for erased reminders kept firing.
                        c.appScope.launch {
                            val r = runCatching {
                                c.backup.resetToBlankApp()
                                c.scheduler.rescheduleAll()
                            }
                            withContext(Dispatchers.Main) {
                                r.onSuccess { toast("App reset to blank") }.onFailure { toast("Reset failed: ${it.message}") }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Erase everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PaletteTile(p: Palette, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${p.name} colour palette" + if (selected) ", selected" else "" },
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                p.preview.forEach { col -> Box(Modifier.size(20.dp).clip(CircleShape).background(col)) }
            }
            Text((if (selected) "✓ " else "") + p.name, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermRow(title: String, ok: Boolean, fix: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, contentDescription = if (ok) "OK" else "Needs attention",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text(title, Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge)
        if (!ok) FilledTonalButton(onClick = fix) { Text("Fix") }
    }
}

/** Writes [text] to a document, replacing everything in it ("wt": plain "w" doesn't truncate on every provider). */
private fun writeTruncating(ctx: android.content.Context, uri: android.net.Uri, text: String) {
    val out = runCatching { ctx.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: ctx.contentResolver.openOutputStream(uri)
        ?: error("Couldn't open the file for writing")
    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}
