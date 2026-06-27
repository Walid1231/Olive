package com.waleve.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.waleve.player.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class AlbumInfo(
    val album: String,
    val artist: String,
    val albumArtUri: String?,
    val songCount: Int,
    val year: Int?,
)

data class ArtistInfo(
    val artist: String,
    val songCount: Int,
    val albumCount: Int,
)

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :albumName AND artist = :artistName ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(albumName: String, artistName: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artistName ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artistName: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE folder = :folder ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByFolder(folder: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%' 
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("""
        SELECT album, artist, 
               MIN(albumArtUri) as albumArtUri, 
               COUNT(*) as songCount, 
               MIN(year) as year 
        FROM songs 
        GROUP BY album, artist 
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getAlbums(): Flow<List<AlbumInfo>>

    @Query("""
        SELECT artist, 
               COUNT(*) as songCount, 
               COUNT(DISTINCT album) as albumCount 
        FROM songs 
        GROUP BY artist 
        ORDER BY artist COLLATE NOCASE ASC
    """)
    fun getArtists(): Flow<List<ArtistInfo>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre COLLATE NOCASE ASC")
    fun getGenres(): Flow<List<String>>

    @Query("SELECT DISTINCT folder FROM songs WHERE folder IS NOT NULL AND folder != '' ORDER BY folder COLLATE NOCASE ASC")
    fun getFolders(): Flow<List<String>>

    @Query("UPDATE songs SET isFavorite = NOT isFavorite WHERE id = :songId")
    suspend fun toggleFavorite(songId: Long)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :songId")
    suspend fun updatePlayCount(songId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE path = :path LIMIT 1")
    suspend fun getSongByPath(path: String): SongEntity?

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSong(songId: Long)

    /** Tag a song with its YouTube videoId after download+scan */
    @Query("UPDATE songs SET videoId = :videoId WHERE id = :songId")
    suspend fun updateVideoId(songId: Long, videoId: String)

    /** Update the album art URI for a song (e.g. with a YouTube thumbnail URL) */
    @Query("UPDATE songs SET albumArtUri = :albumArtUri WHERE id = :songId")
    suspend fun updateAlbumArtUri(songId: Long, albumArtUri: String)

    /** Find a recently-added song by title for post-download tagging */
    @Query("SELECT * FROM songs WHERE title LIKE :titlePattern ORDER BY dateAdded DESC LIMIT 5")
    suspend fun findRecentByTitle(titlePattern: String): List<SongEntity>
}
