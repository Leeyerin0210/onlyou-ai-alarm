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
            // 앱 시작마다 CANCEL_AND_REENQUEUE로 재정렬하는 것은 의도된 동작이다:
            // 주기 작업은 직전 실행 시점을 기준으로 다음 실행을 앵커링하므로, 발송 윈도우(21~23시) 밖에서
            // 단 한 번이라도 실행되면(예: 폰이 꺼져있다가 부팅 시 07:30에 실행) 이후 모든 실행이
            // 그 시각에 영구적으로 고정되어 윈도우 밖으로 영원히 벗어난다. KEEP은 이 드리프트를 그대로
            // 유지시키므로, 앱을 켤 때마다 설정된 시각으로 재정렬해 드리프트를 복구한다.
            eveningFeedbackScheduler.schedule(
                settings.hour,
                settings.minute,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            )
        }
    }
}
