package com.nemuria.miya.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nemuria.miya.service.AlarmService
import com.nemuria.miya.ui.alarm.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val voiceId = intent.getStringExtra(AlarmService.EXTRA_ALARM_VOICE) ?: "default"
        val alarmTitle = intent.getStringExtra(AlarmService.EXTRA_ALARM_TITLE) ?: ""

        Log.d("MiyaAlarm", "Alarm Received: ID=$alarmId, Voice=$voiceId")

        // 안드로이드 10 이상부터 화면이 꺼진 Background 상황에서 Receiver가 
        // startActivity를 강제 호출하면 보안제약에 걸려 무시되거나 에러(BAL 제약)가 납니다.
        // 대신 AlarmService를 Foreground로 시작하고, 그 안에서 FullScreenIntent를 갖는 
        // Notification을 시스템에 띄우도록(AlarmService 내부 구현) 이관합니다!

        // Foreground Service로 소리 + 진동 재생
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_ALARM_TITLE, alarmTitle)
            putExtra(AlarmService.EXTRA_ALARM_VOICE, voiceId)
        }
        context.startForegroundService(serviceIntent)
    }
}
