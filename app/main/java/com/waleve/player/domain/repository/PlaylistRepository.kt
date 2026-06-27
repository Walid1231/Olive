package com.waleve.player.domain.repository

import com.waleve.player.domain.model.Playlist
import com.waleve.player.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    fun getPlaylistById(playlistId: Long): Flow<Playlist?>
    suspend fun createPlaylist(name: String, description: String? = null): Long
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
    suspend fun reorderSongs(playlistId: Long, songIds: List<Long>)
}
