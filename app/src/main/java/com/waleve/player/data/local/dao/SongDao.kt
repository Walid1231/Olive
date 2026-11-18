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

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND mediaType = 'music' ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND album = :albumName AND artist = :artistName ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(albumName: String, artistName: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND artist = :artistName ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artistName: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND genre = :genre ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND folder = :folder ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByFolder(folder: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs 
        WHERE isHidden = 0 AND (title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%') 
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("""
        SELECT album, artist, 
               MIN(albumArtUri) as albumArtUri, 
               COUNT(*) as songCount, 
               MIN(year) as year 
        FROM songs 
        WHERE isHidden = 0 AND mediaType = 'music'
        GROUP BY album, artist 
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getAlbums(): Flow<List<AlbumInfo>>

    /** Audiobooks grouped by book (album tag) — each book is its own "album". */
    @Query("""
        SELECT album, artist,
               MIN(albumArtUri) as albumArtUri,
               COUNT(*) as songCount,
               MIN(year) as year
        FROM songs
        WHERE isHidden = 0 AND mediaType = 'audiobook'
        GROUP BY album, artist
        ORDER BY MAX(COALESCE(lastPlayed, 0)) DESC, album COLLATE NOCASE ASC
    """)
    fun getAudiobookAlbums(): Flow<List<AlbumInfo>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND mediaType = 'audiobook' ORDER BY album COLLATE NOCASE ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    fun getAudiobooks(): Flow<List<SongEntity>>

    /** Auto-classification after a scan. Never touches items the user moved by hand. */
    @Query("UPDATE songs SET mediaType = :mediaType WHERE path IN (:paths) AND mediaTypeManual = 0")
    suspend fun setMediaTypeForPaths(paths: List<String>, mediaType: String)

    /** Manual "Move to Audiobooks / Songs" from the song menu. */
    @Query("UPDATE songs SET mediaType = :mediaType, mediaTypeManual = 1 WHERE id = :songId")
    suspend fun setMediaTypeManual(songId: Long, mediaType: String)

    @Query("""
        SELECT artist, 
               COUNT(*) as songCount, 
               COUNT(DISTINCT album) as albumCount 
        FROM songs 
        WHERE isHidden = 0 AND mediaType = 'music'
        GROUP BY artist 
        ORDER BY artist COLLATE NOCASE ASC
    """)
    fun getArtists(): Flow<List<ArtistInfo>>

    @Query("SELECT DISTINCT genre FROM songs WHERE isHidden = 0 AND mediaType = 'music' AND genre IS NOT NULL AND genre != '' ORDER BY genre COLLATE NOCASE ASC")
    fun getGenres(): Flow<List<String>>

    @Query("SELECT DISTINCT folder FROM songs WHERE isHidden = 0 AND folder IS NOT NULL AND folder != '' ORDER BY folder COLLATE NOCASE ASC")
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

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongs(ids: List<Long>)

    @Query("DELETE FROM playlist_song_cross_ref WHERE songId IN (:ids)")
    suspend fun removeFromAllPlaylists(ids: List<Long>)

    /** "Remove from library": hidden rows stay hidden even after a rescan. */
    @Query("UPDATE songs SET isHidden = 1, isFavorite = 0 WHERE id IN (:ids)")
    suspend fun hideSongs(ids: List<Long>)

    @Query("UPDATE songs SET isHidden = 0")
    suspend fun unhideAll()

    @Query("SELECT COUNT(*) FROM songs WHERE isHidden = 1")
    fun hiddenCount(): Flow<Int>

    @Query("SELECT * FROM songs WHERE source = 'local' OR source IS NULL")
    suspend fun getLocalSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

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
