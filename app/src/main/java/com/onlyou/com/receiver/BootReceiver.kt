package com.onlyou.com.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.onlyou.com.domain.repository.AlarmRepository
import com.onlyou.com.util.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 알람 복구 리시버.
 *
 * AlarmManager 등록은 재부팅 시 초기화되고, 앱 업데이트나 기기 시간/시간대 변경
 * 시에도 어긋날 수 있습니다. 아래 이벤트를 받아 Room DB의 활성 알람을
 * AlarmScheduler로 전부 재등록합니다.
 * - BOOT_COMPLETED: 재부팅
 * - MY_PACKAGE_REPLACED: 앱 업데이트 (일부 OEM은 업데이트 시 알람을 날림)
 * - TIME_SET / TIMEZONE_CHANGED: 벽시계 기준으로 다음 발화 시각 재계산
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var alarmRepository: AlarmRepository

    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, // == android.intent.action.TIME_SET
            Intent.ACTION_TIMEZONE_CHANGED,
        )
        if (intent.action !in supportedActions) return

        Log.d("MiyaAlarm", "${intent.action} — 활성 알람 재등록 시작")

        // BroadcastReceiver에서 coroutine 사용 시 goAsync() 패턴 필요
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val enabledAlarms = alarmRepository.getEnabledAlarms()
                enabledAlarms.forEach { alarm ->
                    alarmScheduler.schedule(alarm)
                    Log.d("MiyaAlarm", "알람 재등록: ID=${alarm.id}, 시간=${alarm.time}")
                }
                Log.d("MiyaAlarm", "총 ${enabledAlarms.size}개 알람 재등록 완료")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
