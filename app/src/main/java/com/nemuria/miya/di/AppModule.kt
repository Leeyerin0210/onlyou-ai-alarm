package com.nemuria.miya.di

import com.nemuria.miya.data.repository.AlarmRepositoryImpl
import com.nemuria.miya.data.repository.ScheduleRepositoryImpl
import com.nemuria.miya.domain.repository.AlarmRepository
import com.nemuria.miya.domain.repository.ScheduleRepository
import com.nemuria.miya.util.DateTimeProvider
import com.nemuria.miya.util.DefaultDateTimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAlarmRepository(
        alarmRepositoryImpl: AlarmRepositoryImpl,
    ): AlarmRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(
        scheduleRepositoryImpl: ScheduleRepositoryImpl,
    ): ScheduleRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDateTimeProvider(): DateTimeProvider = DefaultDateTimeProvider()
}
