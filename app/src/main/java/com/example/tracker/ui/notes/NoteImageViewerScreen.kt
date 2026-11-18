package com.example.tracker.ui.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private val VIEWER_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

@Composable
fun NoteImageViewerScreen(
    noteId: Long,
    initialIndex: Int = 0,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val noteWithImages by remember(noteId) { c.notes.observeNoteWithImages(noteId) }.collectAsStateWithLifecycle(null)
    var course by remember { mutableStateOf<CourseEntity?>(null) }

    LaunchedEffect(noteWithImages?.note?.courseId) {
        val cid = noteWithImages?.note?.courseId ?: return@LaunchedEffect
        course = c.notes.getCourse(cid)
    }

    val note = noteWithImages?.note
    val images = noteWithImages?.images ?: emptyList()

    if (noteWithImages != null && images.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val safeInitial = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitial) { images.size }

    var showControls by remember { mutableStateOf(true) }
    var showOcrSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var rotationKey by remember { mutableIntStateOf(0) }

    val currentImage = images.getOrNull(pagerState.currentPage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11))
    ) {
        if (images.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val img = images[page]
                ZoomableImage(
                    imagePath = img.filePath,
                    rotationKey = rotationKey,
                    onToggleControls = { showControls = !showControls },
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // Top Control Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }

                    Spacer(Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            course?.let { crs ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(crs.color).copy(alpha = 0.85f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = crs.code,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            if (images.isNotEmpty()) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${images.size}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                        note?.let { n ->
                            Text(
                                text = n.title?.takeIf { it.isNotBlank() } ?: VIEWER_DATE.format(n.date),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Rotate
                    IconButton(
                        onClick = {
                            val img = currentImage ?: return@IconButton
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                val ok = c.notes.rotateImage(img.id, 90f)
                                if (ok) rotationKey++
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate 90 degrees")
                    }

                    // Share
                    IconButton(
                        onClick = {
                            val img = currentImage ?: return@IconButton
                            val file = File(img.filePath)
                            if (file.exists()) {
                                runCatching {
                                    val uri = FileProvider.getUriForFile(
                                        ctx,
                                        "${ctx.packageName}.fileprovider",
                                        file,
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "${course?.code ?: "Class"} Note")
                                        note?.text?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_TEXT, it) }
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ctx.startActivity(Intent.createChooser(shareIntent, "Share note image"))
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share image")
                    }

                    // Delete
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFFF6B6B)),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete photo")
                    }
                }
            }
        }

        // Bottom Info & OCR Sheet
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // Tags row
                    note?.let { n ->
                        val tags = n.tags.split(",").filter { it.isNotBlank() }
                        if (tags.isNotEmpty() || n.important) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (n.important) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF2A93B).copy(alpha = 0.25f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.Star, null, tint = Color(0xFFF2A93B), modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Important", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF2A93B))
                                        }
                                    }
                                }
                                tags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.16f),
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (n.text.isNotBlank()) {
                            Text(
                                text = n.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = if (showOcrSheet) 2 else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    // OCR Banner / Drawer Toggle
                    val ocr = currentImage?.ocrText?.trim()
                    if (!ocr.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF26262B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { showOcrSheet = !showOcrSheet },
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.TextFields,
                                            contentDescription = null,
                                            tint = Color(0xFF8AB4F8),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Detected Text from Board (OCR)",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF8AB4F8),
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("Text from photo", ocr))
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                com.example.tracker.ui.components.showMessage(ctx, "Text copied to clipboard")
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.ContentCopy,
                                                contentDescription = "Copy text",
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        Icon(
                                            if (showOcrSheet) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                        )
                                    }
                                }

                                if (showOcrSheet) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = ocr,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                        color = Color.White.copy(alpha = 0.88f),
                                        modifier = Modifier
                                            .heightIn(max = 140.dp)
                                            .verticalScroll(rememberScrollState()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        val img = currentImage
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete photo?") },
            text = { Text("This photo will be permanently removed from this note.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        if (img != null) {
                            scope.launch {
                                c.notes.deleteImage(img.id)
                                if (images.size <= 1) {
                                    onBack()
                                }
                            }
                        }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ZoomableImage(
    imagePath: String,
    rotationKey: Int,
    onToggleControls: () -> Unit,
) {
    var bitmap by remember(imagePath, rotationKey) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(imagePath, rotationKey) { mutableFloatStateOf(1f) }
    var offset by remember(imagePath, rotationKey) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(imagePath, rotationKey) {
        withContext(Dispatchers.IO) {
            val file = File(imagePath)
            if (file.exists()) {
                bitmap = BitmapFactory.decodeFile(file.absolutePath)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imagePath, rotationKey) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            // Center zoom on double-tapped point
                            offset = Offset(
                                (size.width / 2 - tapOffset.x) * 1.5f,
                                (size.height / 2 - tapOffset.y) * 1.5f,
                            )
                        }
                    },
                    onTap = { onToggleControls() },
                )
            }
            .pointerInput(imagePath, rotationKey) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        val maxPanX = (size.width * (newScale - 1f)) / 2f
                        val maxPanY = (size.height * (newScale - 1f)) / 2f
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            (offset.y + pan.y).coerceIn(-maxPanY, maxPanY),
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Class note photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        } else {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
