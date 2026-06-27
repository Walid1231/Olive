package com.waleve.player.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.waleve.player.data.local.dao.PlaylistDao
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.data.local.entity.PlaylistEntity
import com.waleve.player.data.local.entity.PlaylistSongCrossRef
import com.waleve.player.data.local.entity.SongEntity
import com.waleve.player.domain.model.SharedPlaylist
import com.waleve.player.domain.model.SharedTrack
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.SharedPlaylistRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPlaylistRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
) : SharedPlaylistRepository {

    private val currentUid    get() = auth.currentUser?.uid
    private val sharedCol     get() = firestore.collection("sharedPlaylists")
    private val usersCol      get() = firestore.collection("users")
    private fun inboxCol(uid: String) =
        firestore.collection("users").document(uid).collection("inbox")

    // ─── Share a playlist ─────────────────────────────────────────────────────
    override suspend fun sharePlaylist(
        playlistName: String,
        playlistDescription: String?,
        songs: List<Song>,
        friendUids: List<String>,
    ): Result<String> {
        return try {
            val uid = currentUid ?: return Result.failure(Exception("Not signed in"))
            val meDoc = usersCol.document(uid).get().await()
            val myName = meDoc.getString("displayName") ?: "Unknown"

            // Build track list
            // A song is downloadable if it has a videoId (stream OR downloaded from YT)
            val tracks = songs.map { song ->
                // Streamed songs: path is an HTTP URL, videoId was set in getStreamSong()
                // Downloaded songs: path is local file, but videoId was stored at download time
                // Imported stream songs: path is ytm://stream/<videoId>
                val videoId = song.videoId
                    ?: if (song.source == "stream" && song.path.startsWith("http"))
                        extractVideoIdFromUrl(song.path)
                    else if (song.path.startsWith("ytm://stream/"))
                        song.path.substringAfter("ytm://stream/").takeIf { it.isNotEmpty() }
                    else null
                mapOf(
                    "videoId"      to videoId,
                    "title"        to song.title,
                    "artist"       to song.artist,
                    "thumbnailUrl" to (song.albumArtUri ?: ""),
                    "durationMs"   to song.duration,
                    "isLocalOnly"  to (videoId == null),   // only truly local if no videoId
                )
            }

            // Publish shared playlist document
            val docRef = sharedCol.document()
            val shareId = docRef.id
            val data = mapOf(
                "shareId"     to shareId,
                "name"        to playlistName,
                "description" to (playlistDescription ?: ""),
                "ownerId"     to uid,
                "ownerName"   to myName,
                "createdAt"   to System.currentTimeMillis(),
                "tracks"      to tracks,
            )
            docRef.set(data).await()

            // Notify each friend's inbox
            friendUids.forEach { friendUid ->
                val notifRef = inboxCol(friendUid).document()
                val notif = mapOf(
                    "notifId"      to notifRef.id,
                    "type"         to "PLAYLIST_SHARED",
                    "fromUid"      to uid,
                    "fromName"     to myName,
                    "shareId"      to shareId,
                    "playlistName" to playlistName,
                    "sentAt"       to System.currentTimeMillis(),
                    "isRead"       to false,
                )
                notifRef.set(notif).await()
            }

            Result.success(shareId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Fetch a shared playlist ──────────────────────────────────────────────
    override suspend fun getSharedPlaylist(shareId: String): Result<SharedPlaylist> {
        return try {
            val doc = sharedCol.document(shareId).get().await()
            if (!doc.exists()) return Result.failure(Exception("Shared playlist not found"))

            @Suppress("UNCHECKED_CAST")
            val rawTracks = doc.get("tracks") as? List<Map<String, Any?>> ?: emptyList()
            val tracks = rawTracks.map { t ->
                SharedTrack(
                    videoId      = t["videoId"] as? String,
                    title        = t["title"] as? String ?: "Unknown",
                    artist       = t["artist"] as? String ?: "Unknown Artist",
                    thumbnailUrl = t["thumbnailUrl"] as? String,
                    durationMs   = (t["durationMs"] as? Long) ?: 0L,
                    isLocalOnly  = t["isLocalOnly"] as? Boolean ?: false,
                )
            }

            Result.success(
                SharedPlaylist(
                    shareId     = shareId,
                    name        = doc.getString("name") ?: "Shared Playlist",
                    description = doc.getString("description"),
                    ownerId     = doc.getString("ownerId") ?: "",
                    ownerName   = doc.getString("ownerName") ?: "Unknown",
                    createdAt   = doc.getLong("createdAt") ?: 0L,
                    tracks      = tracks,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Import a shared playlist into local Room DB ──────────────────────────
    override suspend fun importSharedPlaylist(shareId: String): Result<Long> {
        return try {
            val playlistResult = getSharedPlaylist(shareId)
            val shared = playlistResult.getOrElse { return Result.failure(it) }

            // Create local playlist
            val playlistEntity = PlaylistEntity(
                name        = shared.name,
                description = "Shared by ${shared.ownerName}",
                createdAt   = System.currentTimeMillis(),
                updatedAt   = System.currentTimeMillis(),
            )
            val playlistId = playlistDao.insertPlaylist(playlistEntity)

            // Insert remote tracks as Song entities (source = "stream")
            shared.tracks
                .filter { !it.isLocalOnly && it.videoId != null }
                .forEachIndexed { index, track ->
                    val pseudoPath = "ytm://stream/${track.videoId}"
                    // Only insert if not already in library (unique path constraint)
                    val existingId = songDao.getSongByPath(pseudoPath)?.id
                    val songId = existingId ?: run {
                        val entity = SongEntity(
                            title       = track.title,
                            artist      = track.artist,
                            album       = shared.name,
                            albumArtUri = track.thumbnailUrl,
                            duration    = track.durationMs,
                            path        = pseudoPath,
                            source      = "stream",
                        )
                        songDao.insertSong(entity)
                    }
                    playlistDao.insertPlaylistSongCrossRef(
                        PlaylistSongCrossRef(
                            playlistId = playlistId,
                            songId     = songId,
                            position   = index,
                        )
                    )
                }

            Result.success(playlistId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun extractVideoIdFromUrl(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.getQueryParameter("v")
                ?: url.substringAfter("watch?v=", "").substringBefore("&").takeIf { it.isNotEmpty() }
                ?: url.substringAfter("youtu.be/", "").substringBefore("?").takeIf { it.isNotEmpty() }
                ?: url.substringAfter("ytm://stream/", "").takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null }
    }
}
