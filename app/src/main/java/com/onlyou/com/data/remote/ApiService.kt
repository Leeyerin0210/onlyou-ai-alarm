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

    @POST("voice/synthesize")
    suspend fun synthesizeVoice(
        @Body request: VoiceSynthesizeRequestDto,
    ): Response<ResponseBody>

    @DELETE("memory/clear")
    suspend fun clearMemory(): Response<Unit>
}
