package com.onlyou.com.data.repository

import android.content.Context
import com.onlyou.com.data.remote.AlarmScriptRequestDto
import com.onlyou.com.data.remote.MemoryItemDto
import com.onlyou.com.data.remote.MiyaApiService
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
        ): ByteArray? {
            // 안드로이드 기본 TTS는 ByteArray 반환이 까다로우므로
            // 여기서는 null을 반환하고 Service에서 직접 TTS를 호출하도록 설계 변경을 유도하거나
            // 추후 외부 TTS API 연동 시 구현합니다.
            return null
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
