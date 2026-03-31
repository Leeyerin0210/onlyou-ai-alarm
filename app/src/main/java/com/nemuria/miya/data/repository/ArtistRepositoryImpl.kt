package com.nemuria.miya.data.repository

import com.nemuria.miya.data.local.ArtistDao
import com.nemuria.miya.data.local.ArtistEntity
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ArtistRepositoryImpl @Inject constructor(
    private val artistDao: ArtistDao,
) : ArtistRepository {

    override fun getAllArtists(): Flow<List<Artist>> =
        artistDao.getAllArtists().map { entities -> entities.map { it.toDomainModel() } }

    override fun getFollowedArtists(): Flow<List<Artist>> =
        artistDao.getFollowedArtists().map { entities -> entities.map { it.toDomainModel() } }

    override suspend fun upsertArtist(artist: Artist) {
        artistDao.upsertArtist(artist.toEntity())
    }

    override suspend fun setFollowed(artistId: String, isFollowed: Boolean) {
        artistDao.setFollowed(artistId, isFollowed)
    }

    private fun ArtistEntity.toDomainModel() = Artist(
        id = id,
        name = name,
        imageUrl = imageUrl,
        isFollowed = isFollowed,
    )

    private fun Artist.toEntity() = ArtistEntity(
        id = id,
        name = name,
        imageUrl = imageUrl,
        isFollowed = isFollowed,
    )
}
