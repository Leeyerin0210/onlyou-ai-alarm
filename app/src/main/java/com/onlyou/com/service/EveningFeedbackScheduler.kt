package com.onlyou.com.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.onlyou.com.util.initialDelayMillis
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EveningFeedbackScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val WORK_NAME = "evening_feedback"
    }

    /**
     * 매일 hour:minute 근처에 도는 주기 작업을 등록한다.
     * - 앱 시작: CANCEL_AND_REENQUEUE (드리프트 재정렬)
     *   주기 작업은 직전 실행 시점 기준으로 앵커되므로, 발송 윈도우 밖 실행이 한 번만 생겨도
     *   이후 스케줄이 그 시각에 영구히 고정된다. 이를 막기 위해 앱 시작 시마다 설정된 시각으로 재정렬한다.
     * - 설정 변경: CANCEL_AND_REENQUEUE (새 시각으로 리셋)
     * 워커 내부에서 자기 이름으로 재등록하면 실행 중인 자신이 취소되므로 금지.
     */
    fun schedule(hour: Int, minute: Int, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<EveningFeedbackWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis(LocalDateTime.now(), hour, minute), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
