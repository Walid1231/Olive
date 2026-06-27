package com.waleve.player.data.repository

import com.waleve.player.data.local.dao.PlaylistDao
import com.waleve.player.data.local.entity.PlaylistEntity
import com.waleve.player.data.local.entity.PlaylistSongCrossRef
import com.waleve.player.data.mapper.toDomain
import com.waleve.player.domain.model.Playlist
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylists().map { list ->
            list.map { it.toDomain() }
        }

    override fun getPlaylistById(playlistId: Long): Flow<Playlist?> =
        playlistDao.getPlaylistWithSongs(playlistId).map { it?.toDomain() }

    override suspend fun createPlaylist(name: String, description: String?): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name, description = description)
        )
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylistSongs(playlistId)
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description,
                createdAt = playlist.createdAt,
                updatedAt = System.currentTimeMillis(),
                artworkUri = playlist.artworkUri,
            )
        )
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val position = playlistDao.getNextPosition(playlistId)
        playlistDao.insertPlaylistSongCrossRef(
            PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = position)
        )
        playlistDao.updateTimestamp(playlistId)
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removePlaylistSong(playlistId, songId)
        playlistDao.updateTimestamp(playlistId)
    }

    override suspend fun reorderSongs(playlistId: Long, songIds: List<Long>) {
        playlistDao.reorderSongs(playlistId, songIds)
    }
}
