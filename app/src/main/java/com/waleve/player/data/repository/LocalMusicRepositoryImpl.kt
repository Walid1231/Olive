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
    private val libraryPrefs: com.waleve.player.data.cover.LibraryPrefs,
    private val coverArtFetcher: com.waleve.player.data.cover.CoverArtFetcher,
) : LocalMusicRepository {

    override suspend fun scanMusic() = withContext(Dispatchers.IO) {
        val hideJunk = libraryPrefs.hideJunk.value
        val scanned = queryMediaStore().filter { !hideJunk || !com.waleve.player.data.cover.LibraryPrefs.isJunk(it.entity.path, it.entity.duration) }
        songDao.insertSongs(scanned.map { it.entity })
        // Voice notes / call recordings / ringtones that were added before → drop them from the library
        if (hideJunk) {
            val junk = songDao.getLocalSongs().filter { com.waleve.player.data.cover.LibraryPrefs.isJunk(it.path, it.duration) }.map { it.id }
            if (junk.isNotEmpty()) junk.chunked(500).forEach { songDao.removeFromAllPlaylists(it); songDao.deleteSongs(it) }
        }
        // Forget local songs whose files were deleted outside the app
        val present = scanned.map { it.entity.path }.toHashSet()
        val gone = songDao.getLocalSongs().filter { it.path !in present && !File(it.path).exists() }.map { it.id }
        if (gone.isNotEmpty()) gone.chunked(500).forEach { songDao.removeFromAllPlaylists(it); songDao.deleteSongs(it) }
        // Insert uses IGNORE, so (re)apply the song/audiobook split to rows that already existed.
        scanned.groupBy { it.entity.mediaType }.forEach { (type, items) ->
            items.map { it.entity.path }.chunked(500).forEach { chunk ->
                songDao.setMediaTypeForPaths(chunk, type)
            }
        }
        // Missing covers → look them up online in the background
        coverArtFetcher.runInBackground()
    }

    override fun getAudiobooks(): Flow<List<Song>> =
        songDao.getAudiobooks().map { list -> list.map { it.toDomain() } }

    override fun getAudiobookAlbums(): Flow<List<Album>> =
        songDao.getAudiobookAlbums().map { list -> list.map { it.toDomain() } }

    override suspend fun setMediaType(songId: Long, audiobook: Boolean) =
        songDao.setMediaTypeManual(
            songId,
            if (audiobook) SongEntity.MEDIA_AUDIOBOOK else SongEntity.MEDIA_MUSIC,
        )

    private class Scanned(val entity: SongEntity)

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

    override suspend fun deleteSong(songId: Long) = removeFromLibrary(listOf(songId))

    override suspend fun removeFromLibrary(songIds: List<Long>) {
        if (songIds.isEmpty()) return
        songIds.chunked(500).forEach { songDao.removeFromAllPlaylists(it); songDao.hideSongs(it) }
    }

    override suspend fun forgetSongs(songIds: List<Long>) {
        if (songIds.isEmpty()) return
        songIds.chunked(500).forEach { songDao.removeFromAllPlaylists(it); songDao.deleteSongs(it) }
    }

    override fun hiddenCount(): Flow<Int> = songDao.hiddenCount()

    override suspend fun restoreHidden() = songDao.unhideAll()

    @Suppress("DEPRECATION")
    private fun queryMediaStore(): List<Scanned> {
        val songs = mutableListOf<Scanned>()

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
            MediaStore.Audio.Media.IS_PODCAST,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(MediaStore.Audio.Media.IS_AUDIOBOOK) else emptyArray()

        // Before: only IS_MUSIC = 1, which silently hid every audiobook. Now music AND spoken audio are scanned.
        val spoken = buildString {
            append("${MediaStore.Audio.Media.IS_PODCAST} = 1")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) append(" OR ${MediaStore.Audio.Media.IS_AUDIOBOOK} = 1")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4b'")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE '%audiobook%'")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE '%audio book%'")
        }
        val selection = "${MediaStore.Audio.Media.DURATION} > 0 AND (${MediaStore.Audio.Media.IS_MUSIC} = 1 OR $spoken)"

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
            val podcastCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_PODCAST)
            val audiobookCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Audio.Media.IS_AUDIOBOOK) else -1

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

                val duration = cursor.getLong(durationCol)
                val flaggedSpoken = (audiobookCol >= 0 && cursor.getInt(audiobookCol) == 1) ||
                    (podcastCol >= 0 && cursor.getInt(podcastCol) == 1)
                val title = cursor.getString(titleCol) ?: "Unknown"
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val mediaType = if (looksLikeAudiobook(path, duration, flaggedSpoken, "$title $artist $album"))
                    SongEntity.MEDIA_AUDIOBOOK else SongEntity.MEDIA_MUSIC

                songs.add(Scanned(
                    SongEntity(
                        title = title,
                        artist = artist,
                        album = album,
                        albumArtUri = albumArtUri,
                        duration = duration,
                        path = path,
                        dateAdded = cursor.getLong(dateAddedCol),
                        genre = null,
                        folder = folder,
                        size = cursor.getLong(sizeCol),
                        trackNumber = cursor.getInt(trackCol).takeIf { it > 0 },
                        year = cursor.getInt(yearCol).takeIf { it > 0 },
                        source = "local",
                        license = null,
                        mediaType = mediaType,
                    )
                ))
            }
        }

        return songs
    }

    /**
     * Decides whether a file is an audiobook / story (spoken word) or a song.
     *  1. Android's own audiobook/podcast flag, .m4b files, "Audiobooks"/"Podcasts" folders
     *  2. Anything 15 min or longer (songs are almost never that long)
     *  3. Story-ish words in the title/artist/album (golpo, গল্প, suspense, story, episode…) on files ≥ 6 min
     */
    private fun looksLikeAudiobook(path: String, durationMs: Long, flaggedSpoken: Boolean, tags: String): Boolean {
        if (flaggedSpoken) return true
        val p = path.lowercase()
        if (p.endsWith(".m4b")) return true
        if ("/audiobooks/" in p || "/audiobook/" in p || "audio book" in p || "/podcasts/" in p) return true
        val minutes = durationMs / 60_000.0
        if (minutes >= 15) return true
        if (minutes >= 6) {
            val t = tags.lowercase()
            if (SPOKEN_WORDS.any { it in t }) return true
        }
        return false
    }

    companion object {
        private val SPOKEN_WORDS = listOf(
            "audiobook", "audio book", "audio story", "audio drama", "audio golpo",
            "golpo", "galpo", "goppo", "গল্প", "গপ্প", "কাহিনী", "kahini", "উপন্যাস", "upanyas",
            "suspense", "সাসপেন্স", "thriller", "horror", "bhuter", "ভূতের", "detective", "goyenda", "গোয়েন্দা",
            "story", "stories", "episode", "chapter", "অধ্যায়", "part ", "পর্ব", "porbo",
            "podcast", "radio", "natok", "নাটক", "shruti natok", "শ্রুতি", "kobita path", "আবৃত্তি", "abritti",
        )
    }
}
