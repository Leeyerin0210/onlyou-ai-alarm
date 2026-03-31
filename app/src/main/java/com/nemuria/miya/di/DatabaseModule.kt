package com.nemuria.miya.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.firestore.FirebaseFirestore
import com.nemuria.miya.data.local.AlarmDao
import com.nemuria.miya.data.local.ArtistDao
import com.nemuria.miya.data.local.MiyaDatabase
import com.nemuria.miya.data.local.VoiceAssetDao
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
    fun provideAlarmDao(database: MiyaDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideStreamScheduleDao(database: MiyaDatabase): com.nemuria.miya.data.local.StreamScheduleDao =
        database.streamScheduleDao()

    @Provides
    fun provideArtistDao(database: MiyaDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideVoiceAssetDao(database: MiyaDatabase): VoiceAssetDao = database.voiceAssetDao()
}
