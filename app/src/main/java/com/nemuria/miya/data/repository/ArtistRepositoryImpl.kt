package com.nemuria.miya.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ArtistRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ArtistRepository {

    override fun getAllArtists(): Flow<List<Artist>> {
        return firestore.collection("streamers")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { doc ->
                    Artist(
                        id = doc.id,
                        name = doc.getString("name") ?: "알 수 없는 아티스트",
                        imageUrl = doc.getString("mainImage"),
                        isFollowed = true // 로그인 구현 전까지는 모두 팔로우 상태로 간주
                    )
                }
            }
    }

    override fun getFollowedArtists(): Flow<List<Artist>> = getAllArtists()

    override suspend fun upsertArtist(artist: Artist) {
        // Firestore에 직접 쓰는 로직 (필요 시 구현)
    }

    override suspend fun setFollowed(artistId: String, isFollowed: Boolean) {
        // 팔로우 상태 업데이트 로직 (로그인 구현 후 Firestore 또는 로컬 DB 연동)
    }
}
