package com.nemuria.miya.util

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.nemuria.miya.domain.model.Memory
import com.nemuria.miya.domain.model.MemoryType
import org.json.JSONArray
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractor @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) {
    private fun getApiKey(): String = remoteConfig.getString("llm_api_key")

    suspend fun extractMemories(userMessage: String): List<Memory> {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            return emptyList()
        }

        val prompt = """
            다음 사용자의 메시지를 분석해서 중요한 맥락을 기억(Memory)으로 추출해.
            해당하는 내용이 없으면 완전히 빈 JSON 배열 [] 을 반환해.
            반드시 다음과 같은 JSON 배열 형식으로만 응답해. JSON 외에 아무 말도 하지 마.

            JSON 형식:
            [
              {
                "type": "SCHEDULE" 또는 "STATE" 또는 "PREFERENCE" 또는 "USER_NOTE",
                "content": "추출된 구체적인 내용 요약 (예: 월요일 오후 3시 회의, 지금 우울함, 매운 것을 좋아함, 유저는 따뜻한 말을 들을 때 감동을 잘 받는 성격임)"
              }
            ]

            [타입 설명]
            - SCHEDULE: 일정 또는 약속
            - STATE: 유저의 현재 기분, 건강 상태, 상황
            - PREFERENCE: 유저의 취향, 좋아하는 것, 싫어하는 것
            - USER_NOTE: 유저의 성격, 특징, 당신(AI)이 유저를 어떻게 대해야 할지에 대한 팁 (예: 유저는 칭찬을 좋아함, 유저는 혼자 있는 시간을 중요하게 생각함)

            사용자 메시지: "$userMessage"
        """.trimIndent()

        return try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3-flash-preview",
                apiKey = apiKey,
                systemInstruction = content { text("You are a specialized JSON data extractor. Return only a valid JSON array.") },
            )

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: ""

            // Markdown Code Block(```json 등)이 섞여있을 수 있으므로 정규식으로 제거 후 트림
            val jsonString = responseText.replace(Regex("```(json)?|```"), "").trim()
            if (jsonString.isBlank() || jsonString == "[]") return emptyList()

            val jsonArray = JSONArray(jsonString)
            val memories = mutableListOf<Memory>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val typeStr = obj.optString("type")
                val content = obj.optString("content")

                val type = runCatching { MemoryType.valueOf(typeStr) }.getOrNull()
                if (type != null && content.isNotBlank()) {
                    memories.add(
                        Memory(
                            id = UUID.randomUUID().toString(),
                            type = type,
                            content = content,
                            targetDate = null,
                            createdAt = LocalDateTime.now(),
                        ),
                    )
                }
            }
            memories
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
