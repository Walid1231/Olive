package com.waleve.player.data.social

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.waleve.player.domain.model.CollabPlaylist
import com.waleve.player.domain.model.RemoteTrack
import com.waleve.player.domain.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Group playlists that several friends edit together.
 * Firestore: collabPlaylists/{id} { name, ownerId, memberIds[], memberNames{}, tracks[] }.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CollabPlaylistRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val resolver: RemoteMediaResolver,
) {
    private val col get() = firestore.collection("collabPlaylists")
    private val users get() = firestore.collection("users")
    private fun inbox(uid: String) = users.document(uid).collection("inbox")
    val myUid: String? get() = auth.currentUser?.uid

    private fun uidFlow(): Flow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }.distinctUntilChanged()

    @Suppress("UNCHECKED_CAST")
    private fun parse(doc: DocumentSnapshot): CollabPlaylist? {
        if (!doc.exists()) return null
        return CollabPlaylist(
            id = doc.id,
            name = doc.getString("name") ?: "Group playlist",
            ownerId = doc.getString("ownerId") ?: "",
            ownerName = doc.getString("ownerName") ?: "",
            memberIds = doc.get("memberIds") as? List<String> ?: emptyList(),
            memberNames = doc.get("memberNames") as? Map<String, String> ?: emptyMap(),
            tracks = (doc.get("tracks") as? List<Map<*, *>>).orEmpty().mapNotNull { RemoteTrack.fromMap(it) },
            updatedAt = doc.getLong("updatedAt") ?: 0L,
        )
    }

    fun observeMine(): Flow<List<CollabPlaylist>> = uidFlow().flatMapLatest { me ->
        if (me == null) flowOf(emptyList()) else callbackFlow {
            val l = col.whereArrayContains("memberIds", me).addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull(::parse)?.sortedByDescending { it.updatedAt }.orEmpty())
            }
            awaitClose { l.remove() }
        }
    }

    fun observe(id: String): Flow<CollabPlaylist?> = callbackFlow {
        val l = col.document(id).addSnapshotListener { snap, err ->
            if (err != null) { trySend(null); return@addSnapshotListener }
            trySend(snap?.let(::parse))
        }
        awaitClose { l.remove() }
    }

    private suspend fun myName(me: String) = users.document(me).get().await().let {
        (it.getString("displayName") ?: "Friend") to (it.getString("avatarUrl") ?: "")
    }

    /** friends = uid → display name */
    suspend fun create(name: String, friends: Map<String, String>): Result<String> = runCatching {
        val me = myUid ?: error("Please sign in first")
        val clean = name.trim().ifBlank { "Our playlist" }.take(60)
        val (myName, avatar) = myName(me)
        val ref = col.document()
        val now = System.currentTimeMillis()
        ref.set(mapOf(
            "name" to clean, "ownerId" to me, "ownerName" to myName,
            "memberIds" to (listOf(me) + friends.keys).distinct(),
            "memberNames" to (friends + (me to myName)),
            "tracks" to emptyList<Map<String, Any>>(),
            "createdAt" to now, "updatedAt" to now,
        )).await()
        invite(me, myName, avatar, ref.id, clean, friends.keys.toList())
        ref.id
    }

    private suspend fun invite(me: String, myName: String, avatar: String, id: String, name: String, to: List<String>) {
        if (to.isEmpty()) return
        val b = firestore.batch()
        to.forEach { f ->
            val r = inbox(f).document()
            b.set(r, mapOf(
                "notifId" to r.id, "type" to "COLLAB_INVITE", "fromUid" to me, "fromName" to myName,
                "fromAvatar" to avatar, "shareId" to id, "playlistName" to name,
                "sentAt" to System.currentTimeMillis(), "isRead" to false,
            ))
        }
        b.commit().await()
    }

    suspend fun addMembers(p: CollabPlaylist, friends: Map<String, String>): Result<Unit> = runCatching {
        val me = myUid ?: error("Please sign in first")
        val fresh = friends.filterKeys { it !in p.memberIds }
        if (fresh.isEmpty()) return@runCatching
        val updates = mutableMapOf<String, Any>(
            "memberIds" to FieldValue.arrayUnion(*fresh.keys.toTypedArray()),
            "updatedAt" to System.currentTimeMillis(),
        )
        fresh.forEach { (uid, n) -> updates["memberNames.$uid"] = n }
        col.document(p.id).update(updates).await()
        val (myName, avatar) = myName(me)
        invite(me, myName, avatar, p.id, p.name, fresh.keys.toList())
    }

    /**
     * Add songs from my library. Local files are uploaded so every member can play them.
     * [onProgress] gets (index, total, upload% of the current file).
     */
    suspend fun addSongs(id: String, songs: List<Song>, onProgress: (Int, Int, Float) -> Unit = { _, _, _ -> }): Result<Int> = runCatching {
        val me = myUid ?: error("Please sign in first")
        val (myName, _) = myName(me)
        val tracks = mutableListOf<RemoteTrack>()
        var failed = 0
        songs.forEachIndexed { i, s ->
            onProgress(i, songs.size, 0f)
            resolver.ensure(s) { onProgress(i, songs.size, it) }
                .onSuccess { tracks += it.copy(addedBy = me, addedByName = myName, addedAt = System.currentTimeMillis()) }
                .onFailure { failed++ }
        }
        if (tracks.isEmpty()) error(if (failed > 0) "Couldn't upload the selected songs" else "Nothing to add")
        val ref = col.document(id)
        firestore.runTransaction { tx ->
            val doc = tx.get(ref)
            @Suppress("UNCHECKED_CAST")
            val existing = (doc.get("tracks") as? List<Map<String, Any>>).orEmpty()
            val keys = existing.mapNotNull { it["key"] as? String }.toMutableSet()
            val added = tracks.filter { keys.add(it.key) }.map { it.toMap() }
            tx.update(ref, mapOf("tracks" to existing + added, "updatedAt" to System.currentTimeMillis()))
            null
        }.await()
        tracks.size
    }

    suspend fun removeTrack(id: String, key: String): Result<Unit> = runCatching {
        val ref = col.document(id)
        firestore.runTransaction { tx ->
            @Suppress("UNCHECKED_CAST")
            val existing = (tx.get(ref).get("tracks") as? List<Map<String, Any>>).orEmpty()
            tx.update(ref, mapOf("tracks" to existing.filter { it["key"] != key }, "updatedAt" to System.currentTimeMillis()))
            null
        }.await()
    }

    suspend fun rename(id: String, name: String): Result<Unit> = runCatching {
        col.document(id).update(mapOf("name" to name.trim().take(60).ifBlank { "Our playlist" }, "updatedAt" to System.currentTimeMillis())).await()
    }

    suspend fun leave(p: CollabPlaylist): Result<Unit> = runCatching {
        val me = myUid ?: error("Please sign in first")
        if (p.ownerId == me) { col.document(p.id).delete().await(); return@runCatching }
        col.document(p.id).update(mapOf(
            "memberIds" to FieldValue.arrayRemove(me), "memberNames.$me" to FieldValue.delete(),
            "updatedAt" to System.currentTimeMillis(),
        )).await()
    }

    fun queueOf(p: CollabPlaylist): List<Song> = p.tracks.mapNotNull { resolver.toQueueSong(it) }
}
