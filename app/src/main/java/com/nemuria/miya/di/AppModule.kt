package com.nemuria.miya.di

import com.nemuria.miya.data.repository.AlarmRepositoryImpl
import com.nemuria.miya.data.repository.ArtistRepositoryImpl
import com.nemuria.miya.data.repository.ScheduleRepositoryImpl
import com.nemuria.miya.data.repository.VoiceRepositoryImpl
import com.nemuria.miya.domain.repository.AlarmRepository
import com.nemuria.miya.domain.repository.ArtistRepository
import com.nemuria.miya.domain.repository.ScheduleRepository
import com.nemuria.miya.domain.repository.VoiceRepository
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

    @Binds
    @Singleton
    abstract fun bindArtistRepository(
        artistRepositoryImpl: ArtistRepositoryImpl,
    ): ArtistRepository

    @Binds
    @Singleton
    abstract fun bindVoiceRepository(
        voiceRepositoryImpl: VoiceRepositoryImpl,
    ): VoiceRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: com.nemuria.miya.data.repository.AuthRepositoryImpl,
    ): com.nemuria.miya.domain.repository.AuthRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDateTimeProvider(): DateTimeProvider = DefaultDateTimeProvider()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): com.google.firebase.auth.FirebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideCredentialManager(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): androidx.credentials.CredentialManager = androidx.credentials.CredentialManager.create(context)
}
