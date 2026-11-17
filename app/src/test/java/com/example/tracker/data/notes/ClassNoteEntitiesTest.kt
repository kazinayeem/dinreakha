package com.example.tracker.data.notes

import com.example.tracker.data.db.ClassNoteEntity
import com.example.tracker.data.db.CourseEntity
import com.example.tracker.data.db.NoteImageEntity
import com.example.tracker.ui.notes.NOTE_TAGS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ClassNoteEntitiesTest {

    @Test
    fun noteTags_containsExpectedPresets() {
        assertTrue(NOTE_TAGS.contains("Lecture"))
        assertTrue(NOTE_TAGS.contains("Board"))
        assertTrue(NOTE_TAGS.contains("Slide"))
        assertTrue(NOTE_TAGS.contains("Assignment"))
        assertTrue(NOTE_TAGS.contains("Exam hint"))
        assertTrue(NOTE_TAGS.contains("Important"))
    }

    @Test
    fun courseEntity_defaultValues() {
        val course = CourseEntity(
            code = "SE544",
            name = "Machine Learning",
            teacher = "Dr. Imran",
            room = "602",
            color = 0xFF4285F4,
        )
        assertEquals("Fall 2026", course.semester)
        assertFalse(course.archived)
        assertEquals("SE544", course.code)
    }

    @Test
    fun classNoteEntity_tagsAndImportance() {
        val today = LocalDate.of(2026, 10, 24)
        val note = ClassNoteEntity(
            courseId = 1L,
            date = today,
            classStartTime = LocalTime.of(10, 0),
            title = "Neural Networks Backpropagation",
            text = "Discussed gradient descent, learning rate alpha, and loss curves.",
            tags = "Board,Lecture,Exam hint",
            important = true,
        )

        assertEquals(1L, note.courseId)
        assertEquals(today, note.date)
        assertTrue(note.important)
        val tagsList = note.tags.split(",")
        assertEquals(3, tagsList.size)
        assertTrue(tagsList.contains("Board"))
        assertTrue(tagsList.contains("Exam hint"))
    }

    @Test
    fun noteImageEntity_ocrAndOrder() {
        val img = NoteImageEntity(
            noteId = 42L,
            filePath = "/files/notes/SE544/2026-10-24/img_1.jpg",
            thumbPath = "/files/notes/SE544/2026-10-24/thumb_1.jpg",
            width = 1600,
            height = 1200,
            ocrText = "W = W - alpha * dL/dW",
            sortOrder = 0,
        )

        assertEquals(42L, img.noteId)
        assertEquals(1600, img.width)
        assertEquals(1200, img.height)
        assertEquals("W = W - alpha * dL/dW", img.ocrText)
        assertEquals(0, img.sortOrder)
    }

    @Test
    fun courseWithNoteStats_countsAndLastDate() {
        val course = CourseEntity(
            id = 5L,
            code = "SE333",
            name = "Artificial Intelligence",
            color = 0xFF2FB38A,
        )
        val stat = com.example.tracker.data.db.CourseWithNoteStats(
            course = course,
            noteCount = 8,
            photoCount = 24,
            lastNoteDate = LocalDate.of(2026, 11, 15),
        )

        assertEquals(8, stat.noteCount)
        assertEquals(24, stat.photoCount)
        assertEquals("SE333", stat.course.code)
        assertEquals(LocalDate.of(2026, 11, 15), stat.lastNoteDate)
    }
}

