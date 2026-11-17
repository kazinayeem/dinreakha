package com.example.tracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.schedule.Reminder
import com.example.tracker.data.schedule.ReminderPlanner
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.SHORT_DATE
import com.example.tracker.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Debug view: the next alarm of every slot (what AlarmManager holds) + recently fired notifications. */
@Composable
fun NotificationLogScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val log by remember { c.schedule.observeLog() }.collectAsStateWithLifecycle(emptyList())
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val cfg by c.scheduleState.collectAsStateWithLifecycle()
    var upcoming by remember { mutableStateOf(emptyList<Reminder>()) }
    LaunchedEffect(settings, cfg) {
        upcoming = ReminderPlanner.nextPerSlot(LocalDateTime.now(Schedule.ZONE), c.scheduler.reminderConfig()).values.sortedBy { it.at }
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("Scheduled (next of each)") {
                if (upcoming.isEmpty()) Text("Nothing scheduled.")
                upcoming.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${SHORT_DATE.format(r.at)} ${HM.format(r.at)}", Modifier.weight(0.42f), fontWeight = FontWeight.SemiBold)
                        Text(r.title, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            SectionCard("Recently fired", trailing = { TextButton(onClick = { scope.launch { c.schedule.clearLog() } }) { Text("Clear") } }) {
                if (log.isEmpty()) Text("Nothing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                log.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${SHORT_DATE.format(e.firedAt)} ${HM.format(e.firedAt)}", Modifier.weight(0.42f), fontWeight = FontWeight.SemiBold)
                        Text(e.title, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
