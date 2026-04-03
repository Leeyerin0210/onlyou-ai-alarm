package com.nemuria.miya.domain.repository

import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.domain.model.StreamSchedule
import com.nemuria.miya.domain.model.VoiceAsset
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun getAllAlarms(): Flow<List<MiyaAlarm>>
    suspend fun getEnabledAlarms(): List<MiyaAlarm>
    suspend fun getAlarmById(id: Int): MiyaAlarm?
    suspend fun insertAlarm(alarm: MiyaAlarm): Int
    suspend fun updateAlarm(alarm: MiyaAlarm)
    suspend fun deleteAlarm(alarm: MiyaAlarm)
}

interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<StreamSchedule>>
    suspend fun refreshSchedules()
    suspend fun insertSchedule(schedule: StreamSchedule)
    suspend fun updateSchedule(schedule: StreamSchedule)
    suspend fun deleteSchedule(schedule: StreamSchedule)
}

// =================================================================
// 아티스트 & 보이스 Repository 인터페이스
// 인터페이스를 유지하면 추후 서버 구현체로 교체 시 ViewModel 변경 불필요
// =================================================================

/**
 * 아티스트 팔로우 상태 관리.
 * 현재 구현: Room 로컬 DB (Mock)
 * 추후 교체: Firebase Auth + Firestore 기반 구현체
 */
interface ArtistRepository {
    fun getAllArtists(): Flow<List<Artist>>
    fun getAllArtistsWithFollowState(): Flow<List<Artist>>
    fun getFollowedArtists(): Flow<List<Artist>>
    suspend fun upsertArtist(artist: Artist)
    suspend fun setFollowed(artistId: String, isFollowed: Boolean)
}

/**
 * 보이스 에셋 구매 상태 및 암호화 파일 관리.
 * 현재 구현: Room 로컬 DB (Mock) + 암호화 파일 저장
 * 추후 교체: Firebase Auth + Firestore + 서버 다운로드 구현체
 */
interface VoiceRepository {
    fun getVoicesByArtist(artistId: String): Flow<List<VoiceAsset>>
    fun getPurchasedVoicesByArtist(artistId: String): Flow<List<VoiceAsset>>
    suspend fun upsertVoice(voice: VoiceAsset)
    suspend fun setPurchased(voiceId: String, isPurchased: Boolean)

    /**
     * 보이스 파일을 서버에서 다운로드하여 암호화 저장.
     * 이미 다운로드된 경우 스킵.
     */
    suspend fun downloadAndStoreVoice(voice: VoiceAsset)

    /**
     * 보이스의 오디오 데이터를 메모리에서 복호화하여 반환.
     * 디스크에 평문 파일을 생성하지 않음.
     * @return 복호화된 오디오 ByteArray, 실패 시 null
     */
    suspend fun getVoiceBytes(voiceId: String): ByteArray?
}
