package com.onlyou.com.domain.repository

import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.domain.model.AlarmVoiceChunk
import com.onlyou.com.domain.model.ChatEvent
import com.onlyou.com.domain.model.ChatMessage
import com.onlyou.com.domain.model.Memory
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.domain.model.Persona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AlarmRepository {
    fun getAllAlarms(): Flow<List<MiyaAlarm>>

    suspend fun getEnabledAlarms(): List<MiyaAlarm>

    suspend fun getAlarmById(id: Int): MiyaAlarm?

    suspend fun insertAlarm(alarm: MiyaAlarm): Int

    suspend fun updateAlarm(alarm: MiyaAlarm)

    suspend fun deleteAlarm(alarm: MiyaAlarm)
}

/**
 * AI 기반 일정 관리
 */
interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<AiSchedule>>

    suspend fun insertSchedule(schedule: AiSchedule)

    suspend fun updateSchedule(schedule: AiSchedule)

    suspend fun deleteSchedule(schedule: AiSchedule)

    suspend fun syncSchedules() // 서버와 로컬 DB 동기화 (pull + 재시도)
}

/**
 * AI 페르소나 관리 및 구매 정보
 */
interface PersonaRepository {
    fun getAllPersonas(): Flow<List<Persona>>

    fun getSelectedPersona(): Flow<Persona?>

    /**
     * 서버와 로컬 DB 동기화.
     * @return 서버 도달 및 동기화 성공 여부. 서버에 닿지 못하면 false(=오프라인).
     */
    suspend fun syncPersonas(): Boolean

    suspend fun deletePersona(personaId: String)

    suspend fun setSelectedPersona(personaId: String)

    suspend fun updatePersona(persona: Persona)

    suspend fun upsertPersona(persona: Persona)
}

/**
 * AI 채팅 기록 관리
 */
interface ChatRepository {
    fun getChatMessages(): Flow<List<ChatMessage>>

    fun sendMessage(
        message: ChatMessage,
        persona: Persona,
    ): Flow<ChatEvent>

    suspend fun clearHistory()

    /**
     * 유저 발화 없이 시스템 지시로 AI 선톡을 생성한다.
     * 지시문은 유저 메시지로 저장하지 않고, 생성된 응답만 AI 메시지로 저장한다.
     * @return 생성된 텍스트, 실패(네트워크 오류 포함)나 빈 응답이면 null. 예외를 던지지 않는다.
     */
    suspend fun sendProactiveMessage(instruction: String, persona: Persona): String?
}

/**
 * AI가 기억하는 유저의 맥락(메모리) 관리
 */
interface MemoryRepository {
    fun getAllMemories(): Flow<List<Memory>>

    suspend fun addMemory(memory: Memory)

    suspend fun deleteMemory(memoryId: String)

    suspend fun clearOldMemories()
}

/**
 * 커스텀 TTS 음성 합성 및 재생 관리
 */
interface VoiceRepository {
    /**
     * AI의 멘트를 음성으로 변환하여 반환 (Voice Design 방식)
     */
    suspend fun synthesizeVoice(
        text: String,
        persona: Persona,
    ): ByteArray?

    /**
     * 저장된 참조 음성을 기반으로 음성 합성 (Voice Clone 방식)
     */
    suspend fun synthesizeVoiceCloned(
        text: String,
        personaId: String,
    ): ByteArray?

    /**
     * 디자인된 음성을 참조용으로 서버에 저장
     */
    suspend fun saveReferenceVoice(
        personaId: String,
        audioData: ByteArray,
        refText: String,
    ): Boolean

    /**
     * 서버에 저장된 마스터 참조 음성을 가져옴
     */
    suspend fun getReferenceVoice(personaId: String): ByteArray?

    /**
     * 서버에 저장된 참조 음성을 삭제함
     */
    suspend fun deleteReferenceVoice(personaId: String): Boolean

    /**
     * 기상 알람을 위한 초개인화 스크립트 생성 (스트리밍)
     */
    fun generateWakeUpScriptStream(persona: Persona): Flow<String>

    /**
     * 알람이 울리기 전 보이스를 미리 생성하여 캐시함
     */
    suspend fun preGenerateAlarmVoice(
        alarmId: Int,
        persona: Persona,
    ): Boolean

    /**
     * 캐시된 알람 보이스 청크들을 가져옴
     */
    suspend fun getCachedAlarmVoiceChunks(alarmId: Int): List<AlarmVoiceChunk>
}

data class EveningFeedbackSettings(
    val enabled: Boolean = true,
    val hour: Int = 21,
    val minute: Int = 0,
)

interface FeedbackSettingsRepository {
    val settings: StateFlow<EveningFeedbackSettings>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setTime(hour: Int, minute: Int)
}
