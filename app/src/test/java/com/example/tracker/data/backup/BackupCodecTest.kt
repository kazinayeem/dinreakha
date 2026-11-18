package com.example.tracker.data.backup

import com.example.tracker.data.db.Account
import com.example.tracker.data.db.AccountType
import com.example.tracker.data.db.BlockRuleEntity
import com.example.tracker.data.db.BlockRuleType
import com.example.tracker.data.db.Budget
import com.example.tracker.data.db.BusTimeEntity
import com.example.tracker.data.db.Category
import com.example.tracker.data.db.CategoryKind
import com.example.tracker.data.db.ClassNoteEntity
import com.example.tracker.data.db.ClassSessionEntity
import com.example.tracker.data.db.CourseEntity
import com.example.tracker.data.db.DayLog
import com.example.tracker.data.db.DayNoteEntity
import com.example.tracker.data.db.EventEntity
import com.example.tracker.data.db.EventExceptionEntity
import com.example.tracker.data.db.EventReminderEntity
import com.example.tracker.data.db.FocusBlockEntity
import com.example.tracker.data.db.FocusSessionEntity
import com.example.tracker.data.db.HabitCheck
import com.example.tracker.data.db.HolidayEntity
import com.example.tracker.data.db.NoteImageEntity
import com.example.tracker.data.db.PlanPhaseEntity
import com.example.tracker.data.db.Recurring
import com.example.tracker.data.db.ReminderPrefEntity
import com.example.tracker.data.db.RoutineEntity
import com.example.tracker.data.db.Setting
import com.example.tracker.data.db.SleepLogEntity
import com.example.tracker.data.db.Txn
import com.example.tracker.data.db.TxnType
import com.example.tracker.data.db.WakeOverrideEntity
import com.example.tracker.data.db.WeeklyReview
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BackupCodecTest {
    private val C = BackupCodec
    private val d = LocalDate.of(2026, 11, 18)

    /** Builds a file exactly the way BackupManager.exportJson does, from rows like the ones on the phone. */
    private fun file(): String {
        val root = JSONObject().put("format", C.FORMAT).put("version", C.VERSION).put("exported_at", "2026-11-18T21:40:00")
        root.put("day_log", C.arr(listOf(DayLog(d, null, ""), DayLog(d.minusDays(1), 4, "good day")), C::dayLog))
        root.put("habit_check", C.arr(listOf(
            HabitCheck(35, d, "woke_on_time", true, LocalTime.of(15, 14, 31)),
            HabitCheck(39, d, "nap", true, LocalTime.of(20, 41)), // stored on the phone as "20:41"
            HabitCheck(40, d, "walk", false, null),
        ), C::habitCheck))
        root.put("weekly_review", C.arr(listOf(WeeklyReview(1, "a", "", "c", 5L)), C::review))
        root.put("settings", C.arr(listOf(Setting("plan_start", "2026-11-17"), Setting("dyn_slots", ""),
            Setting("weather_cache", """{"temperature":25.6}""")), C::setting))
        root.put("account", C.arr(listOf(Account(7, "Cash", AccountType.CASH, 0, LocalDate.of(2026, 11, 17), null, 4282289763, false)), C::account))
        root.put("category", C.arr(listOf(Category(61, "Food", "🍛", CategoryKind.EXPENSE)), C::category))
        root.put("txn", C.arr(listOf(
            Txn(11, TxnType.EXPENSE, 250000, 7, null, 61, LocalDateTime.of(2026, 11, 17, 23, 9), "", null),
            Txn(12, TxnType.TRANSFER, 1000, 7, 8, null, LocalDateTime.of(2026, 11, 18, 9, 0), "to bKash", 13),
        ), C::txn))
        root.put("budget", C.arr(listOf(Budget(1, 52, "2026-11", 50000)), C::budget))
        root.put("recurring", C.arr(listOf(Recurring(1, "Rent", 500000, 52, 7, 1, true, null)), C::recurring))
        root.put("class_session", C.arr(listOf(ClassSessionEntity(0, DayOfWeek.SUNDAY, LocalTime.of(8, 30), LocalTime.of(10, 0), "SE 101", "")), C::classSession))
        root.put("bus_time", C.arr(listOf(BusTimeEntity(0, DayOfWeek.SUNDAY, "TO_CAMPUS", LocalTime.of(7, 0))), C::bus))
        root.put("focus_block", C.arr(listOf(FocusBlockEntity(0, "STUDY", null, LocalTime.of(20, 0), LocalTime.of(21, 0), "")), C::block))
        root.put("plan_phase", C.arr(listOf(PlanPhaseEntity(1, 1, 30, LocalTime.of(7, 0), LocalTime.of(23, 0), "", 0)), C::phase))
        root.put("wake_override", C.arr(listOf(WakeOverrideEntity(DayOfWeek.MONDAY, LocalTime.of(6, 0))), C::wakeOverride))
        root.put("holiday", C.arr(listOf(HolidayEntity(d, "Victory Day")), C::holiday))
        root.put("routine", C.arr(listOf(RoutineEntity(3, "Gym", "🏋️", "WEEKLY", "1,3", 1, null, false, true)), C::routine))
        root.put("reminder_pref", C.arr(listOf(ReminderPrefEntity("wake", true, 0, null, "SOUND", null)), C::reminderPref))
        root.put("event", C.arr(listOf(EventEntity(5, "Exam", d.atTime(10, 0), d.atTime(12, 0), false, "", "", 0xFF4F7CAC, null, null)), C::event))
        root.put("event_exception", C.arr(listOf(EventExceptionEntity(0, 5, d, true, null, null, null, null, null)), C::eventException))
        root.put("event_reminder", C.arr(listOf(EventReminderEntity(0, 5, 30)), C::eventReminder))
        root.put("day_note", C.arr(listOf(DayNoteEntity(d, "note")), C::dayNote))
        root.put("focus_session", C.arr(listOf(FocusSessionEntity(0, "STUDY", d.atTime(20, 0), d.atTime(20, 45), 45, "")), C::focusSession))
        root.put("course", C.arr(listOf(CourseEntity(7, "HDHD", "bdbdj", "hdhd", null, 4281316234, "Fall 2026", false)), C::course))
        root.put("block_rule", C.arr(listOf(BlockRuleEntity(7, BlockRuleType.DAILY_LIMIT, "fb", "com.facebook.katana",
            "https://bornosoft.bd,bornosoft.bd", 5, 10, 15, null, null, "", false, false, 1794941974980)), C::blockRule))
        root.put("class_note", C.arr(listOf(ClassNoteEntity(3, 7, d, null, null, "", "", false, 1794976914843, 1794976914843)), C::classNote))
        root.put("note_image", C.arr(listOf(NoteImageEntity(3, 3, "/data/x/img.jpg", "/data/x/thumb.jpg", 1536, 2048, null, 0, 1794976914973)), C::noteImage))
        root.put("sleep_log", C.arr(listOf(SleepLogEntity(d.minusDays(1), d.minusDays(1).atTime(23, 30), d.atTime(6, 30), null, "")), C::sleep))
        return root.toString(2)
    }

    @Test fun everyTableSurvivesTheRoundTrip() {
        val b = C.parse(file())
        assertEquals(listOf(DayLog(d, null, ""), DayLog(d.minusDays(1), 4, "good day")), b.dayLogs)
        assertEquals(LocalTime.of(20, 41), b.habitChecks[1].doneAt)
        assertNull(b.habitChecks[2].doneAt)
        assertEquals("", b.settings.first { it.key == "dyn_slots" }.value)
        assertEquals("""{"temperature":25.6}""", b.settings.first { it.key == "weather_cache" }.value)
        assertEquals(Account(7, "Cash", AccountType.CASH, 0, LocalDate.of(2026, 11, 17), null, 4282289763, false), b.accounts.single())
        assertEquals(13L, b.txns[1].linkedTxnId)
        assertNull(b.txns[0].toAccountId)
        assertEquals(DayOfWeek.SUNDAY, b.schedule!!.classes.single().dayOfWeek)
        assertNull(b.schedule!!.blocks.single().dayOfWeek)
        assertNull(b.schedule!!.routines.single().time)
        assertEquals(1, b.calendar!!.exceptions.size)
        assertEquals(30, b.calendar!!.reminders.single().minutesBefore)
        assertNull(b.courses!!.single().room)
        assertEquals("", b.blockRules!!.single().days)
        assertNull(b.blockRules!!.single().startTime)
        assertNull(b.classNotes!!.single().title)
        assertEquals(2048, b.noteImages!!.single().height)
        assertNull(b.sleep!!.single().quality)
        assertTrue(b.summary().contains("2 transactions"))
    }

    @Test fun olderBackupWithoutNewTablesKeepsThemNull() {
        val old = JSONObject().put("format", C.FORMAT).put("habit_check", org.json.JSONArray()).toString()
        val b = C.parse(old)
        assertNull(b.schedule); assertNull(b.calendar); assertNull(b.courses); assertNull(b.sleep)
    }

    @Test fun acceptsNumbersAndBooleansWrittenEitherWay() {
        val f = JSONObject(file())
        f.getJSONArray("account").getJSONObject(0).put("archived", 0)
        f.getJSONArray("habit_check").getJSONObject(0).put("done", "true")
        val b = C.parse(f.toString())
        assertEquals(false, b.accounts.single().archived)
        assertEquals(true, b.habitChecks[0].done)
    }

    @Test fun badRowNamesTableAndRow() {
        val f = JSONObject(file())
        f.getJSONArray("txn").getJSONObject(1).remove("date_time")
        try {
            C.parse(f.toString()); fail()
        } catch (e: BackupCodec.BadBackup) {
            assertTrue(e.message, e.message!!.contains("\"txn\", row 2"))
        }
    }

    @Test fun notABackupIsRejected() {
        for (text in listOf("", "hello", """{"foo":1}""")) {
            try { C.parse(text); fail(text) } catch (_: BackupCodec.BadBackup) {}
        }
    }

    @Test fun byteOrderMarkAndTrailingSpaceAreFine() {
        C.parse("﻿" + file() + "\n\n")
    }
}
