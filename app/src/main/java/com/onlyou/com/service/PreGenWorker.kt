package com.onlyou.com.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.VoiceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PreGenWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val voiceRepository: VoiceRepository,
    private val personaRepository: PersonaRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_ALARM_ID = "alarm_id"
        const val KEY_PERSONA_ID = "persona_id"
    }

    override suspend fun doWork(): Result {
        val alarmId = inputData.getInt(KEY_ALARM_ID, -1)
        val personaId = inputData.getString(KEY_PERSONA_ID) ?: ""

        if (alarmId == -1) {
            android.util.Log.e("PreGenWorker", "Invalid alarm ID: -1")
            return Result.failure()
        }

        android.util.Log.d("PreGenWorker", "Starting pre-generation for alarm $alarmId with personaId: $personaId")

        // 1. 페르소나 조회
        val persona = personaRepository.getAllPersonas().first().find { it.id == personaId }
            ?: personaRepository.getSelectedPersona().first()

        if (persona == null) {
            android.util.Log.e("PreGenWorker", "Persona not found for ID: $personaId and no selected persona found.")
            return Result.failure()
        }

        android.util.Log.d("PreGenWorker", "Resolved persona: ${persona.name} (ID: ${persona.id})")

        // 2. 음성 사전 생성 및 로컬 캐싱
        val success = voiceRepository.preGenerateAlarmVoice(alarmId, persona)

        return if (success) {
            android.util.Log.d("PreGenWorker", "Pre-generation successful for alarm $alarmId")
            Result.success()
        } else {
            android.util.Log.e("PreGenWorker", "Pre-generation failed for alarm $alarmId")
            if (runAttemptCount < 3) {
                android.util.Log.d("PreGenWorker", "Retrying pre-generation (Attempt: ${runAttemptCount + 1})")
                Result.retry()
            } else {
                android.util.Log.e("PreGenWorker", "Pre-generation failed after max retries.")
                Result.failure()
            }
        }
    }
}
