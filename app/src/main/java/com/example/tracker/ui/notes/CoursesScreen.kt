package com.example.tracker.ui.notes

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.container
import com.example.tracker.data.db.CourseEntity
import com.example.tracker.data.db.CourseWithNoteStats
import com.example.tracker.ui.components.Gap
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val COURSE_PALETTE = listOf(
    0xFF2FB38A, // Emerald / Green
    0xFF38BDF8, // Sky Blue
    0xFF818CF8, // Indigo
    0xFFC77DFF, // Purple
    0xFFF07AAE, // Rose / Pink
    0xFFF2A93B, // Amber / Orange
    0xFFE0605E, // Coral / Red
    0xFF34D399, // Mint
)

@Composable
fun CoursesScreen(
    onOpenCourse: (Long) -> Unit,
    onCaptureNote: (Long?) -> Unit,
    onBack: () -> Unit,
    showTitle: Boolean = true,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Active, 1 = Archived
    val isArchived = selectedTab == 1

    val coursesWithStats by remember { c.notes.observeCoursesWithStats(archived = isArchived) }.collectAsStateWithLifecycle(emptyList())
    val activeCourses by remember { c.notes.observeCourses(archived = false) }.collectAsStateWithLifecycle(emptyList())
    val archivedCourses by remember { c.notes.observeCourses(archived = true) }.collectAsStateWithLifecycle(emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var isAddingCourse by remember { mutableStateOf(false) }
    var courseToEdit by remember { mutableStateOf<CourseEntity?>(null) }
    var courseToDelete by remember { mutableStateOf<CourseWithNoteStats?>(null) }

    val totalCourses = coursesWithStats.size
    val totalNotes = coursesWithStats.sumOf { it.noteCount }
    val totalPhotos = coursesWithStats.sumOf { it.photoCount }

    val filtered = if (searchQuery.isBlank()) {
        coursesWithStats
    } else {
        coursesWithStats.filter {
            it.course.code.contains(searchQuery, ignoreCase = true) ||
                it.course.name.contains(searchQuery, ignoreCase = true) ||
                (it.course.teacher != null && it.course.teacher.contains(searchQuery, ignoreCase = true)) ||
                (it.course.room != null && it.course.room.contains(searchQuery, ignoreCase = true))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header with title, count stats, and Add button
            item {
                Column(modifier = Modifier.padding(top = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (showTitle) Text(
                                text = "Course Library",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "$totalCourses ${if (totalCourses == 1) "course" else "courses"} • $totalNotes notes • $totalPhotos photos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        FilledTonalButton(
                            onClick = { isAddingCourse = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Course", fontWeight = FontWeight.Bold)
                        }
                    }

                    Gap(12.dp)

                    // Active vs Archived segment tabs
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) {
                            Text("Active (${activeCourses.size})")
                        }
                        SegmentedButton(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) {
                            Text("Archived (${archivedCourses.size})")
                        }
                    }

                    Gap(12.dp)

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by code, title, teacher or room...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Course cards list
            items(filtered, key = { it.course.id }) { item ->
                CourseCard(
                    stat = item,
                    onClick = { onOpenCourse(item.course.id) },
                    onCaptureForCourse = { onCaptureNote(item.course.id) },
                    onEditCourse = { courseToEdit = item.course },
                    onToggleArchive = {
                        scope.launch {
                            val newArchived = !item.course.archived
                            c.notes.setCourseArchived(item.course.id, newArchived)
                            Toast.makeText(
                                ctx,com.example.tracker.ui.i18n.tr(if (newArchived) "${item.course.code} archived" else "${item.course.code} unarchived"),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onDeleteCourse = { courseToDelete = item },
                )
            }

            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "No matching courses found"
                                } else if (isArchived) {
                                    "No archived courses"
                                } else {
                                    "No courses in your library yet"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "Try searching for another keyword"
                                } else {
                                    "Tap '+ Add Course' to organize your class notes & board photos."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (searchQuery.isBlank() && !isArchived) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { isAddingCourse = true },
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Your First Course")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for Quick Capture
        FloatingActionButton(
            onClick = { onCaptureNote(null) },
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

    // Add Course Dialog
    if (isAddingCourse) {
        CourseEditorDialog(
            initialCourse = null,
            onDismiss = { isAddingCourse = false },
            onSave = { newCourse ->
                isAddingCourse = false
                scope.launch {
                    c.notes.saveCourse(newCourse)
                    Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Course '${newCourse.code}' added"), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    // Edit Course Dialog
    if (courseToEdit != null) {
        CourseEditorDialog(
            initialCourse = courseToEdit,
            onDismiss = { courseToEdit = null },
            onSave = { updatedCourse ->
                courseToEdit = null
                scope.launch {
                    c.notes.saveCourse(updatedCourse)
                    Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Course '${updatedCourse.code}' updated"), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    // Delete Course Confirmation Dialog
    if (courseToDelete != null) {
        val target = courseToDelete!!
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            icon = {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text("Delete Course?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Are you sure you want to delete ${target.course.code} • ${target.course.name}?\n\n" +
                        "This will permanently delete ${target.noteCount} notes and ${target.photoCount} photos saved for this course. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.course.id
                        val code = target.course.code
                        courseToDelete = null
                        scope.launch {
                            c.notes.deleteCourse(id)
                            Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("Course '$code' and its notes deleted"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete Course", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { courseToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CourseCard(
    stat: CourseWithNoteStats,
    onClick: () -> Unit,
    onCaptureForCourse: () -> Unit,
    onEditCourse: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeleteCourse: () -> Unit,
) {
    val course = stat.course
    val courseColor = Color(course.color)
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Course Code Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(courseColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = course.code,
                        fontWeight = FontWeight.Bold,
                        color = courseColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick photo capture button for this course
                    IconButton(
                        onClick = onCaptureForCourse,
                    ) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = "Capture photo for course",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    // Three-dot Options Menu
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Course options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit course") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = {
                                    menuOpen = false
                                    onEditCourse()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (course.archived) "Unarchive course" else "Archive course") },
                                leadingIcon = {
                                    Icon(
                                        if (course.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                        null,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onToggleArchive()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete course", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    onDeleteCourse()
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = course.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (!course.teacher.isNullOrBlank() || !course.room.isNullOrBlank()) {
                val details = listOfNotNull(course.teacher?.ifBlank { null }, course.room?.ifBlank { null })
                    .joinToString(", ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Footer: Note count & Photo count & Last note date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${stat.noteCount} notes",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${stat.photoCount} photos",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (stat.lastNoteDate != null) {
                    Text(
                        text = "Last: ${stat.lastNoteDate.format(DateTimeFormatter.ofPattern("dd MMM"))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseEditorDialog(
    initialCourse: CourseEntity?,
    onDismiss: () -> Unit,
    onSave: (CourseEntity) -> Unit,
) {
    val isEdit = initialCourse != null
    var code by remember { mutableStateOf(initialCourse?.code ?: "") }
    var name by remember { mutableStateOf(initialCourse?.name ?: "") }
    var teacher by remember { mutableStateOf(initialCourse?.teacher ?: "") }
    var room by remember { mutableStateOf(initialCourse?.room ?: "") }
    var semester by remember { mutableStateOf(initialCourse?.semester ?: "Fall 2026") }
    var selectedColor by remember { mutableStateOf(initialCourse?.color ?: COURSE_PALETTE.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEdit) "Edit Course" else "Add New Course", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase()
                        error = null
                    },
                    label = { Text("Course Code *") },
                    placeholder = { Text("e.g. CSE411, SE333") },
                    singleLine = true,
                    isError = error != null && code.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Course Title *") },
                    placeholder = { Text("e.g. Artificial Intelligence") },
                    singleLine = true,
                    isError = error != null && name.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("Teacher") },
                        placeholder = { Text("Dr. Rahman") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("Room") },
                        placeholder = { Text("KT-712B") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = semester,
                    onValueChange = { semester = it },
                    label = { Text("Semester") },
                    placeholder = { Text("Fall 2026") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Color Tag",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    COURSE_PALETTE.forEach { colorVal ->
                        val color = Color(colorVal)
                        val isSelected = selectedColor == colorVal
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = colorVal },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.isBlank()) {
                        error = "Please enter course code"
                        return@Button
                    }
                    if (name.isBlank()) {
                        error = "Please enter course title"
                        return@Button
                    }
                    val course = CourseEntity(
                        id = initialCourse?.id ?: 0L,
                        code = code.trim(),
                        name = name.trim(),
                        teacher = teacher.trim().ifBlank { null },
                        room = room.trim().ifBlank { null },
                        color = selectedColor,
                        semester = semester.trim().ifBlank { "Fall 2026" },
                        archived = initialCourse?.archived ?: false,
                    )
                    onSave(course)
                },
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(if (isEdit) "Save Changes" else "Create Course")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
