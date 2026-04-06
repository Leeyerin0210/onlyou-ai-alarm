package com.nemuria.miya.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.nemuria.miya.data.local.VoiceAssetDao
import com.nemuria.miya.data.local.VoiceAssetEntity
import com.nemuria.miya.domain.model.VoiceAsset
import com.nemuria.miya.domain.repository.VoiceRepository
import com.nemuria.miya.util.VoiceEncryptionUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject

class VoiceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceAssetDao: VoiceAssetDao,
    private val encryptionUtil: VoiceEncryptionUtil,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : VoiceRepository {

    /** 암호화된 보이스 파일이 저장되는 내부 디렉토리 */
    private val voiceDir: File
        get() = File(context.filesDir, "voices").also { it.mkdirs() }

    override fun getVoicesByArtist(artistId: String): Flow<List<VoiceAsset>> {
        val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

        val userPurchasedFlow = callbackFlow {
            val listener = firestore.collection("users").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    @Suppress("UNCHECKED_CAST")
                    val ids = snapshot?.get("purchasedVoiceIds") as? List<String> ?: emptyList()
                    trySend(ids)
                }
            awaitClose { listener.remove() }
        }

        val voicesFlow = callbackFlow {
            val listener = firestore.collection("voices")
                .whereEqualTo("artistId", artistId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        VoiceAsset(
                            id = doc.id,
                            artistId = artistId,
                            name = doc.getString("name") ?: "",
                            audioUrl = doc.getString("audioUrl") ?: "",
                            isPurchased = false, // Will be combined below
                            isDownloaded = File(voiceDir, "${doc.id}.enc").exists()
                        )
                    } ?: emptyList()
                    trySend(list)
                }
            awaitClose { listener.remove() }
        }

        return voicesFlow.combine(userPurchasedFlow) { voices, purchasedIds ->
            voices.map { it.copy(isPurchased = purchasedIds.contains(it.id)) }
        }
    }

    override fun getAllPurchasedVoices(): Flow<List<VoiceAsset>> {
        val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

        val userPurchasedFlow = callbackFlow {
            val listener = firestore.collection("users").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    @Suppress("UNCHECKED_CAST")
                    val ids = snapshot?.get("purchasedVoiceIds") as? List<String> ?: emptyList()
                    trySend(ids)
                }
            awaitClose { listener.remove() }
        }

        val allVoicesFlow = callbackFlow {
            val listener = firestore.collection("voices")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        VoiceAsset(
                            id = doc.id,
                            artistId = doc.getString("artistId") ?: "",
                            name = doc.getString("name") ?: "",
                            audioUrl = doc.getString("audioUrl") ?: "",
                            isPurchased = false,
                            isDownloaded = File(voiceDir, "${doc.id}.enc").exists()
                        )
                    } ?: emptyList()
                    trySend(list)
                }
            awaitClose { listener.remove() }
        }

        return allVoicesFlow.combine(userPurchasedFlow) { voices, purchasedIds ->
            voices.map { it.copy(isPurchased = purchasedIds.contains(it.id)) }
                .filter { it.isPurchased }
        }
    }

    override fun getPurchasedVoicesByArtist(artistId: String): Flow<List<VoiceAsset>> =
        getVoicesByArtist(artistId).map { voices ->
            voices.filter { it.isPurchased }
        }

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

    private fun VoiceAssetEntity.toDomainModel(): VoiceAsset {
        val isDownloaded = File(voiceDir, "$id.enc").exists()
        return VoiceAsset(
            id = id,
            artistId = artistId,
            name = name,
            audioUrl = audioUrl,
            isPurchased = isPurchased,
            isDownloaded = isDownloaded,
        )
    }

    private fun VoiceAsset.toEntity() = VoiceAssetEntity(
        id = id,
        artistId = artistId,
        name = name,
        audioUrl = audioUrl,
        isPurchased = isPurchased,
    )
}
