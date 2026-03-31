package com.nemuria.miya.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nemuria.miya.domain.repository.AlarmRepository
import com.nemuria.miya.util.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 기기 재부팅 시 등록된 알람을 복구하는 리시버.
 *
 * AlarmManager의 알람은 기기 재부팅 시 초기화됩니다.
 * RECEIVE_BOOT_COMPLETED 인텐트를 받아 Room DB에서 활성 알람 목록을 조회하고
 * AlarmScheduler를 통해 재등록합니다.
 *
 * 추후 로그인 구현 후에도 이 로직은 변경 불필요합니다.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d("MiyaAlarm", "Boot completed — 활성 알람 재등록 시작")

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
