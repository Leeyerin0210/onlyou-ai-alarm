package com.onlyou.com.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onlyou.com.di.MIGRATION_22_23
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonaFieldsMigrationTest {
    private val testDbName = "persona-fields-migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MiyaDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate22To23_addsEmptyFieldsAndPreservesPersona() {
        var db = helper.createDatabase(testDbName, 22)
        db.execSQL(
            """
            INSERT INTO personas (id, name, prompt, description, voiceTone, voiceSpeed, voicePrompt, userCallSign, isSelected, imageUrl, primaryHex, secondaryHex, creatorId, usageCount, isPrivate, presetKey, tags)
            VALUES ('p1', '미야', '', '설명', 1.0, 1.0, '', '주인님', 1, NULL, NULL, NULL, 'uid-1', 3, 0, 'casual_warm', '["아침"]')
            """.trimIndent(),
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 23, true, MIGRATION_22_23)

        val c = db.query(
            "SELECT name, presetKey, tags, speechStyle, personality, role, reportLocked, isOfficial FROM personas WHERE id = 'p1'",
        )
        assertTrue(c.moveToFirst())
        assertEquals("미야", c.getString(0))
        assertEquals("casual_warm", c.getString(1))
        assertEquals("[\"아침\"]", c.getString(2))
        assertEquals("", c.getString(3))
        assertEquals("", c.getString(4))
        assertEquals("", c.getString(5))
        assertEquals(0, c.getInt(6))
        assertEquals(0, c.getInt(7))
        c.close()
    }
}
