package com.nemuria.miya.data.repository

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.nemuria.miya.domain.model.Persona
import com.nemuria.miya.domain.repository.MemoryRepository
import com.nemuria.miya.domain.repository.VoiceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class VoiceRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val remoteConfig: com.google.firebase.remoteconfig.FirebaseRemoteConfig,
        private val memoryRepository: MemoryRepository,
    ) : VoiceRepository {
        private fun getApiKey(): String = remoteConfig.getString("llm_api_key")

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
                    val apiKey = getApiKey()
                    if (apiKey.isBlank()) return@withContext "${persona.userCallSign}, 일어날 시간이에요!"

                    // 최근 메모리 5개 로드
                    val recentMemories = memoryRepository.getAllMemories().first()
                        .sortedByDescending { it.createdAt }
                        .take(5)

                    val memoryContextMsg = if (recentMemories.isNotEmpty()) {
                        val memoryLines = recentMemories.joinToString("\n") { "- [${it.type}] ${it.content}" }
                        "\n\n최근 사용자의 상태 및 일정 기록:\n$memoryLines\n\n지침: 위 기록을 참고하여 사용자에게 필요한 위로나 응원, 혹은 스케줄 리마인드를 곁들여 깨워주세요."
                    } else {
                        ""
                    }

                    val generativeModel = GenerativeModel(
                        modelName = "gemini-3-flash-preview",
                        apiKey = apiKey,
                        systemInstruction = content {
                            text(
                                "당신은 유저를 깨워주는 AI 파트너 '${persona.name}'입니다. " +
                                    "${persona.prompt} " +
                                    "지침: 유저의 이름('${persona.userCallSign}')을 부르며 다정하거나 성격에 맞게 깨워주세요. " +
                                    "답변은 아주 짧게 한 두 문단으로 끝내세요." +
                                    memoryContextMsg
                            )
                        },
                    )

                    val response = generativeModel.generateContent("지금은 아침이에요. 저를 깨워주는 멘트를 해주세요.")
                    response.text ?: "${persona.userCallSign}, 좋은 아침이에요! 오늘도 화이팅하세요."
                } catch (e: Exception) {
                    e.printStackTrace()
                    "${persona.userCallSign}, 좋은 아침이에요! 오늘도 화이팅하세요."
                }
            }
    }
