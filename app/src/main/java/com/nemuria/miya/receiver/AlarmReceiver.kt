package com.nemuria.miya.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val voiceId = intent.getStringExtra("ALARM_VOICE") ?: "default"
        
        Log.d("MiyaAlarm", "Alarm Received: ID=$alarmId, Voice=$voiceId")

        // Start a service or show a notification to play the sound
        // For simplicity, we'll just log it for now.
        // In a real implementation, you'd start an AlarmActivity or a Foreground Service.
    }
}
