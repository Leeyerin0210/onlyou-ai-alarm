package com.nemuria.miya.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.nemuria.miya.data.local.VoiceAssetDao
import com.nemuria.miya.data.local.VoiceAssetEntity
import com.nemuria.miya.di.ApplicationScope
import com.nemuria.miya.domain.model.VoiceAsset
import com.nemuria.miya.domain.repository.VoiceRepository
import com.nemuria.miya.util.VoiceEncryptionUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject

class VoiceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceAssetDao: VoiceAssetDao,
    private val encryptionUtil: VoiceEncryptionUtil,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    @ApplicationScope private val appScope: CoroutineScope,
) : VoiceRepository {

    /** 암호화된 보이스 파일이 저장되는 내부 디렉토리 */
    private val voiceDir: File
        get() = File(context.filesDir, "voices").also { it.mkdirs() }

    /**
     * 앱 레벨 캐싱: voices 컬렉션 전체 (구매 여부 제외).
     * 구독자가 없으면 5초 후 Firestore 연결 자동 해제.
     */
    private val allVoicesShared = firestore.collection("voices")
        .snapshots()
        .map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                VoiceAsset(
                    id = doc.id,
                    artistId = doc.getString("artistId") ?: "",
                    name = doc.getString("name") ?: "",
                    audioUrl = doc.getString("audioUrl") ?: "",
                    isPurchased = false,
                    isDownloaded = File(voiceDir, "${doc.id}.enc").exists()
                )
            }
        }.shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * 앱 레벨 캐싱: 현재 유저의 purchasedVoiceIds.
     * 구독자가 없으면 5초 후 Firestore 연결 자동 해제.
     */
    private val purchasedIdsShared: Flow<List<String>> by lazy {
        val uid = firebaseAuth.currentUser?.uid
            ?: return@lazy flowOf(emptyList())

        firestore.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                @Suppress("UNCHECKED_CAST")
                snapshot.get("purchasedVoiceIds") as? List<String> ?: emptyList()
            }.shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    override fun getVoicesByArtist(artistId: String): Flow<List<VoiceAsset>> =
        allVoicesShared.combine(purchasedIdsShared) { voices, purchasedIds ->
            voices
                .filter { it.artistId == artistId }
                .map { it.copy(
                    isPurchased = purchasedIds.contains(it.id),
                    isDownloaded = File(voiceDir, "${it.id}.enc").exists()
                ) }
        }

    override fun getAllPurchasedVoices(): Flow<List<VoiceAsset>> =
        allVoicesShared.combine(purchasedIdsShared) { voices, purchasedIds ->
            voices
                .filter { purchasedIds.contains(it.id) }
                .map { it.copy(
                    isPurchased = true,
                    isDownloaded = File(voiceDir, "${it.id}.enc").exists()
                ) }
        }

    override fun getPurchasedVoicesByArtist(artistId: String): Flow<List<VoiceAsset>> =
        getVoicesByArtist(artistId).map { voices -> voices.filter { it.isPurchased } }

    override suspend fun upsertVoice(voice: VoiceAsset) {
        voiceAssetDao.upsertVoice(voice.toEntity())
    }

    override suspend fun setPurchased(voiceId: String, isPurchased: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userRef = firestore.collection("users").document(uid)

        try {
            if (isPurchased) {
                userRef.set(
                    hashMapOf("purchasedVoiceIds" to FieldValue.arrayUnion(voiceId)),
                    SetOptions.merge()
                ).await()
            } else {
                userRef.set(
                    hashMapOf("purchasedVoiceIds" to FieldValue.arrayRemove(voiceId)),
                    SetOptions.merge()
                ).await()
            }
        } catch (e: Exception) {
            // handle error if needed
        }
    }

    /**
     * 서버에서 보이스 파일을 다운로드하여 AES-256/GCM으로 암호화 저장.
     * 이미 암호화 파일이 존재하면 스킵.
     */
    override suspend fun downloadAndStoreVoice(voice: VoiceAsset) = withContext(Dispatchers.IO) {
        val encryptedFile = File(voiceDir, "${voice.id}.enc")
        if (encryptedFile.exists()) return@withContext

        URL(voice.audioUrl).openStream().use { inputStream ->
            encryptionUtil.encrypt(inputStream, encryptedFile)
        }
    }

    /**
     * 암호화 파일을 메모리에서 ByteArray로 복호화하여 반환.
     * 디스크에 평문 파일을 절대 생성하지 않음.
     */
    override suspend fun getVoiceBytes(voiceId: String): ByteArray? = withContext(Dispatchers.IO) {
        val encryptedFile = File(voiceDir, "$voiceId.enc")
        if (!encryptedFile.exists()) return@withContext null
        encryptionUtil.decryptToByteArray(encryptedFile)
    }

    private fun VoiceAsset.toEntity() = VoiceAssetEntity(
        id = id,
        artistId = artistId,
        name = name,
        audioUrl = audioUrl,
        isPurchased = isPurchased,
    )
}
