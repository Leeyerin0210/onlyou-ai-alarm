package com.onlyou.com.di

import android.content.Context
import androidx.room.Room
import com.onlyou.com.data.local.AiScheduleDao
import com.onlyou.com.data.local.AlarmDao
import com.onlyou.com.data.local.AlarmVoiceChunkDao
import com.onlyou.com.data.local.ChatDao
import com.onlyou.com.data.local.MiyaDatabase
import com.onlyou.com.data.local.PersonaDao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ai_schedules ADD COLUMN location TEXT")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE ai_schedules ADD COLUMN endDate TEXT DEFAULT NULL")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ai_schedules ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ai_schedules ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ai_schedules ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE personas ADD COLUMN presetKey TEXT NOT NULL DEFAULT ''")
    }
}

// 기억은 서버(pgvector)가 단일 소유한다 — 로컬 사본을 들고 있으면 서버가 모순을
// 해소해도(B) 앱에 폐기된 기억이 남는다.
// 스펙: docs/superpowers/specs/2026-09-28-memory-ownership-consolidation-design.md
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS memories")
    }
}

// 상점 해시태그 — 스펙: docs/superpowers/specs/2026-10-12-shop-redesign-design.md
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE personas ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
    }
}

// 유저 목소리 디자인 — 스펙: docs/superpowers/specs/2026-10-31-user-voice-design-design.md
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE personas ADD COLUMN voiceDescription TEXT")
        db.execSQL("ALTER TABLE personas ADD COLUMN voiceSampleText TEXT")
    }
}

// 성격 프리셋 폐지 — 스펙: docs/superpowers/specs/2026-10-13-presets-removal-design.md
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE personas ADD COLUMN speechStyle TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE personas ADD COLUMN personality TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE personas ADD COLUMN role TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE personas ADD COLUMN reportLocked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE personas ADD COLUMN isOfficial INTEGER NOT NULL DEFAULT 0")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MiyaDatabase =
        Room
            .databaseBuilder(
                context,
                MiyaDatabase::class.java,
                "miya_database",
            )
            .addMigrations(
                MIGRATION_13_14, MIGRATION_14_15, MIGRATION_17_18,
                MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
                MIGRATION_22_23, MIGRATION_23_24,
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideRemoteConfig(): com.google.firebase.remoteconfig.FirebaseRemoteConfig =
        com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance().apply {
            val configSettings = com.google.firebase.remoteconfig.remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600 // 1시간마다 업데이트
            }
            setConfigSettingsAsync(configSettings)
        }

    @Provides
    fun provideAlarmDao(database: MiyaDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideAiScheduleDao(database: MiyaDatabase): AiScheduleDao = database.aiScheduleDao()

    @Provides
    fun providePersonaDao(database: MiyaDatabase): PersonaDao = database.personaDao()

    @Provides
    fun provideChatDao(database: MiyaDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideAlarmVoiceChunkDao(database: MiyaDatabase): AlarmVoiceChunkDao = database.alarmVoiceChunkDao()
}
