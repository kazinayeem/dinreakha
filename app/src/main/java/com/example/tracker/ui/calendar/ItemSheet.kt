package com.example.tracker.ui.calendar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.LocalFeedback
import com.example.tracker.ui.components.Space
import com.example.tracker.ui.theme.Expense
import java.io.File
import java.time.format.DateTimeFormatter

private val WHEN = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** Details of a tapped item, with the actions that make sense for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(item: CalItem, vm: CalendarViewModel, nav: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val feedback = LocalFeedback.current
    var ask by remember { mutableStateOf<String?>(null) } // "edit" | "delete"
    val o = item.occurrence
    val repeats = o?.event?.recurrence?.repeats == true

    val notesForDate by remember(item.start.toLocalDate()) { c.notes.observeNotesForDate(item.start.toLocalDate()) }.collectAsStateWithLifecycle(emptyList())
    val courses by remember { c.notes.observeCourses() }.collectAsStateWithLifecycle(emptyList())

    val matchedCourse = courses.firstOrNull {
        it.code.equals(item.title, ignoreCase = true) ||
            it.name.contains(item.title, ignoreCase = true) ||
            item.title.contains(it.code, ignoreCase = true)
    }
    val classNotes = if (matchedCourse != null) {
        notesForDate.filter { it.note.courseId == matchedCourse.id }
    } else {
        emptyList()
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl).padding(bottom = Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(item.color))
                Text(item.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = Space.md))
            }
            Text(
                WHEN.format(item.start) + if (item.allDay) ", all day" else ", ${HM.format(item.start)} to ${HM.format(item.end)}",
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.sm),
            )
            if (repeats) Text(o!!.event.recurrence.describe() + if (o.modified) " (this one was changed)" else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Space.xs))
            o?.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
            }
            if (o != null && o.event.reminders.isNotEmpty()) Text(
                "Reminders: " + o.event.reminders.joinToString(", ") { reminderLabel(it) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm))

            // Class Notes & Photos Section
            if (item.scheduleType == PlanCalendar.EventType.CLASS || classNotes.isNotEmpty() || matchedCourse != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Class Notes & Photos",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    TextButton(
                        onClick = {
                            onClose()
                            val cid = matchedCourse?.id ?: 0L
                            nav(if (cid > 0) "notes/capture?courseId=$cid" else "notes/capture")
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add photo")
                    }
                }

                if (classNotes.isNotEmpty()) {
                    classNotes.forEach { noteWithImgs ->
                        val n = noteWithImgs.note
                        val imgs = noteWithImgs.images
                        if (n.title?.isNotBlank() == true || n.text.isNotBlank()) {
                            Text(
                                text = n.title?.takeIf { it.isNotBlank() } ?: n.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        if (imgs.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            ) {
                                itemsIndexed(imgs) { idx, img ->
                                    val file = File(img.thumbPath.takeIf { File(it).exists() } ?: img.filePath)
                                    val bmp = remember(file.absolutePath) {
                                        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Note photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    onClose()
                                                    nav("notes/viewer/${n.id}/$idx")
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No photos captured for this class yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            Row(Modifier.padding(top = Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                when (item.kind) {
                    ItemKind.EVENT -> {
                        FilledTonalButton(onClick = { if (repeats) ask = "edit" else { onClose(); nav("calendar/edit?id=${o!!.event.id}") } },
                            Modifier.heightIn(min = Space.touch)) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Text(" Edit") }
                        if (repeats && o!!.modified) OutlinedButton(onClick = { vm.restore(o); onClose() }, Modifier.heightIn(min = Space.touch)) {
                            Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp)); Text(" Undo change")
                        }
                        if (repeats) OutlinedButton(onClick = {
                            vm.skip(o!!); onClose()
                            feedback?.show("Skipped ${WHEN.format(o.originalDate)}", "Undo") { vm.restore(o) }
                        }, Modifier.heightIn(min = Space.touch)) { Icon(Icons.Rounded.EventBusy, null, Modifier.size(18.dp)); Text(" Skip this one") }
                    }
                    ItemKind.SCHEDULE -> FilledTonalButton(onClick = { onClose(); nav("settings/schedule") }, Modifier.heightIn(min = Space.touch)) {
                        Text("Edit in Schedule")
                    }
                    ItemKind.HOLIDAY -> FilledTonalButton(onClick = { onClose(); nav("settings/schedule") }, Modifier.heightIn(min = Space.touch)) {
                        Text("Edit holidays")
                    }
                }
            }
            if (item.kind == ItemKind.EVENT) TextButton(onClick = { if (repeats) ask = "delete" else { vm.deleteEvent(o!!.event.id); onClose() } },
                Modifier.heightIn(min = Space.touch)) { Text(if (repeats) "Delete…" else "Delete event", color = Expense) }
        }
    }

    ask?.let { what ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(if (what == "edit") "Edit which events?" else "Delete which events?") },
            text = { Text(WHEN.format(o!!.originalDate)) },
            confirmButton = {
                TextButton(onClick = {
                    ask = null; onClose()
                    if (what == "edit") nav("calendar/edit?id=${o!!.event.id}") else vm.deleteEvent(o!!.event.id)
                }) { Text(if (what == "edit") "All events" else "Delete all", color = if (what == "delete") Expense else MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    ask = null; onClose()
                    if (what == "edit") nav("calendar/edit?id=${o!!.event.id}&occ=${o.originalDate}")
                    else { vm.skip(o!!); feedback?.show("Deleted ${WHEN.format(o.originalDate)}", "Undo") { vm.restore(o) } }
                }) { Text("Only this one") }
            },
        )
    }
}

fun reminderLabel(m: Int): String = when {
    m == 0 -> "at the start"
    m < 60 -> "$m min before"
    m % 1440 == 0 -> if (m == 1440) "1 day before" else "${m / 1440} days before"
    m % 60 == 0 -> if (m == 60) "1 hour before" else "${m / 60} hours before"
    else -> "$m min before"
}
