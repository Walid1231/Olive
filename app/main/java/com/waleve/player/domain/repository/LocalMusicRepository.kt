package com.waleve.player.domain.repository

import com.waleve.player.domain.model.Album
import com.waleve.player.domain.model.Artist
import com.waleve.player.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface LocalMusicRepository {
    suspend fun scanMusic()
    fun getAllSongs(): Flow<List<Song>>
    fun getSongsByAlbum(albumName: String, artistName: String): Flow<List<Song>>
    fun getSongsByArtist(artistName: String): Flow<List<Song>>
    fun getSongsByGenre(genre: String): Flow<List<Song>>
    fun getSongsByFolder(folder: String): Flow<List<Song>>
    fun getFavorites(): Flow<List<Song>>
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<Song>>
    fun getMostPlayed(limit: Int = 50): Flow<List<Song>>
    fun searchSongs(query: String): Flow<List<Song>>
    fun getAlbums(): Flow<List<Album>>
    fun getArtists(): Flow<List<Artist>>
    fun getGenres(): Flow<List<String>>
    fun getFolders(): Flow<List<String>>
    suspend fun toggleFavorite(songId: Long)
    suspend fun updatePlayCount(songId: Long)
    suspend fun insertSong(song: Song): Long
    suspend fun deleteSong(songId: Long)
}
