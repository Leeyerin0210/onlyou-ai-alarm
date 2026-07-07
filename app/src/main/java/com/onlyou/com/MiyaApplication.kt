package com.onlyou.com

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import com.onlyou.com.domain.repository.FeedbackSettingsRepository
import com.onlyou.com.service.EveningFeedbackScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MiyaApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var feedbackSettingsRepository: FeedbackSettingsRepository

    @Inject
    lateinit var eveningFeedbackScheduler: EveningFeedbackScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        val settings = feedbackSettingsRepository.settings.value
        if (settings.enabled) {
            // WorkManager는 재부팅 후에도 작업을 복원하므로 부팅 리시버는 불필요.
            eveningFeedbackScheduler.schedule(
                settings.hour,
                settings.minute,
                ExistingPeriodicWorkPolicy.KEEP,
            )
        }
    }
}
