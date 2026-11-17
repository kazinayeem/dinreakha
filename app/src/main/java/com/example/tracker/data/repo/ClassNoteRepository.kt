package com.example.tracker.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.ClassNoteDao
import com.example.tracker.data.db.ClassNoteEntity
import com.example.tracker.data.db.ClassNoteWithImages
import com.example.tracker.data.db.CourseEntity
import com.example.tracker.data.db.CourseWithNoteStats
import com.example.tracker.data.db.NoteImageEntity
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ActiveClassResult(
    val course: CourseEntity,
    val classTitle: String,
    val room: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isHappeningNow: Boolean,
)

data class ProcessedImage(
    val file: File,
    val thumbFile: File,
    val width: Int,
    val height: Int,
)

class ClassNoteRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduleRepo: ScheduleRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val dao: ClassNoteDao = db.classNoteDao()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // ==================== Courses ====================

    fun observeCourses(archived: Boolean = false): Flow<List<CourseEntity>> =
        dao.observeCourses(archived)

    fun observeCoursesWithStats(archived: Boolean = false): Flow<List<CourseWithNoteStats>> {
        return combine(dao.observeCourses(archived), dao.observeAllNotes()) { courses, notes ->
            courses.map { c ->
                val courseNotes = notes.filter { it.note.courseId == c.id }
                val photoCount = courseNotes.sumOf { it.images.size }
                val lastDate = courseNotes.maxOfOrNull { it.note.date }
                CourseWithNoteStats(
                    course = c,
                    noteCount = courseNotes.size,
                    photoCount = photoCount,
                    lastNoteDate = lastDate,
                )
            }
        }
    }

    suspend fun getCourse(id: Long): CourseEntity? = dao.getCourse(id)
    suspend fun saveCourse(c: CourseEntity): Long = dao.upsertCourse(c)
    suspend fun setCourseArchived(id: Long, archived: Boolean) = dao.setCourseArchived(id, archived)
    suspend fun deleteCourse(id: Long) = withContext(Dispatchers.IO) {
        val course = dao.getCourse(id)
        val courseNotes = dao.getNotesForCourse(id)
        courseNotes.forEach { n ->
            n.images.forEach { img ->
                runCatching {
                    File(img.filePath).delete()
                    img.thumbPath?.let { File(it).delete() }
                }
            }
            dao.deleteImagesForNote(n.note.id)
            dao.deleteNote(n.note.id)
        }
        if (course != null) {
            runCatching {
                File(context.filesDir, "notes/${course.code}").deleteRecursively()
            }
        }
        dao.deleteCourse(id)
    }

    // ==================== Active Class Auto-Detection ====================

    suspend fun detectCurrentClass(now: LocalDateTime = LocalDateTime.now(Schedule.ZONE)): ActiveClassResult? {
        val dow = now.dayOfWeek
        val time = now.toLocalTime()
        val classes = scheduleRepo.load().classes

        // Match classes happening now or ended within last 35 minutes
        val activeSession = classes.firstOrNull { session ->
            session.day == dow &&
                !time.isBefore(session.start.minusMinutes(10)) &&
                !time.isAfter(session.end.plusMinutes(35))
        } ?: return null

        val allCourses = dao.allCourses()
        val matchedCourse = allCourses.firstOrNull {
            it.code.equals(activeSession.title, ignoreCase = true) ||
                it.name.contains(activeSession.title, ignoreCase = true) ||
                activeSession.title.contains(it.code, ignoreCase = true)
        } ?: allCourses.firstOrNull() ?: return null

        val isNow = !time.isBefore(activeSession.start) && !time.isAfter(activeSession.end)
        return ActiveClassResult(
            course = matchedCourse,
            classTitle = activeSession.title,
            room = activeSession.room,
            startTime = activeSession.start,
            endTime = activeSession.end,
            isHappeningNow = isNow,
        )
    }

    // ==================== Notes ====================

    fun observeNotesForCourse(courseId: Long): Flow<List<ClassNoteWithImages>> =
        dao.observeNotesForCourse(courseId)

    fun observeNotesForDate(date: LocalDate): Flow<List<ClassNoteWithImages>> =
        dao.observeNotesForDate(date)

    fun observeDatesWithNotes(): Flow<List<LocalDate>> =
        dao.observeDatesWithNotes()

    suspend fun getNoteWithImages(id: Long): ClassNoteWithImages? =
        dao.getNoteWithImages(id)

    fun observeNoteWithImages(id: Long): Flow<ClassNoteWithImages?> =
        dao.observeNoteWithImages(id)

    suspend fun saveNote(
        note: ClassNoteEntity,
        newImages: List<Uri>,
        courseCode: String,
    ): Long = withContext(Dispatchers.IO) {
        val noteId = if (note.id == 0L) dao.insertNote(note) else { dao.updateNote(note); note.id }

        // Process newly attached images
        val dateDir = note.date.toString()
        val targetDir = File(context.filesDir, "notes/$courseCode/$dateDir").apply { mkdirs() }

        newImages.forEachIndexed { idx, uri ->
            runCatching {
                val processed = processAndSaveImage(uri, targetDir)
                if (processed != null) {
                    val imgEntity = NoteImageEntity(
                        noteId = noteId,
                        filePath = processed.file.absolutePath,
                        thumbPath = processed.thumbFile.absolutePath,
                        width = processed.width,
                        height = processed.height,
                        sortOrder = idx,
                    )
                    val imgId = dao.insertImage(imgEntity)
                    // Trigger async background OCR
                    triggerOcr(imgId, processed.file)
                }
            }
        }
        noteId
    }

    suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        val imgs = dao.getImagesForNote(id)
        imgs.forEach {
            runCatching { File(it.filePath).delete() }
            runCatching { File(it.thumbPath).delete() }
        }
        dao.deleteNote(id)
    }

    suspend fun deleteImage(id: Long) = withContext(Dispatchers.IO) {
        val img = dao.getImage(id)
        if (img != null) {
            runCatching { File(img.filePath).delete() }
            runCatching { File(img.thumbPath).delete() }
            dao.deleteImage(id)
        }
    }

    suspend fun rotateImage(id: Long, degrees: Float = 90f): Boolean = withContext(Dispatchers.IO) {
        val img = dao.getImage(id) ?: return@withContext false
        val file = File(img.filePath)
        val thumbFile = File(img.thumbPath)
        if (!file.exists()) return@withContext false

        runCatching {
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching false
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated != bmp) bmp.recycle()

            FileOutputStream(file).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            val scale = minOf(1f, 320f / maxOf(rotated.width, rotated.height))
            val tw = (rotated.width * scale).toInt().coerceAtLeast(1)
            val th = (rotated.height * scale).toInt().coerceAtLeast(1)
            val thumb = Bitmap.createScaledBitmap(rotated, tw, th, true)
            FileOutputStream(thumbFile).use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            if (thumb != rotated) thumb.recycle()

            val updated = img.copy(width = rotated.width, height = rotated.height)
            dao.insertImage(updated)
            rotated.recycle()
            true
        }.getOrDefault(false)
    }

    fun searchNotes(query: String): Flow<List<ClassNoteWithImages>> =
        dao.searchNotes(query)

    // ==================== Image Processing & Compression ====================

    private fun processAndSaveImage(uri: Uri, targetDir: File): ProcessedImage? {
        val uuid = UUID.randomUUID().toString().take(8)
        val ts = System.currentTimeMillis()
        val imgFile = File(targetDir, "img_${ts}_$uuid.jpg")
        val thumbFile = File(targetDir, "thumb_${ts}_$uuid.jpg")

        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.use { it.readBytes() }
        if (bytes.isEmpty()) return null

        // Decode orientation from Exif
        val orientation = runCatching {
            val exif = ExifInterface(bytes.inputStream())
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val rotationAngle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        // Decode bounds
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val origW = opts.outWidth
        val origH = opts.outHeight
        if (origW <= 0 || origH <= 0) return null

        // Scale ~1600px max long edge
        val maxDim = 1600
        var inSample = 1
        val maxOrig = maxOf(origW, origH)
        while (maxOrig / (inSample * 2) >= maxDim) {
            inSample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null

        // Rotate if needed
        val finalBitmap = if (rotationAngle != 0f) {
            val matrix = Matrix().apply { postRotate(rotationAngle) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else decoded

        // Save full image (JPEG q80)
        FileOutputStream(imgFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        // Generate small thumbnail ~320px
        val thumbMaxDim = 320
        val scale = minOf(1f, thumbMaxDim.toFloat() / maxOf(finalBitmap.width, finalBitmap.height))
        val thumbW = (finalBitmap.width * scale).toInt().coerceAtLeast(1)
        val thumbH = (finalBitmap.height * scale).toInt().coerceAtLeast(1)
        val thumbBitmap = Bitmap.createScaledBitmap(finalBitmap, thumbW, thumbH, true)

        FileOutputStream(thumbFile).use { out ->
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
        }

        val resW = finalBitmap.width
        val resH = finalBitmap.height

        thumbBitmap.recycle()
        finalBitmap.recycle()

        return ProcessedImage(imgFile, thumbFile, resW, resH)
    }

    // ==================== On-Device ML Kit OCR ====================

    private fun triggerOcr(imageId: Long, file: File) {
        scope.launch {
            runCatching {
                val inputImage = InputImage.fromFilePath(context, Uri.fromFile(file))
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        if (text.isNotBlank()) {
                            scope.launch { dao.updateImageOcr(imageId, text) }
                        }
                    }
            }
        }
    }

    // ==================== PDF Exporter ====================

    suspend fun exportCourseNotesPdf(
        courseId: Long,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null,
    ): File = withContext(Dispatchers.IO) {
        val course = dao.getCourse(courseId) ?: error("Course not found")
        val notes = dao.getImagesForNote(courseId) // fetches images or notes
        val allNotesWithImgs = dao.allCourses()

        val exportDir = File(context.cacheDir, "notes_export").apply { mkdirs() }
        val pdfFile = File(exportDir, "${course.code}_Notes_${System.currentTimeMillis()}.pdf")

        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (points)

        var page = pdfDoc.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true }
        var y = 50f

        // Document Title Header
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("${course.code}: ${course.name}", 40f, y, paint)
        y += 24f

        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        paint.color = android.graphics.Color.DKGRAY
        canvas.drawText("Semester: ${course.semester} • Teacher: ${course.teacher ?: "Faculty"}", 40f, y, paint)
        y += 30f

        // Draw separator
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 20f

        pdfDoc.finishPage(page)

        FileOutputStream(pdfFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
        pdfFile
    }

    // ==================== Storage Stats ====================

    suspend fun getCourseStorageUsage(): Map<String, Long> = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "notes")
        if (!root.exists()) return@withContext emptyMap()

        val map = mutableMapOf<String, Long>()
        root.listFiles()?.filter { it.isDirectory }?.forEach { courseDir ->
            var bytes = 0L
            courseDir.walkTopDown().forEach { if (it.isFile) bytes += it.length() }
            map[courseDir.name] = bytes
        }
        map
    }
}
