package com.onlyou.com.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onlyou.com.di.MIGRATION_19_20
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonaMigrationTest {
    private val testDbName = "persona-migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MiyaDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate19To20_preservesPersonaAndDefaultsPresetKey() {
        var db = helper.createDatabase(testDbName, 19)
        db.execSQL(
            """
            INSERT INTO personas (id, name, prompt, description, voiceTone, voiceSpeed, voicePrompt, userCallSign, isSelected, imageUrl, primaryHex, secondaryHex, creatorId, usageCount, isPrivate)
            VALUES ('p1', '미야', '자유 프롬프트', '설명', 1.0, 1.0, '다정하게', '주인님', 1, NULL, NULL, NULL, 'uid-1', 3, 0)
            """.trimIndent(),
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 20, true, MIGRATION_19_20)

        val cursor = db.query("SELECT name, presetKey, usageCount FROM personas WHERE id = 'p1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("미야", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("presetKey")))
        assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("usageCount")))
        cursor.close()
    }
}
