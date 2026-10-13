package com.onlyou.com.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onlyou.com.di.MIGRATION_21_22
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonaTagsMigrationTest {
    private val testDbName = "persona-tags-migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MiyaDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate21To22_addsEmptyTagsAndPreservesPersona() {
        var db = helper.createDatabase(testDbName, 21)
        db.execSQL(
            """
            INSERT INTO personas (id, name, prompt, description, voiceTone, voiceSpeed, voicePrompt, userCallSign, isSelected, imageUrl, primaryHex, secondaryHex, creatorId, usageCount, isPrivate, presetKey)
            VALUES ('p1', '미야', '', '설명', 1.0, 1.0, '', '주인님', 1, NULL, NULL, NULL, 'uid-1', 3, 0, 'casual_warm')
            """.trimIndent(),
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 22, true, MIGRATION_21_22)

        val c = db.query("SELECT name, usageCount, presetKey, tags FROM personas WHERE id = 'p1'")
        assertTrue(c.moveToFirst())
        assertEquals("미야", c.getString(0))
        assertEquals(3, c.getInt(1))
        assertEquals("casual_warm", c.getString(2))
        assertEquals("[]", c.getString(3))
        c.close()
    }
}
