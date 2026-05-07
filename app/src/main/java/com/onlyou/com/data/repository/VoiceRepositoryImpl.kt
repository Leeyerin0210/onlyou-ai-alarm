package com.onlyou.com.data.repository

import android.content.Context
import android.util.Base64
import com.onlyou.com.data.local.AlarmVoiceChunkDao
import com.onlyou.com.data.local.AlarmVoiceChunkEntity
import com.onlyou.com.data.remote.AlarmScriptRequestDto
import com.onlyou.com.data.remote.MemoryItemDto
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.data.remote.VoiceCloneRequestDto
import com.onlyou.com.data.remote.VoiceSaveReferenceRequestDto
import com.onlyou.com.data.remote.VoiceSynthesizeRequestDto
import com.onlyou.com.domain.model.AlarmVoiceChunk
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.MemoryRepository
import com.onlyou.com.domain.repository.ScheduleRepository
import com.onlyou.com.domain.repository.VoiceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class VoiceRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val memoryRepository: MemoryRepository,
        private val scheduleRepository: ScheduleRepository,
        private val apiService: MiyaApiService,
        private val alarmVoiceChunkDao: AlarmVoiceChunkDao,
    ) : VoiceRepository {
        override suspend fun synthesizeVoice(
            text: String,
            persona: Persona,
        ): ByteArray? =
            withContext(Dispatchers.IO) {
                try {
                    val request = VoiceSynthesizeRequestDto(
                        text = text,
                        instruct = persona.voicePrompt,
                    )
                    val response = apiService.synthesizeVoice(request)
                    if (response.isSuccessful) {
                        response.body()?.bytes()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

        override suspend fun synthesizeVoiceCloned(
            text: String,
            personaId: String,
        ): ByteArray? =
            withContext(Dispatchers.IO) {
                try {
                    val request = VoiceCloneRequestDto(
                        text = text,
                        persona_id = personaId,
                    )
                    val response = apiService.cloneVoice(request)
                    if (response.isSuccessful) {
                        response.body()?.bytes()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

        override suspend fun saveReferenceVoice(
            personaId: String,
            audioData: ByteArray,
            refText: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
                    val request = VoiceSaveReferenceRequestDto(
                        audio = audioBase64,
                        ref_text = refText,
                    )
                    val response = apiService.saveVoiceReference(personaId, request)
                    response.isSuccessful
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

        override suspend fun getReferenceVoice(personaId: String): ByteArray? =
            withContext(Dispatchers.IO) {
                try {
                    val response = apiService.getReferenceVoice(personaId)
                    if (response.isSuccessful) {
                        response.body()?.bytes()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

        override fun generateWakeUpScriptStream(persona: Persona): Flow<String> =
            flow {
                try {
                    val today = LocalDate.now()
                    
                    // 1. 최근 메모리 로드
                    val recentMemories = memoryRepository
                        .getAllMemories()
                        .first()
                        .sortedByDescending { it.createdAt }
                        .take(5)

                    // 2. 오늘 일정 로드
                    val todaySchedules = scheduleRepository
                        .getAllSchedules()
                        .first()
                        .filter { it.date == today }
                        .sortedBy { it.startTime }

                    val memoryDtos = mutableListOf<MemoryItemDto>()
                    
                    // 메모리 추가
                    memoryDtos.addAll(recentMemories.map { m ->
                        MemoryItemDto(type = m.type.name, content = m.content)
                    })
                    
                    // 일정 추가 (특수한 포맷으로)
                    todaySchedules.forEach { s ->
                        val timeStr = s.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                        memoryDtos.add(
                            MemoryItemDto(
                                type = "SCHEDULE",
                                content = "[오늘 일정] $timeStr - ${s.title}"
                            )
                        )
                    }

                    val requestDto = AlarmScriptRequestDto(
                        persona_name = persona.name,
                        persona_prompt = persona.prompt ?: "",
                        user_call_sign = persona.userCallSign,
                        recent_memories = memoryDtos,
                    )

                    val response = apiService.generateAlarmScriptStream(requestDto)
                    if (response.isSuccessful) {
                        response.body()?.source()?.let { source ->
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line() ?: continue
                                if (line.startsWith("data: ")) {
                                    val dataStr = line.substring(6).trim()
                                    emit(dataStr)
                                }
                            }
                        }
                    } else {
                        emit("${persona.userCallSign}, 좋은 아침이에요! 오늘도 화이팅하세요.")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emit("${persona.userCallSign}, 좋은 아침이에요! 오늘도 화이팅하세요.")
                }
            }.flowOn(Dispatchers.IO)

        override suspend fun preGenerateAlarmVoice(
            alarmId: Int,
            persona: Persona,
        ): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    val today = LocalDate.now()
                    
                    // 1. 최근 메모리 로드
                    val recentMemories = memoryRepository
                        .getAllMemories()
                        .first()
                        .sortedByDescending { it.createdAt }
                        .take(5)
                        
                    // 2. 오늘 일정 로드
                    val todaySchedules = scheduleRepository
                        .getAllSchedules()
                        .first()
                        .filter { it.date == today }
                        .sortedBy { it.startTime }

                    val memoryDtos = mutableListOf<MemoryItemDto>()
                    
                    // 메모리 추가
                    memoryDtos.addAll(recentMemories.map { m ->
                        MemoryItemDto(type = m.type.name, content = m.content)
                    })
                    
                    // 일정 추가
                    todaySchedules.forEach { s ->
                        val timeStr = s.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                        memoryDtos.add(
                            MemoryItemDto(
                                type = "SCHEDULE",
                                content = "[오늘 일정] $timeStr - ${s.title}"
                            )
                        )
                    }

                    // 3. 알람 스크립트 청크 요청
                    val scriptRequest = AlarmScriptRequestDto(
                        persona_name = persona.name,
                        persona_prompt = persona.prompt ?: "",
                        user_call_sign = persona.userCallSign,
                        recent_memories = memoryDtos,
                    )

                    val scriptResponse = apiService.generateAlarmScript(scriptRequest)
                    val chunks = scriptResponse.chunks

                    // 4. 기존 캐시 삭제
                    alarmVoiceChunkDao.deleteChunksForAlarm(alarmId)

                    // 5. 각 청크별로 음성 합성 및 저장
                    chunks.forEachIndexed { index, text ->
                        var voiceBytes = synthesizeVoiceCloned(text, persona.id)
                        if (voiceBytes == null) {
                            voiceBytes = synthesizeVoice(text, persona)
                        }

                        if (voiceBytes != null) {
                            alarmVoiceChunkDao.insertChunk(
                                AlarmVoiceChunkEntity(
                                    alarmId = alarmId,
                                    chunkIndex = index,
                                    script = text,
                                    audioBytes = voiceBytes,
                                ),
                            )
                        }
                    }
                    true
                }.getOrElse {
                    it.printStackTrace()
                    false
                }
            }

        override suspend fun getCachedAlarmVoiceChunks(alarmId: Int): List<AlarmVoiceChunk> =
            withContext(Dispatchers.IO) {
                alarmVoiceChunkDao.getChunksForAlarm(alarmId).map {
                    AlarmVoiceChunk(
                        alarmId = it.alarmId,
                        chunkIndex = it.chunkIndex,
                        script = it.script,
                        audioBytes = it.audioBytes,
                    )
                }
            }
    }
