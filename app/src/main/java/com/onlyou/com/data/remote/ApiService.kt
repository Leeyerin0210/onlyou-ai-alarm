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
}
