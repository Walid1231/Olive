package com.waleve.player.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.data.local.entity.SongEntity
import com.waleve.player.data.mapper.toDomain
import com.waleve.player.data.mapper.toEntity
import com.waleve.player.domain.model.Album
import com.waleve.player.domain.model.Artist
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.LocalMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val contentResolver: ContentResolver,
) : LocalMusicRepository {

    override suspend fun scanMusic() = withContext(Dispatchers.IO) {
        val songs = queryMediaStore()
        songDao.insertSongs(songs)
    }

    override fun getAllSongs(): Flow<List<Song>> =
        songDao.getAllSongs().map { list -> list.map { it.toDomain() } }

    override fun getSongsByAlbum(albumName: String, artistName: String): Flow<List<Song>> =
        songDao.getSongsByAlbum(albumName, artistName).map { list -> list.map { it.toDomain() } }

    override fun getSongsByArtist(artistName: String): Flow<List<Song>> =
        songDao.getSongsByArtist(artistName).map { list -> list.map { it.toDomain() } }

    override fun getSongsByGenre(genre: String): Flow<List<Song>> =
        songDao.getSongsByGenre(genre).map { list -> list.map { it.toDomain() } }

    override fun getSongsByFolder(folder: String): Flow<List<Song>> =
        songDao.getSongsByFolder(folder).map { list -> list.map { it.toDomain() } }

    override fun getFavorites(): Flow<List<Song>> =
        songDao.getFavorites().map { list -> list.map { it.toDomain() } }

    override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> =
        songDao.getRecentlyPlayed(limit).map { list -> list.map { it.toDomain() } }

    override fun getMostPlayed(limit: Int): Flow<List<Song>> =
        songDao.getMostPlayed(limit).map { list -> list.map { it.toDomain() } }

    override fun searchSongs(query: String): Flow<List<Song>> =
        songDao.searchSongs(query).map { list -> list.map { it.toDomain() } }

    override fun getAlbums(): Flow<List<Album>> =
        songDao.getAlbums().map { list -> list.map { it.toDomain() } }

    override fun getArtists(): Flow<List<Artist>> =
        songDao.getArtists().map { list -> list.map { it.toDomain() } }

    override fun getGenres(): Flow<List<String>> = songDao.getGenres()

    override fun getFolders(): Flow<List<String>> = songDao.getFolders()

    override suspend fun toggleFavorite(songId: Long) = songDao.toggleFavorite(songId)

    override suspend fun updatePlayCount(songId: Long) = songDao.updatePlayCount(songId)

    override suspend fun insertSong(song: Song): Long = songDao.insertSong(song.toEntity())

    override suspend fun deleteSong(songId: Long) = songDao.deleteSong(songId)

    @Suppress("DEPRECATION")
    private fun queryMediaStore(): List<SongEntity> {
        val songs = mutableListOf<SongEntity>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.SIZE,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DURATION} > 0"

        contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val albumId = cursor.getLong(albumIdCol)
                val path = cursor.getString(dataCol) ?: continue
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                val folder = try {
                    File(path).parentFile?.name
                } catch (_: Exception) {
                    null
                }

                songs.add(
                    SongEntity(
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        albumArtUri = albumArtUri,
                        duration = cursor.getLong(durationCol),
                        path = path,
                        dateAdded = cursor.getLong(dateAddedCol),
                        genre = null,
                        folder = folder,
                        size = cursor.getLong(sizeCol),
                        trackNumber = cursor.getInt(trackCol).takeIf { it > 0 },
                        year = cursor.getInt(yearCol).takeIf { it > 0 },
                        source = "local",
                        license = null,
                    )
                )
            }
        }

        return songs
    }
}
