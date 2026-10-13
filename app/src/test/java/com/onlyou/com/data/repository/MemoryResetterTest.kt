package com.onlyou.com.data.repository

import com.onlyou.com.data.local.ChatDao
import com.onlyou.com.data.local.ChatMessageEntity
import com.onlyou.com.data.remote.AlarmScriptRequestDto
import com.onlyou.com.data.remote.AlarmScriptResponseDto
import com.onlyou.com.data.remote.BackupDto
import com.onlyou.com.data.remote.ChatRequestDto
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.data.remote.PersonaDto
import com.onlyou.com.data.remote.PresetDto
import com.onlyou.com.data.remote.ScheduleDto
import com.onlyou.com.data.remote.UserProfileDto
import com.onlyou.com.data.remote.UserProfilePutDto
import com.onlyou.com.data.remote.VoiceCloneRequestDto
import com.onlyou.com.data.remote.VoiceSaveReferenceRequestDto
import com.onlyou.com.data.remote.VoiceSynthesizeRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * "모든 기억 초기화"의 핵심 계약을 검증한다.
 *
 * 기억은 서버가 단일 소유하므로 지울 곳은 두 군데다 — 서버 벡터 기억과
 * 로컬 대화 기록. 순서가 계약이다: 서버 삭제가 실패했는데 로컬 대화만 지우면
 * "AI는 기억하는데 유저 화면엔 아무것도 없는" 상태가 된다.
 */
class MemoryResetterTest {

    @Test
    fun `서버 삭제가 성공하면 대화 기록이 삭제된다`() = runBlocking {
        val chatDao = FakeChatDao()
        val api = FakeApiService(clearMemoryResult = Response.success(Unit))

        val result = MemoryResetter(chatDao, api).resetAll()

        assertTrue(result)
        assertEquals(1, api.clearMemoryCallCount)
        assertTrue(chatDao.cleared)
    }

    @Test
    fun `서버 삭제가 실패하면 로컬 대화를 그대로 둔다`() = runBlocking {
        val chatDao = FakeChatDao()
        val api = FakeApiService(
            clearMemoryResult = Response.error(500, "".toResponseBody(null)),
        )

        val result = MemoryResetter(chatDao, api).resetAll()

        assertFalse(result)
        assertFalse(chatDao.cleared)
    }

    @Test
    fun `네트워크 예외가 나도 던지지 않고 로컬 대화를 보존한다`() = runBlocking {
        val chatDao = FakeChatDao()
        val api = FakeApiService(clearMemoryError = java.io.IOException("offline"))

        val result = MemoryResetter(chatDao, api).resetAll()

        assertFalse(result)
        assertFalse(chatDao.cleared)
    }
}

private class FakeChatDao : ChatDao {
    var cleared = false

    override fun getChatMessages(): Flow<List<ChatMessageEntity>> = flowOf(emptyList())

    override suspend fun getChatMessagesOnce(): List<ChatMessageEntity> = emptyList()

    override suspend fun insertMessage(message: ChatMessageEntity) = Unit

    override suspend fun clearHistory() {
        cleared = true
    }
}

/**
 * clearMemory만 실제로 동작하는 스텁. 나머지는 이 테스트에서 호출되면 안 되므로
 * 호출 시 즉시 실패하도록 둔다.
 */
private class FakeApiService(
    private val clearMemoryResult: Response<Unit> = Response.success(Unit),
    private val clearMemoryError: Throwable? = null,
) : MiyaApiService {
    var clearMemoryCallCount = 0

    override suspend fun clearMemory(): Response<Unit> {
        clearMemoryCallCount++
        clearMemoryError?.let { throw it }
        return clearMemoryResult
    }

    private fun unused(): Nothing = throw AssertionError("이 테스트에서 호출되면 안 되는 API")

    override suspend fun chatStream(request: ChatRequestDto): Response<ResponseBody> = unused()

    override suspend fun generateAlarmScript(request: AlarmScriptRequestDto): AlarmScriptResponseDto = unused()

    override suspend fun generateAlarmScriptStream(request: AlarmScriptRequestDto): Response<ResponseBody> = unused()

    override suspend fun synthesizeVoice(request: VoiceSynthesizeRequestDto): Response<ResponseBody> = unused()

    override suspend fun saveVoiceReference(personaId: String, request: VoiceSaveReferenceRequestDto): Response<Unit> = unused()

    override suspend fun cloneVoice(request: VoiceCloneRequestDto): Response<ResponseBody> = unused()

    override suspend fun getReferenceVoice(personaId: String): Response<ResponseBody> = unused()

    override suspend fun deleteVoiceReference(personaId: String): Response<Unit> = unused()

    override suspend fun getPresets(): List<PresetDto> = unused()

    override suspend fun getPersonas(): List<PersonaDto> = unused()

    override suspend fun getFeaturedPersonas(): com.onlyou.com.data.remote.FeaturedDto = unused()

    override suspend fun upsertPersona(id: String, body: PersonaDto): Response<Unit> = unused()

    override suspend fun deletePersona(id: String): Response<Unit> = unused()

    override suspend fun selectPersona(id: String): Response<Unit> = unused()

    override suspend fun getMe(): UserProfileDto = unused()

    override suspend fun putMe(body: UserProfilePutDto): Response<Unit> = unused()

    override suspend fun deleteMe(): Response<Unit> = unused()

    override suspend fun getSchedules(): List<ScheduleDto> = unused()

    override suspend fun upsertSchedule(id: String, body: ScheduleDto): Response<Unit> = unused()

    override suspend fun getBackup(): Response<BackupDto> = unused()

    override suspend fun putBackup(body: BackupDto): Response<Unit> = unused()
}
