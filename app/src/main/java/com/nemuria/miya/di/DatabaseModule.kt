package com.nemuria.miya.di

import android.content.Context
import androidx.room.Room
import com.nemuria.miya.data.local.AlarmDao
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
    fun provideDatabase(@ApplicationContext context: Context): MiyaDatabase {
        return Room.databaseBuilder(
            context,
            MiyaDatabase::class.java,
            "miya_database"
        ).build()
    }

    @Provides
    fun provideAlarmDao(database: MiyaDatabase): AlarmDao {
        return database.alarmDao()
    }
}
