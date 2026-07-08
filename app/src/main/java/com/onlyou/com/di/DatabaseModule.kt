package com.onlyou.com.di

import android.content.Context
import androidx.room.Room
import com.onlyou.com.data.local.AiScheduleDao
import com.onlyou.com.data.local.AlarmDao
import com.onlyou.com.data.local.AlarmVoiceChunkDao
import com.onlyou.com.data.local.ChatDao
import com.onlyou.com.data.local.MemoryDao
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
            .addMigrations(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_17_18, MIGRATION_18_19)
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
    fun provideMemoryDao(database: MiyaDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideAlarmVoiceChunkDao(database: MiyaDatabase): AlarmVoiceChunkDao = database.alarmVoiceChunkDao()
}
