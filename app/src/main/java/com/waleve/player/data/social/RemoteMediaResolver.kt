package com.waleve.player.data.social

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.waleve.player.data.remote.SupabaseStorage
import com.waleve.player.domain.model.RemoteTrack
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.StreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a library [Song] into a [RemoteTrack] friends can play, and back.
 * Local files are uploaded once (Supabase) and the URL is remembered, so the same song
 * is never uploaded twice (Listen Together, group playlists, sharing).
 */
@Singleton
class RemoteMediaResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: SupabaseStorage,
    private val streams: StreamRepository,
    private val auth: FirebaseAuth,
) {
    private val cache by lazy { context.getSharedPreferences("remote_uploads", Context.MODE_PRIVATE) }

    private fun ytThumb(id: String) = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    fun videoIdOf(song: Song): String? = song.videoId ?: run {
        val p = song.path
        when {
            p.startsWith("ytm://stream/") -> p.substringAfter("ytm://stream/")
            p.contains("watch?v=") -> p.substringAfter("watch?v=").substringBefore("&")
            p.contains("youtu.be/") -> p.substringAfter("youtu.be/").substringBefore("?")
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun cacheKey(path: String): String {
        val f = File(path)
        return "$path|${f.length()}|${f.lastModified()}"
    }

    /** No network: what we already know (YouTube id, http URL, or a previous upload). Null fields = local only. */
    fun quick(song: Song): RemoteTrack {
        val vid = videoIdOf(song)
        val http = song.path.takeIf { vid == null && it.startsWith("http") && !it.contains("googlevideo.com") }
        val uploaded = if (vid == null && http == null) cache.getString(cacheKey(song.path), null) else null
        val art = song.albumArtUri?.takeIf { it.startsWith("http") } ?: vid?.let(::ytThumb)
        return RemoteTrack(
            key = vid?.let { "yt:$it" } ?: (http ?: uploaded)?.let { "url:${it.hashCode()}" } ?: "local:${song.title}|${song.artist}",
            title = song.title, artist = song.artist, artUrl = art,
            videoId = vid, fileUrl = http ?: uploaded,
            mediaType = song.mediaType, durationMs = song.duration,
        )
    }

    /** Make the song playable for friends, uploading a local file if needed. */
    suspend fun ensure(song: Song, onProgress: (Float) -> Unit = {}): Result<RemoteTrack> {
        val q = quick(song)
        if (q.playable) return Result.success(q)
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Please sign in first"))
        val mime = SupabaseStorage.mimeForPath(song.path)
        return storage.uploadLocalFile(song.path, uid, mime, onProgress).map { up ->
            cache.edit().putString(cacheKey(song.path), up.publicUrl).apply()
            q.copy(key = "url:${up.publicUrl.hashCode()}", fileUrl = up.publicUrl)
        }
    }

    /** A queue entry for PlaybackController (YouTube ids are resolved by the controller). */
    fun toQueueSong(t: RemoteTrack): Song? {
        val path = t.fileUrl ?: t.videoId?.let { "ytm://stream/$it" } ?: return null
        return Song(
            id = t.key.hashCode().toLong(), title = t.title, artist = t.artist,
            album = if (t.isAudiobook) t.title else "", albumArtUri = t.artUrl,
            duration = t.durationMs, path = path, source = "stream", videoId = t.videoId, mediaType = t.mediaType,
        )
    }

    /** Same as [toQueueSong] but with a ready-to-play URL (used when exact timing matters). */
    suspend fun toSong(t: RemoteTrack): Song? {
        val base = toQueueSong(t) ?: return null
        val vid = t.videoId ?: return base
        val url = runCatching { streams.getStreamUrl(vid) }.getOrNull() ?: return null
        return base.copy(path = url)
    }
}
