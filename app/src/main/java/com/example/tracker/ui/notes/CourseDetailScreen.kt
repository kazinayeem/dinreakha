package com.example.tracker.ui.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.db.ClassNoteWithImages
import com.example.tracker.data.db.CourseEntity
import com.example.tracker.data.db.NoteImageEntity
import com.example.tracker.ui.components.Gap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseDetailScreen(
    courseId: Long,
    onBack: () -> Unit,
    onCaptureNote: () -> Unit,
    onOpenImageViewer: (noteId: Long, imageIndex: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()

    var course by remember { mutableStateOf<CourseEntity?>(null) }
    LaunchedEffect(courseId) {
        course = c.notes.getCourse(courseId)
    }

    val allNotes by remember(courseId) { c.notes.observeNotesForCourse(courseId) }.collectAsStateWithLifecycle(emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var importantOnly by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<Long?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }

    // Filter notes
    val filteredNotes = allNotes.filter { n ->
        val matchesQuery = searchQuery.isBlank() ||
            (n.note.title?.contains(searchQuery, ignoreCase = true) == true) ||
            n.note.text.contains(searchQuery, ignoreCase = true) ||
            n.note.tags.contains(searchQuery, ignoreCase = true) ||
            n.images.any { it.ocrText?.contains(searchQuery, ignoreCase = true) == true }

        val matchesTag = selectedTagFilter == null || selectedTagFilter in n.note.tags.split(",")
        val matchesImportant = !importantOnly || n.note.important

        matchesQuery && matchesTag && matchesImportant
    }

    // Group notes by date
    val groupedNotes = filteredNotes.groupBy { it.note.date }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Course Header Card
            item {
                val crs = course
                if (crs != null) {
                    val col = Color(crs.color)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(col.copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = crs.code,
                                        fontWeight = FontWeight.Bold,
                                        color = col,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        if (allNotes.isEmpty()) {
                                            Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("No notes to export"), Toast.LENGTH_SHORT).show()
                                            return@TextButton
                                        }
                                        isExportingPdf = true
                                        scope.launch {
                                            runCatching {
                                                val pdf = c.notes.exportCourseNotesPdf(courseId)
                                                withContext(Dispatchers.Main) {
                                                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", pdf)
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    ctx.startActivity(Intent.createChooser(intent, "Share course notes PDF"))
                                                }
                                            }.onFailure {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("PDF export failed: ${it.message}"), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            isExportingPdf = false
                                        }
                                    },
                                    enabled = !isExportingPdf,
                                ) {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isExportingPdf) "Generating…" else "Export PDF")
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = crs.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            if (!crs.teacher.isNullOrBlank() || !crs.room.isNullOrBlank()) {
                                val det = listOfNotNull(crs.teacher?.ifBlank { null }, crs.room?.ifBlank { null }).joinToString(", ")
                                Text(
                                    text = det,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar & Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search text, tags, OCR...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedTagFilter == null && !importantOnly,
                            onClick = { selectedTagFilter = null; importantOnly = false },
                            label = { Text("All (${allNotes.size})") },
                        )
                        FilterChip(
                            selected = importantOnly,
                            onClick = { importantOnly = !importantOnly },
                            label = { Text("⭐ Important") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                        NOTE_TAGS.filter { it != "Important" }.forEach { tag ->
                            val sel = selectedTagFilter == tag
                            FilterChip(
                                selected = sel,
                                onClick = { selectedTagFilter = if (sel) null else tag },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }

            // Notes grouped by date
            if (groupedNotes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (allNotes.isEmpty()) "No class notes yet.\nTap 'Capture Note' to take board/slide photos!" else "No matching notes found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                groupedNotes.forEach { (date, notesOnDate) ->
                    item(key = "date_$date") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        ) {
                            Icon(
                                Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    items(notesOnDate, key = { it.note.id }) { noteWithImgs ->
                        NoteCardItem(
                            item = noteWithImgs,
                            onOpenPhoto = { idx -> onOpenImageViewer(noteWithImgs.note.id, idx) },
                            onDelete = { noteToDelete = noteWithImgs.note.id },
                        )
                    }
                }
            }
        }

        // Floating Action Button to capture note for this course
        FloatingActionButton(
            onClick = onCaptureNote,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = "Capture")
                Spacer(Modifier.width(8.dp))
                Text("Capture Note", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Confirmation dialog for deleting note
    noteToDelete?.let { noteId ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete note & photos?") },
            text = { Text("This will permanently remove this class note and all its attached photos from your device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        noteToDelete = null
                        scope.launch { c.notes.deleteNote(noteId) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCardItem(
    item: ClassNoteWithImages,
    onOpenPhoto: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val note = item.note
    val images = item.images

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title + Important star + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.important) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = "Important",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = note.title?.ifBlank { null } ?: "Class Note",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete note",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Tags
            if (note.tags.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    note.tags.split(",").filter { it.isNotBlank() && it != "Important" }.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // Text note
            if (note.text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
            }

            // Photos Grid
            if (images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                PhotoGrid(images = images, onOpenPhoto = onOpenPhoto)
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    images: List<NoteImageEntity>,
    onOpenPhoto: (Int) -> Unit,
) {
    val displayImages = images.take(4)
    val remaining = images.size - 4

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        displayImages.forEachIndexed { idx, img ->
            val isLast = idx == 3 && remaining > 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenPhoto(idx) },
            ) {
                LocalImagePreview(path = img.thumbPath.ifBlank { img.filePath })

                if (isLast) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+$remaining",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocalImagePreview(
    path: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                bitmap = bmp
            }
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

