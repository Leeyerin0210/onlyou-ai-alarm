package com.onlyou.com.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Streaming

interface MiyaApiService {
    @Streaming
    @POST("chat/stream")
    suspend fun chatStream(
        @Body request: ChatRequestDto,
    ): Response<ResponseBody>

    @POST("memory/extract")
    suspend fun extractMemory(
        @Body request: MemoryExtractRequestDto,
    ): List<MemoryItemDto>

    @POST("alarm/script")
    suspend fun generateAlarmScript(
        @Body request: AlarmScriptRequestDto,
    ): AlarmScriptResponseDto

    @Streaming
    @POST("alarm/script/stream")
    suspend fun generateAlarmScriptStream(
        @Body request: AlarmScriptRequestDto,
    ): Response<ResponseBody>

    @POST("voice/synthesize")
    suspend fun synthesizeVoice(
        @Body request: VoiceSynthesizeRequestDto,
    ): Response<ResponseBody>

    @POST("voice/save_reference/{persona_id}")
    suspend fun saveVoiceReference(
        @retrofit2.http.Path("persona_id") personaId: String,
        @Body request: VoiceSaveReferenceRequestDto
    ): Response<Unit>

    @POST("voice/clone")
    suspend fun cloneVoice(
        @Body request: VoiceCloneRequestDto
    ): Response<ResponseBody>

    @retrofit2.http.GET("voice/reference/{persona_id}")
    suspend fun getReferenceVoice(
        @retrofit2.http.Path("persona_id") personaId: String
    ): Response<ResponseBody>

    @DELETE("voice/reference/{persona_id}")
    suspend fun deleteVoiceReference(
        @retrofit2.http.Path("persona_id") personaId: String
    ): Response<Unit>

    @DELETE("memory/clear")
    suspend fun clearMemory(): Response<Unit>

    // Presets (성격 프리셋 카탈로그)
    @retrofit2.http.GET("presets")
    suspend fun getPresets(): List<PresetDto>

    // Personas
    @retrofit2.http.GET("personas")
    suspend fun getPersonas(): List<PersonaDto>

    @retrofit2.http.PUT("personas/{id}")
    suspend fun upsertPersona(
        @retrofit2.http.Path("id") id: String,
        @Body body: PersonaDto,
    ): Response<Unit>

    @DELETE("personas/{id}")
    suspend fun deletePersona(@retrofit2.http.Path("id") id: String): Response<Unit>

    @POST("personas/{id}/select")
    suspend fun selectPersona(@retrofit2.http.Path("id") id: String): Response<Unit>

    // Users
    @retrofit2.http.GET("users/me")
    suspend fun getMe(): UserProfileDto

    @retrofit2.http.PUT("users/me")
    suspend fun putMe(@Body body: UserProfilePutDto): Response<Unit>

    // 회원 탈퇴: 서버에 저장된 내 개인정보(프로필/백업/일정/페르소나) 전체 파기
    @DELETE("users/me")
    suspend fun deleteMe(): Response<Unit>

    // Schedules
    @retrofit2.http.GET("schedules")
    suspend fun getSchedules(): List<ScheduleDto>

    @retrofit2.http.PUT("schedules/{id}")
    suspend fun upsertSchedule(
        @retrofit2.http.Path("id") id: String,
        @Body body: ScheduleDto,
    ): Response<Unit>

    // Backups
    @retrofit2.http.GET("backups")
    suspend fun getBackup(): Response<BackupDto>

    @retrofit2.http.PUT("backups")
    suspend fun putBackup(@Body body: BackupDto): Response<Unit>
}
