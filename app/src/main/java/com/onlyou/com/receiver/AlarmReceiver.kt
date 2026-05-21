package com.onlyou.com.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.onlyou.com.domain.repository.AlarmRepository
import com.onlyou.com.service.AlarmService
import com.onlyou.com.util.AlarmScheduler
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @dagger.hilt.InstallIn(SingletonComponent::class)
    interface AlarmReceiverEntryPoint {
        fun alarmRepository(): AlarmRepository
        fun alarmScheduler(): AlarmScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
        val personaId = intent.getStringExtra(AlarmService.EXTRA_PERSONA_ID) ?: "default"
        val alarmTitle = intent.getStringExtra(AlarmService.EXTRA_ALARM_TITLE) ?: ""

        Log.d("MiyaAlarm", "Alarm Received: ID=$alarmId, Persona=$personaId")

        // ① Foreground Service로 소리 + 진동 재생
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_ALARM_TITLE, alarmTitle)
            putExtra(AlarmService.EXTRA_PERSONA_ID, personaId)
        }
        context.startForegroundService(serviceIntent)

        // ② 하루 1회 보장: 알람이 울리면 다음날 같은 시간으로 자동 재스케줄
        // Room DB 읽기 + AlarmManager 설정은 빠른 작업이라 goAsync() 60초 안에 충분히 완료됨
        if (alarmId != -1) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        AlarmReceiverEntryPoint::class.java
                    )
                    val alarmRepository = entryPoint.alarmRepository()
                    val alarmScheduler = entryPoint.alarmScheduler()

                    val alarm = alarmRepository.getAlarmById(alarmId)
                    if (alarm != null && alarm.isEnabled) {
                        // 요일 반복이 있는 경우만 다음 해당 요일로 재스케줄
                        if (alarm.repeatDays.isNotEmpty()) {
                            Log.d("MiyaAlarm", "Re-scheduling alarm $alarmId for next occurrence")
                            alarmScheduler.schedule(alarm)
                        } else {
                            // 1회성 알람 (특정 날짜 지정이거나 반복 없음)은 울린 후 비활성화
                            alarmRepository.updateAlarm(alarm.copy(isEnabled = false))
                            Log.d("MiyaAlarm", "One-time alarm $alarmId disabled after firing")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MiyaAlarm", "Failed to reschedule alarm", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
