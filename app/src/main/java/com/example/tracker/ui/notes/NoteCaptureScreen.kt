package com.example.tracker.ui.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.db.ClassNoteEntity
import com.example.tracker.data.db.CourseEntity
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

val NOTE_TAGS = listOf("Lecture", "Board", "Slide", "Assignment", "Exam hint", "Important")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteCaptureScreen(
    prefilledCourseId: Long? = null,
    initialSharedUris: List<Uri> = emptyList(),
    onSharedConsumed: () -> Unit = {},
    onOpenCourses: () -> Unit = {},
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val courses by remember { c.notes.observeCourses(archived = false) }.collectAsStateWithLifecycle(emptyList())

    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now(com.example.tracker.data.schedule.Schedule.ZONE)) }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>() }
    var isImportant by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Saveable: the camera app often kills ours in the background; the photos taken must survive that.
    val capturedUris = rememberSaveable(
        saver = listSaver<SnapshotStateList<Uri>, Uri>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf() }

    // Add shared uris from external apps (WhatsApp, Messenger, etc.), then tell the caller they're taken.
    LaunchedEffect(initialSharedUris) {
        if (initialSharedUris.isNotEmpty()) {
            initialSharedUris.forEach { if (it !in capturedUris) capturedUris.add(it) }
            onSharedConsumed()
        }
    }

    // Auto-detect currently running or recent class
    LaunchedEffect(courses) {
        if (selectedCourse == null && courses.isNotEmpty()) {
            if (prefilledCourseId != null) {
                selectedCourse = courses.firstOrNull { it.id == prefilledCourseId }
            } else {
                val detected = c.notes.detectCurrentClass()
                if (detected != null) {
                    selectedCourse = detected.course
                    selectedDate = LocalDate.now(com.example.tracker.data.schedule.Schedule.ZONE)
                } else {
                    selectedCourse = courses.firstOrNull()
                }
            }
        }
    }

    // Camera launcher
    var tempCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            capturedUris.add(tempCameraUri!!)
            tempCameraUri = null
        }
    }

    fun launchCamera() {
        val camDir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val tmpFile = File(camDir, "cam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", tmpFile)
        tempCameraUri = uri
        cameraLauncher.launch(uri)
    }

    // Photo picker launcher
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)) { uris ->
        if (uris.isNotEmpty()) {
            capturedUris.addAll(uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Active Class Indicator banner if class is in session
        val activeClass = remember(courses) { c.notes }
        // Header Course selector
        SectionCard("Course / বিষয়", icon = Icons.Rounded.School) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (courses.isEmpty()) {
                    // Without a course the note can't be saved — offer the way out instead of a dead end.
                    Text(
                        "No courses yet. Add one to file this note under.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    TextButton(onClick = onOpenCourses) { Text("Add course") }
                }
                courses.forEach { course ->
                    val isSel = selectedCourse?.id == course.id
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedCourse = course },
                        label = { Text("${course.code}: ${course.name}") },
                        leadingIcon = if (isSel) { { Icon(Icons.Rounded.Check, null) } } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(course.color).copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.heightIn(min = 44.dp),
                    )
                }
            }
            Gap(4.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Date: ${selectedDate.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Change date")
                }
            }
        }

        // Photo Capture & Preview Gallery
        SectionCard("Photos & Whiteboard (${capturedUris.size})", icon = Icons.Rounded.CameraAlt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { launchCamera() },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Take photo")
                }
                OutlinedButton(
                    onClick = { pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("From gallery")
                }
            }

            if (capturedUris.isNotEmpty()) {
                Gap(10.dp)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(capturedUris) { idx, uri ->
                        PhotoThumbnailItem(
                            uri = uri,
                            onRemove = { capturedUris.removeAt(idx) },
                        )
                    }
                }
            }
        }

        // Note Details Card
        SectionCard("Note Details", icon = Icons.Rounded.School) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Topic / Title (optional)") },
                placeholder = { Text("e.g. Heuristic Search, Graph Algorithm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Gap(8.dp)

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Notes / Formulas / Transcript") },
                placeholder = { Text("Write formulas, hints, teacher's remarks...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp),
                maxLines = 10,
            )

            Gap(10.dp)
            Text("Tags", style = MaterialTheme.typography.labelMedium)
            Gap(4.dp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NOTE_TAGS.forEach { tag ->
                    val sel = tag in selectedTags
                    FilterChip(
                        selected = sel,
                        onClick = {
                            if (sel) selectedTags.remove(tag) else selectedTags.add(tag)
                            if (tag == "Important") isImportant = !sel
                        },
                        label = { Text(tag) },
                    )
                }
            }
        }

        // Save Action Button
        Button(
            onClick = {
                val course = selectedCourse
                if (course == null) {
                    Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Please select a course"), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (text.isBlank() && title.isBlank() && capturedUris.isEmpty()) {
                    Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Add at least a photo or some notes"), Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isSaving = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    val noteEntity = ClassNoteEntity(
                        courseId = course.id,
                        date = selectedDate,
                        title = title.trim().ifBlank { null },
                        text = text.trim(),
                        tags = selectedTags.joinToString(","),
                        important = isImportant || ("Important" in selectedTags),
                    )
                    val result = runCatching { c.notes.saveNote(noteEntity, capturedUris.toList(), course.code) }
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Note saved with ${capturedUris.size} photos"), Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            // Leave the screen usable so the note can be saved again.
                            isSaving = false
                            Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Couldn't save the note") + ": ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                if (isSaving) "Saving note & processing photos…" else "Save Note (${capturedUris.size} photos)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        selectedDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dpState)
        }
    }
}

@Composable
private fun PhotoThumbnailItem(
    uri: Uri,
    onRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val input = ctx.contentResolver.openInputStream(uri)
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(input, null, opts)
            }.getOrNull()?.let {
                bitmap = it
            }
        }
    }

    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Delete button badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
