package com.nemuria.miya.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.firestore.FirebaseFirestore
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
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MiyaDatabase {
        return Room.databaseBuilder(
            context,
            MiyaDatabase::class.java,
            "miya_database"
        )
        .fallbackToDestructiveMigration() // 버전이 바뀌면 기존 데이터를 지우고 다시 생성
        .build()
    }

    @Provides
    fun provideAlarmDao(database: MiyaDatabase): AlarmDao {
        return database.alarmDao()
    }

    @Provides
    fun provideStreamScheduleDao(database: MiyaDatabase): com.nemuria.miya.data.local.StreamScheduleDao {
        return database.streamScheduleDao()
    }
}
