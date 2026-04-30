package com.onlyou.com.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.VoiceRepository
import com.onlyou.com.service.AlarmService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmPreGenReceiver : BroadcastReceiver() {
    @Inject lateinit var voiceRepository: VoiceRepository
    @Inject lateinit var personaRepository: PersonaRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
        val personaId = intent.getStringExtra(AlarmService.EXTRA_PERSONA_ID) ?: ""

        if (alarmId != -1) {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    val persona = personaRepository.getAllPersonas().first().find { it.id == personaId }
                        ?: personaRepository.getSelectedPersona().first()

                    if (persona != null) {
                        android.util.Log.d("AlarmPreGen", "Starting pre-generation for alarm $alarmId")
                        voiceRepository.preGenerateAlarmVoice(alarmId, persona)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
