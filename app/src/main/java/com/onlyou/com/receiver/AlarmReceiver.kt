package com.onlyou.com.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.onlyou.com.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
        val personaId = intent.getStringExtra(AlarmService.EXTRA_PERSONA_ID) ?: "default"
        val alarmTitle = intent.getStringExtra(AlarmService.EXTRA_ALARM_TITLE) ?: ""

        Log.d("MiyaAlarm", "Alarm Received: ID=$alarmId, Persona=$personaId")

        // Foreground Service로 소리 + 진동 재생
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_ALARM_TITLE, alarmTitle)
            putExtra(AlarmService.EXTRA_PERSONA_ID, personaId)
        }
        context.startForegroundService(serviceIntent)
    }
}
