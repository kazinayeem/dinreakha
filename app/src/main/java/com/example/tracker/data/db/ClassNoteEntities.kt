package com.example.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "course",
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val teacher: String? = null,
    val room: String? = null,
    val color: Long,
    val semester: String = "Fall 2026",
    val archived: Boolean = false,
)

@Entity(
    tableName = "class_note",
    indices = [
        Index(value = ["course_id", "date"]),
        Index(value = ["date"]),
    ],
)
data class ClassNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "course_id") val courseId: Long,
    val date: LocalDate,
    @ColumnInfo(name = "class_start_time") val classStartTime: LocalTime? = null,
    val title: String? = null,
    val text: String = "",
    val tags: String = "", // Comma-separated: Lecture,Board,Slide,Assignment,Exam hint,Important
    val important: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "note_image",
    indices = [
        Index(value = ["note_id"]),
    ],
)
data class NoteImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "thumb_path") val thumbPath: String,
    val width: Int = 0,
    val height: Int = 0,
    @ColumnInfo(name = "ocr_text") val ocrText: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

data class ClassNoteWithImages(
    @Embedded val note: ClassNoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "note_id",
    )
    val images: List<NoteImageEntity> = emptyList(),
)

data class CourseWithNoteStats(
    val course: CourseEntity,
    val noteCount: Int,
    val photoCount: Int,
    val lastNoteDate: LocalDate?,
)

@Dao
interface ClassNoteDao {
    // ---- Courses ----
    @Query("SELECT * FROM course WHERE archived = :archived ORDER BY code ASC")
    fun observeCourses(archived: Boolean = false): Flow<List<CourseEntity>>

    @Query("SELECT * FROM course ORDER BY code ASC")
    suspend fun allCourses(): List<CourseEntity>

    @Query("SELECT * FROM course WHERE id = :id")
    suspend fun getCourse(id: Long): CourseEntity?

    @Query("SELECT * FROM course WHERE code = :code LIMIT 1")
    suspend fun getCourseByCode(code: String): CourseEntity?

    @Upsert
    suspend fun upsertCourse(c: CourseEntity): Long

    @Query("DELETE FROM course WHERE id = :id")
    suspend fun deleteCourse(id: Long)

    @Query("UPDATE course SET archived = :archived WHERE id = :id")
    suspend fun setCourseArchived(id: Long, archived: Boolean)

    // ---- Notes ----
    @Transaction
    @Query("SELECT * FROM class_note WHERE course_id = :courseId ORDER BY date DESC, id DESC")
    fun observeNotesForCourse(courseId: Long): Flow<List<ClassNoteWithImages>>

    @Transaction
    @Query("SELECT * FROM class_note WHERE course_id = :courseId")
    suspend fun getNotesForCourse(courseId: Long): List<ClassNoteWithImages>

    @Transaction
    @Query("SELECT * FROM class_note WHERE date = :date ORDER BY id DESC")
    fun observeNotesForDate(date: LocalDate): Flow<List<ClassNoteWithImages>>

    @Transaction
    @Query("SELECT * FROM class_note ORDER BY date DESC, id DESC")
    fun observeAllNotes(): Flow<List<ClassNoteWithImages>>

    @Transaction
    @Query("SELECT * FROM class_note WHERE id = :id")
    suspend fun getNoteWithImages(id: Long): ClassNoteWithImages?

    @Transaction
    @Query("SELECT * FROM class_note WHERE id = :id")
    fun observeNoteWithImages(id: Long): Flow<ClassNoteWithImages?>

    @Query("SELECT DISTINCT date FROM class_note")
    fun observeDatesWithNotes(): Flow<List<LocalDate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(n: ClassNoteEntity): Long

    @Update
    suspend fun updateNote(n: ClassNoteEntity)

    @Query("DELETE FROM class_note WHERE id = :id")
    suspend fun deleteNote(id: Long)

    // ---- Images ----
    @Query("SELECT * FROM note_image WHERE note_id = :noteId ORDER BY sort_order ASC, id ASC")
    suspend fun getImagesForNote(noteId: Long): List<NoteImageEntity>

    @Query("SELECT * FROM note_image WHERE id = :id")
    suspend fun getImage(id: Long): NoteImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(img: NoteImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(imgs: List<NoteImageEntity>): List<Long>

    @Query("DELETE FROM note_image WHERE id = :id")
    suspend fun deleteImage(id: Long)

    @Query("DELETE FROM note_image WHERE note_id = :noteId")
    suspend fun deleteImagesForNote(noteId: Long)

    @Query("UPDATE note_image SET ocr_text = :ocrText WHERE id = :id")
    suspend fun updateImageOcr(id: Long, ocrText: String)

    // ---- Search ----
    @Transaction
    @Query("""
        SELECT DISTINCT n.* FROM class_note n 
        LEFT JOIN note_image i ON i.note_id = n.id 
        WHERE n.title LIKE '%' || :query || '%' 
           OR n.text LIKE '%' || :query || '%' 
           OR n.tags LIKE '%' || :query || '%' 
           OR (i.ocr_text IS NOT NULL AND i.ocr_text LIKE '%' || :query || '%')
        ORDER BY n.date DESC
    """)
    fun searchNotes(query: String): Flow<List<ClassNoteWithImages>>

    // ---- Backup ----
    @Query("SELECT * FROM class_note ORDER BY id")
    suspend fun allNotesForBackup(): List<ClassNoteEntity>

    @Query("SELECT * FROM note_image ORDER BY id")
    suspend fun allImagesForBackup(): List<NoteImageEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun restoreNote(n: ClassNoteEntity)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun restoreImage(i: NoteImageEntity)

    // ---- Wipes ----
    @Query("DELETE FROM course")
    suspend fun clearCourses()

    @Query("DELETE FROM class_note")
    suspend fun clearNotes()

    @Query("DELETE FROM note_image")
    suspend fun clearImages()
}
