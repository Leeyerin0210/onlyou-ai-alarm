package com.onlyou.com.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onlyou.com.di.MIGRATION_17_18
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleMigrationTest {
    private val testDbName = "schedule-migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MiyaDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate17To18_preservesExistingRowAndDefaultsNewColumns() {
        var db = helper.createDatabase(testDbName, 17)
        db.execSQL(
            """
            INSERT INTO ai_schedules (id, date, endDate, startTime, timeHint, repeatDays, title, description, location, isAlarmEnabled)
            VALUES ('sched-1', '2026-07-10', NULL, '09:00', NULL, '', '병원 예약', NULL, NULL, 1)
            """.trimIndent(),
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 18, true, MIGRATION_17_18)

        val cursor = db.query("SELECT title, updatedAt, pendingSync FROM ai_schedules WHERE id = 'sched-1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("병원 예약", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("pendingSync")))
        cursor.close()
    }
}
