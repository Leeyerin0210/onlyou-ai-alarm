package com.onlyou.com.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onlyou.com.di.MIGRATION_20_21
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryTableDropMigrationTest {
    private val testDbName = "memory-drop-migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MiyaDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate20To21_dropsMemoriesTableAndPreservesEverythingElse() {
        var db = helper.createDatabase(testDbName, 20)
        db.execSQL(
            "INSERT INTO memories (id, type, content, targetDate, createdAt) " +
                "VALUES ('m1', 'USER_NOTE', '커피를 좋아함', NULL, '2026-09-01T10:00:00')",
        )
        db.execSQL(
            "INSERT INTO chat_messages (id, text, sender, timestamp) " +
                "VALUES ('c1', '안녕', 'USER', '2026-09-01T10:00:00')",
        )
        db.execSQL(
            """
            INSERT INTO personas (id, name, prompt, description, voiceTone, voiceSpeed, voicePrompt, userCallSign, isSelected, imageUrl, primaryHex, secondaryHex, creatorId, usageCount, isPrivate, presetKey)
            VALUES ('p1', '미야', '', '설명', 1.0, 1.0, '', '주인님', 1, NULL, NULL, NULL, 'uid-1', 3, 0, 'casual_warm')
            """.trimIndent(),
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 21, true, MIGRATION_20_21)

        val dropped = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'memories'",
        )
        assertFalse(dropped.moveToFirst())
        dropped.close()

        val chats = db.query("SELECT text FROM chat_messages WHERE id = 'c1'")
        assertTrue(chats.moveToFirst())
        assertEquals("안녕", chats.getString(0))
        chats.close()

        val personas = db.query("SELECT name, presetKey FROM personas WHERE id = 'p1'")
        assertTrue(personas.moveToFirst())
        assertEquals("미야", personas.getString(0))
        assertEquals("casual_warm", personas.getString(1))
        personas.close()
    }
}
