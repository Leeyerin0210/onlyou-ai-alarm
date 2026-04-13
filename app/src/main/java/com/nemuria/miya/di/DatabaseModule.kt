package com.nemuria.miya.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.firestore.FirebaseFirestore
import com.nemuria.miya.data.local.AlarmDao
import com.nemuria.miya.data.local.AiScheduleDao
import com.nemuria.miya.data.local.PersonaDao
import com.nemuria.miya.data.local.ChatDao
import com.nemuria.miya.data.local.MemoryDao
import com.nemuria.miya.data.local.MiyaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MiyaDatabase {
        return Room.databaseBuilder(
            context,
            MiyaDatabase::class.java,
            "miya_database",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideRemoteConfig(): com.google.firebase.remoteconfig.FirebaseRemoteConfig {
        return com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance().apply {
            val configSettings = com.google.firebase.remoteconfig.remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600 // 1시간마다 업데이트
            }
            setConfigSettingsAsync(configSettings)
        }
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
}
