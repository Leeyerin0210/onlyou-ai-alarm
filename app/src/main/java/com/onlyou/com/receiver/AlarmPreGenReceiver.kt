package com.onlyou.com.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.onlyou.com.service.AlarmService
import com.onlyou.com.service.PreGenWorker

/**
 * 알람 사전 생성 트리거 리시버.
 * 무거운 네트워크 작업(스크립트+TTS 생성)은 WorkManager에 위임하여 ANR 방지.
 * BroadcastReceiver.onReceive() 자체는 10초, goAsync()는 60초 제한이 있으므로
 * 수십 초 이상 걸리는 작업은 절대 직접 수행하면 안 됨.
 */
class AlarmPreGenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
        val personaId = intent.getStringExtra(AlarmService.EXTRA_PERSONA_ID) ?: ""

        if (alarmId == -1) return

        android.util.Log.d("AlarmPreGen", "Received pre-gen trigger for alarm $alarmId → dispatching WorkManager")

        val inputData = Data.Builder()
            .putInt(PreGenWorker.KEY_ALARM_ID, alarmId)
            .putString(PreGenWorker.KEY_PERSONA_ID, personaId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<PreGenWorker>()
            .setInputData(inputData)
            .build()

        // 같은 알람 ID에 대한 중복 작업 방지
        WorkManager.getInstance(context).enqueueUniqueWork(
            "pre_gen_alarm_$alarmId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }
}
