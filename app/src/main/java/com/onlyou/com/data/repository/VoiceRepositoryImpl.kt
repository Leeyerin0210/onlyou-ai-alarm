package com.onlyou.com.data.repository

import android.content.Context
import com.onlyou.com.data.remote.AlarmScriptRequestDto
import com.onlyou.com.data.remote.MemoryItemDto
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.data.remote.VoiceSynthesizeRequestDto
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.MemoryRepository
import com.onlyou.com.domain.repository.VoiceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class VoiceRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val memoryRepository: MemoryRepository,
        private val apiService: MiyaApiService,
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

        override suspend fun generateWakeUpScript(persona: Persona): String =
            withContext(Dispatchers.IO) {
                try {
                    // 최근 메모리 5개 로드
                    val recentMemories = memoryRepository
                        .getAllMemories()
                        .first()
                        .sortedByDescending { it.createdAt }
                        .take(5)

                    val memoryDtos = recentMemories.map { m ->
                        MemoryItemDto(type = m.type.name, content = m.content)
                    }

                    val requestDto = AlarmScriptRequestDto(
                        persona_name = persona.name,
                        persona_prompt = persona.prompt ?: "",
                        user_call_sign = persona.userCallSign,
                        recent_memories = memoryDtos,
                    )

                    val response = apiService.generateAlarmScript(requestDto)
                    response.script
                } catch (e: Exception) {
                    e.printStackTrace()
                    "${persona.userCallSign}, 좋은 아침이에요! 오늘도 화이팅하세요."
                }
            }
    }
