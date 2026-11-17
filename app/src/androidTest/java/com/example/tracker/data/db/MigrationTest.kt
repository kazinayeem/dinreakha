package com.example.tracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies v1 → v2 keeps habit data and creates/seeds the finance tables. Run: ./gradlew connectedDebugAndroidTest */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO habit_check(date, habit_key, done, done_at) VALUES ('2026-11-18', 'woke_on_time', 1, '06:01')")
        }
        helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2).use { db ->
            db.query("SELECT COUNT(*) FROM habit_check").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM category WHERE name = 'Fees & charges'").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM account").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        }
    }

    @Test
    fun migrate2To3SeedsSchedule() {
        helper.createDatabase(dbName + "3", 2).use { db ->
            db.execSQL("INSERT INTO habit_check(date, habit_key, done, done_at) VALUES ('2026-11-18', 'walk', 1, NULL)")
        }
        helper.runMigrationsAndValidate(dbName + "3", 3, true, Migrations.MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM habit_check").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM class_session").use { it.moveToFirst(); assertEquals(9, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM plan_phase").use { it.moveToFirst(); assertEquals(4, it.getInt(0)) }
            db.query("SELECT value FROM settings WHERE `key` = 'plan_start'").use { it.moveToFirst(); assertEquals("2026-11-18", it.getString(0)) }
        }
    }

    @Test
    fun migrate3To4AddsCalendar() {
        helper.createDatabase(dbName + "4", 3).use { db ->
            db.execSQL("INSERT INTO holiday(date, name) VALUES ('2026-12-16', 'Victory Day')")
        }
        helper.runMigrationsAndValidate(dbName + "4", 4, true, Migrations.MIGRATION_3_4).use { db ->
            db.query("SELECT COUNT(*) FROM holiday").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM event").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migrate4To5AddsFocus() {
        helper.createDatabase(dbName + "5", 4).close()
        helper.runMigrationsAndValidate(dbName + "5", 5, true, Migrations.MIGRATION_4_5).use { db ->
            db.query("SELECT COUNT(*) FROM focus_session").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }
}
