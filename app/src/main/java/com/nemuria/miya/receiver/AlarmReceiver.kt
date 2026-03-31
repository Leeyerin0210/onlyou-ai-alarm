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

        // AlarmManager가 BroadcastReceiver에게 10초간 BAL 허용 창을 부여함
        // 이 창 안에서 startActivity()를 호출하면 전체화면으로 즉시 실행됨
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_ALARM_TITLE, alarmTitle)
            putExtra(AlarmService.EXTRA_ALARM_VOICE, voiceId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(activityIntent)

        // Foreground Service로 소리 + 진동 재생
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_ALARM_TITLE, alarmTitle)
            putExtra(AlarmService.EXTRA_ALARM_VOICE, voiceId)
        }
        context.startForegroundService(serviceIntent)
    }
}
