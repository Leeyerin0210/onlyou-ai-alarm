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
     * - 앱 시작: KEEP (이미 있으면 유지)
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
