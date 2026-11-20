package com.waleve.player.data.social

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.waleve.player.domain.model.Playlist
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.LocalMusicRepository
import com.waleve.player.domain.repository.PlaylistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies "Liked songs" and your playlists to the cloud (users/{uid}/library), so Olive for
 * Chrome on your PC shows the same library. Only you can read it (Firestore rules).
 * One way: phone → cloud. Writes only what changed.
 */
@Singleton
class LibrarySync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val music: LocalMusicRepository,
    private val playlists: PlaylistRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("library_sync", Context.MODE_PRIVATE)
    private var started = false

    private fun uidFlow(): Flow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun playlistsWithSongs(): Flow<List<Playlist>> =
        playlists.getAllPlaylists().flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList())
            else combine(list.map { p -> playlists.getPlaylistById(p.id) }) { arr -> arr.filterNotNull() }
        }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            uidFlow().flatMapLatest { uid ->
                if (uid == null) flowOf(null)
                else combine(music.getFavorites(), playlistsWithSongs()) { fav, pls -> Triple(uid, fav, pls) }
            }.debounce(4_000).collect { data ->
                if (data == null) return@collect
                runCatching { push(data.first, data.second, data.third) }
                    .onFailure { Log.w("LibrarySync", "Sync failed", it) }
            }
        }
    }

    private fun track(s: Song): Map<String, Any?> = buildMap {
        put("t", s.title.take(200))
        put("a", s.artist.take(200))
        s.albumArtUri?.takeIf { it.startsWith("https://") }?.let { put("art", it.take(1000)) }
        s.videoId?.let { put("v", it) }
        if (s.duration > 0) put("d", s.duration)
        if (s.isAudiobook) put("b", true)
    }

    private suspend fun push(uid: String, favorites: List<Song>, lists: List<Playlist>) {
        val col = firestore.collection("users").document(uid).collection("library")
        val docs = mutableMapOf<String, Map<String, Any?>>()
        docs["favorites"] = mapOf("name" to "Liked songs", "kind" to "favorites", "order" to 0,
            "tracks" to favorites.take(1500).map(::track))
        lists.forEachIndexed { i, p ->
            docs["pl_${p.id}"] = mapOf("name" to p.name.take(100), "kind" to "playlist", "order" to i + 1,
                "tracks" to p.songs.take(1500).map(::track))
        }
        val keyPrefix = "h_${uid}_"
        val known = prefs.all.keys.filter { it.startsWith(keyPrefix) }.map { it.removePrefix(keyPrefix) }.toSet()
        val edit = prefs.edit()
        var writes = 0
        for ((id, body) in docs) {
            val hash = body.hashCode().toString()
            if (prefs.getString(keyPrefix + id, null) == hash) continue
            col.document(id).set(body + ("updatedAt" to System.currentTimeMillis())).await()
            edit.putString(keyPrefix + id, hash); writes++
        }
        // Playlists deleted on the phone disappear from the PC too
        for (gone in known - docs.keys) {
            runCatching { col.document(gone).delete().await() }
            edit.remove(keyPrefix + gone); writes++
        }
        edit.apply()
        if (writes > 0) Log.d("LibrarySync", "Synced $writes item(s) for the PC")
    }
}
