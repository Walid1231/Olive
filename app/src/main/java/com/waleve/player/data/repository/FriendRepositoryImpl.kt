package com.waleve.player.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.FriendRequest
import com.waleve.player.domain.model.FriendRequestStatus
import com.waleve.player.domain.model.InboxNotification
import com.waleve.player.domain.model.InboxNotificationType
import com.waleve.player.domain.repository.FriendRepository
import com.waleve.player.domain.repository.SendRequestResult
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

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val supabaseStorage: com.waleve.player.data.remote.SupabaseStorage,
) : FriendRepository {

    private val currentUid get() = auth.currentUser?.uid

    private val usersCol    get() = firestore.collection("users")
    private val requestsCol get() = firestore.collection("friendRequests")
    private fun inboxCol(uid: String) = firestore.collection("users").document(uid).collection("inbox")

    // ─── Auth-aware plumbing ─────────────────────────────────────────────────
    override fun observeUid(): Flow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }.distinctUntilChanged()

    private fun <T> perUser(empty: T, block: (String) -> Flow<T>): Flow<T> =
        observeUid().flatMapLatest { uid -> if (uid == null) flowOf(empty) else block(uid) }

    // ─── Profile ─────────────────────────────────────────────────────────────
    override suspend fun ensureUserProfile(uid: String, displayName: String, email: String) {
        val doc = usersCol.document(uid).get().await()
        val data = mutableMapOf<String, Any>("uid" to uid, "email" to email.lowercase())
        // Only fill the name if missing — never overwrite a name the user chose in their profile
        if (doc.getString("displayName").isNullOrBlank()) {
            data["displayName"] = displayName
            data["displayNameLower"] = displayName.lowercase()
        }
        usersCol.document(uid).set(data, SetOptions.merge()).await()
    }

    // ─── Search ──────────────────────────────────────────────────────────────
    override suspend fun searchUsers(query: String): List<Friend> {
        val uid = currentUid ?: return emptyList()
        val lower = query.lowercase().trim()
        if (lower.length < 2) return emptyList()
        val snapshot = usersCol
            .whereGreaterThanOrEqualTo("displayNameLower", lower)
            .whereLessThan("displayNameLower", lower + "\uf8ff")
            .limit(20).get().await()
        return snapshot.documents.filter { it.id != uid }.map { docToFriend(it) }
    }

    override suspend fun searchByFriendCode(code: String): Friend? {
        val uid = currentUid ?: return null
        val raw = code.uppercase().trim().replace(" ", "")
        if (raw.length < 4) return null
        val candidates = if (raw.contains("-")) listOf(raw) else listOf("OLV-$raw", "WLEV-$raw")
        for (c in candidates) {
            val snap = usersCol.whereEqualTo("friendCode", c).limit(1).get().await()
            val doc = snap.documents.firstOrNull() ?: continue
            return if (doc.id == uid) null else docToFriend(doc)
        }
        return null
    }

    override suspend fun searchByEmail(email: String): Friend? {
        val uid = currentUid ?: return null
        val trimmed = email.trim()
        if (!trimmed.contains("@")) return null
        for (e in listOf(trimmed.lowercase(), trimmed).distinct()) {
            val doc = usersCol.whereEqualTo("email", e).limit(1).get().await().documents.firstOrNull() ?: continue
            return if (doc.id == uid) null else docToFriend(doc)
        }
        return null
    }

    private fun docToFriend(doc: DocumentSnapshot) = Friend(
        uid         = doc.getString("uid") ?: doc.id,
        displayName = doc.getString("displayName") ?: "Unknown",
        email       = doc.getString("email") ?: "",
        avatarUrl   = doc.getString("avatarUrl")?.takeIf { it.isNotBlank() },
        friendCode  = doc.getString("friendCode") ?: "",
    )

    @Suppress("UNCHECKED_CAST")
    private fun friendIdsOf(doc: DocumentSnapshot): List<String> = doc.get("friendIds") as? List<String> ?: emptyList()

    // ─── Requests: send ─────────────────────────────────────────────────────
    override suspend fun sendFriendRequest(toUid: String, toName: String): Result<SendRequestResult> = runCatching {
        val uid = currentUid ?: error("Please sign in first")
        if (toUid == uid) error("That's your own account 🙂")
        val me = usersCol.document(uid).get().await()
        if (toUid in friendIdsOf(me)) error("You're already friends with $toName")

        // They already asked me → just accept theirs (no duplicate requests)
        val theirs = requestsCol.whereEqualTo("fromUid", toUid).whereEqualTo("toUid", uid)
            .whereEqualTo("status", "PENDING").get().await().documents.firstOrNull()
        if (theirs != null) {
            acceptFriendRequest(theirs.id, toUid).getOrThrow()
            return@runCatching SendRequestResult.BECAME_FRIENDS
        }
        val mine = requestsCol.whereEqualTo("fromUid", uid).whereEqualTo("toUid", toUid)
            .whereEqualTo("status", "PENDING").get().await()
        if (!mine.isEmpty) error("Request already sent — waiting for $toName")

        val them = usersCol.document(toUid).get().await()
        val myName = me.getString("displayName") ?: "Someone"
        val myAvatar = me.getString("avatarUrl") ?: ""
        val now = System.currentTimeMillis()
        val reqRef = requestsCol.document()
        val notifRef = inboxCol(toUid).document()
        firestore.batch().apply {
            set(reqRef, mapOf(
                "requestId" to reqRef.id, "fromUid" to uid, "fromName" to myName, "fromAvatar" to myAvatar,
                "toUid" to toUid, "toName" to (them.getString("displayName") ?: toName),
                "toAvatar" to (them.getString("avatarUrl") ?: ""), "status" to "PENDING", "sentAt" to now,
            ))
            set(notifRef, mapOf(
                "notifId" to notifRef.id, "type" to "FRIEND_REQUEST", "fromUid" to uid, "fromName" to myName,
                "fromAvatar" to myAvatar, "requestId" to reqRef.id, "sentAt" to now, "isRead" to false,
            ))
        }.commit().await()
        SendRequestResult.SENT
    }

    // ─── Requests: accept / decline / cancel ────────────────────────────────
    override suspend fun acceptFriendRequest(requestId: String, fromUid: String): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Please sign in first")
        val req = requestsCol.document(requestId).get().await()
        if (!req.exists()) error("This request no longer exists")
        when (req.getString("status")) {
            "CANCELLED" -> error("They took this request back")
            "ACCEPTED" -> return@runCatching
        }
        val me = usersCol.document(uid).get().await()
        val myName = me.getString("displayName") ?: "Someone"
        val now = System.currentTimeMillis()
        val notifRef = inboxCol(fromUid).document()
        // One atomic write: request accepted + both friend lists + "accepted" notice for them
        firestore.batch().apply {
            update(requestsCol.document(requestId), mapOf("status" to "ACCEPTED", "respondedAt" to now))
            set(usersCol.document(uid), mapOf("friendIds" to FieldValue.arrayUnion(fromUid), "friendCount" to FieldValue.increment(1)), SetOptions.merge())
            update(usersCol.document(fromUid), mapOf("friendIds" to FieldValue.arrayUnion(uid), "friendCount" to FieldValue.increment(1)))
            set(notifRef, mapOf(
                "notifId" to notifRef.id, "type" to "FRIEND_ACCEPTED", "fromUid" to uid, "fromName" to myName,
                "fromAvatar" to (me.getString("avatarUrl") ?: ""), "requestId" to requestId, "sentAt" to now, "isRead" to false,
            ))
        }.commit().await()
        deleteRequestNotices(uid, requestId)
    }

    override suspend fun declineFriendRequest(requestId: String): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Please sign in first")
        requestsCol.document(requestId).update(mapOf("status" to "DECLINED", "respondedAt" to System.currentTimeMillis())).await()
        deleteRequestNotices(uid, requestId)
    }

    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> = runCatching {
        currentUid ?: error("Please sign in first")
        requestsCol.document(requestId).update(mapOf("status" to "CANCELLED", "respondedAt" to System.currentTimeMillis())).await()
    }

    private suspend fun deleteRequestNotices(uid: String, requestId: String) {
        runCatching {
            inboxCol(uid).whereEqualTo("requestId", requestId).get().await().documents
                .filter { it.getString("type") == "FRIEND_REQUEST" }
                .forEach { it.reference.delete().await() }
        }
    }

    // ─── Remove friend (both sides, atomically) ─────────────────────────────
    override suspend fun removeFriend(friendUid: String): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Please sign in first")
        firestore.batch().apply {
            update(usersCol.document(uid), mapOf("friendIds" to FieldValue.arrayRemove(friendUid), "friendCount" to FieldValue.increment(-1)))
            update(usersCol.document(friendUid), mapOf("friendIds" to FieldValue.arrayRemove(uid), "friendCount" to FieldValue.increment(-1)))
        }.commit().await()
    }

    // ─── Real-time lists ─────────────────────────────────────────────────────
    override fun observeFriends(): Flow<List<Friend>> = perUser(emptyList()) { uid ->
        callbackFlow {
            val listener = usersCol.document(uid).addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val ids = friendIdsOf(snap).distinct()
                if (ids.isEmpty()) { trySend(emptyList()); return@addSnapshotListener }
                val tasks = ids.map { usersCol.document(it).get() }
                com.google.android.gms.tasks.Tasks.whenAllComplete(tasks).addOnCompleteListener {
                    val friends = tasks.mapNotNull { t ->
                        if (!t.isSuccessful) return@mapNotNull null
                        val doc = t.result ?: return@mapNotNull null
                        if (!doc.exists()) null else docToFriend(doc)
                    }
                    trySend(friends.sortedBy { it.displayName.lowercase() })
                }
            }
            awaitClose { listener.remove() }
        }
    }

    private fun docToRequest(doc: DocumentSnapshot) = FriendRequest(
        requestId = doc.getString("requestId") ?: doc.id,
        fromUid   = doc.getString("fromUid") ?: "",
        fromName  = doc.getString("fromName") ?: "Someone",
        toUid     = doc.getString("toUid") ?: "",
        toName    = doc.getString("toName") ?: "Someone",
        status    = runCatching { FriendRequestStatus.valueOf(doc.getString("status") ?: "PENDING") }.getOrDefault(FriendRequestStatus.PENDING),
        sentAt    = doc.getLong("sentAt") ?: 0L,
        fromAvatar = doc.getString("fromAvatar")?.takeIf { it.isNotBlank() },
        toAvatar   = doc.getString("toAvatar")?.takeIf { it.isNotBlank() },
    )

    private fun requestsFlow(field: String, uid: String): Flow<List<FriendRequest>> = callbackFlow {
        val listener = requestsCol.whereEqualTo(field, uid).whereEqualTo("status", "PENDING")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.map { docToRequest(it) }.sortedByDescending { it.sentAt })
            }
        awaitClose { listener.remove() }
    }

    override fun observeIncomingRequests(): Flow<List<FriendRequest>> = perUser(emptyList()) { requestsFlow("toUid", it) }
    override fun observeOutgoingRequests(): Flow<List<FriendRequest>> = perUser(emptyList()) { requestsFlow("fromUid", it) }

    override fun observeInbox(): Flow<List<InboxNotification>> = perUser(emptyList()) { uid ->
        callbackFlow {
            val listener = inboxCol(uid)
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snap, _ ->
                    if (snap == null) return@addSnapshotListener
                    val items = snap.documents.mapNotNull { doc ->
                        val type = when (doc.getString("type")) {
                            "FRIEND_REQUEST"  -> InboxNotificationType.FRIEND_REQUEST
                            "FRIEND_ACCEPTED" -> InboxNotificationType.FRIEND_ACCEPTED
                            "PLAYLIST_SHARED" -> InboxNotificationType.PLAYLIST_SHARED
                            "SONG_SHARED"     -> InboxNotificationType.SONG_SHARED
                            "LISTEN_INVITE"   -> InboxNotificationType.LISTEN_INVITE
                            "COLLAB_INVITE"   -> InboxNotificationType.COLLAB_INVITE
                            "REACTION"        -> InboxNotificationType.REACTION
                            else              -> return@mapNotNull null
                        }
                        InboxNotification(
                            notifId      = doc.getString("notifId") ?: doc.id,
                            type         = type,
                            fromUid      = doc.getString("fromUid") ?: "",
                            fromName     = doc.getString("fromName") ?: "Unknown",
                            fromAvatar   = doc.getString("fromAvatar")?.takeIf { it.isNotBlank() },
                            shareId      = doc.getString("shareId"),
                            playlistName = doc.getString("playlistName"),
                            songTitle    = doc.getString("songTitle"),
                            thumbnailUrl = doc.getString("thumbnailUrl")?.takeIf { it.isNotBlank() },
                            videoId      = doc.getString("videoId"),
                            requestId    = doc.getString("requestId"),
                            sentAt       = doc.getLong("sentAt") ?: 0L,
                            isRead       = doc.getBoolean("isRead") ?: false,
                            artist       = doc.getString("artist"),
                            durationMs   = doc.getLong("durationMs") ?: 0L,
                            mediaType    = doc.getString("mediaType") ?: "music",
                            fileUrl      = doc.getString("fileUrl"),
                            fileName     = doc.getString("fileName"),
                            mimeType     = doc.getString("mimeType"),
                            fileSize     = doc.getLong("fileSize") ?: 0L,
                            reaction     = doc.getString("reaction"),
                            message      = doc.getString("message")?.takeIf { it.isNotBlank() },
                            myReaction   = doc.getString("myReaction"),
                            transferId   = doc.getString("transferId"),
                            savedPath    = doc.getString("savedPath"),
                            saveFailed   = doc.getBoolean("saveFailed") ?: false,
                        )
                    }
                    trySend(items)
                }
            awaitClose { listener.remove() }
        }
    }

    // ─── Inbox actions ───────────────────────────────────────────────────────
    override suspend fun markNotificationRead(notifId: String) {
        val uid = currentUid ?: return
        runCatching { inboxCol(uid).document(notifId).update("isRead", true).await() }
    }

    override suspend fun markAllRead() {
        val uid = currentUid ?: return
        runCatching {
            val unread = inboxCol(uid).whereEqualTo("isRead", false).get().await().documents
            unread.chunked(400).forEach { chunk ->
                firestore.batch().apply { chunk.forEach { update(it.reference, "isRead", true) } }.commit().await()
            }
        }
    }

    override suspend fun deleteNotification(notifId: String) {
        val uid = currentUid ?: return
        runCatching { inboxCol(uid).document(notifId).delete().await() }
    }

    override suspend fun clearInbox() {
        val uid = currentUid ?: return
        runCatching {
            val docs = inboxCol(uid).get().await().documents.filter { it.getString("type") != "FRIEND_REQUEST" }
            docs.chunked(400).forEach { chunk ->
                firestore.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
            }
        }
    }

    override suspend fun getOutgoingRequests(): List<FriendRequest> {
        val uid = currentUid ?: return emptyList()
        return runCatching {
            requestsCol.whereEqualTo("fromUid", uid).whereEqualTo("status", "PENDING").get().await().documents.map { docToRequest(it) }
        }.getOrDefault(emptyList())
    }

    // ─── Share a single song / audiobook ─────────────────────────────────────
    // Online songs (YouTube id) share as a link. Local files are uploaded to Supabase Storage
    // so friends can stream or download the exact file.
    override suspend fun shareSong(
        song: com.waleve.player.domain.model.Song,
        friendUids: List<String>,
        onUploadProgress: (Float) -> Unit,
    ): Result<Unit> {
        return try {
            val uid = currentUid ?: return Result.failure(Exception("Not signed in"))
            val meDoc = usersCol.document(uid).get().await()
            val myName = meDoc.getString("displayName") ?: "Unknown"
            val myAvatar = meDoc.getString("avatarUrl") ?: ""

            val isRemote = song.source == "stream" && song.path.startsWith("http")
            val videoId = song.videoId
                ?: if (isRemote) extractVideoIdFromUrl(song.path) else null

            var fileUrl: String? = null
            var fileName: String? = null
            var mimeType: String? = null
            var fileSize = 0L
            if (videoId == null) {
                if (isRemote) {
                    // Already a direct URL (e.g. a file a friend shared with us) — just pass it on
                    fileUrl = song.path
                    fileName = song.title
                } else {
                    mimeType = com.waleve.player.data.remote.SupabaseStorage.mimeForPath(song.path)
                    val up = supabaseStorage.uploadLocalFile(song.path, uid, mimeType, onUploadProgress)
                        .getOrElse { return Result.failure(it) }
                    fileUrl = up.publicUrl
                    fileName = java.io.File(song.path).name
                    fileSize = up.size
                }
            }

            // Don't send content:// art URIs — they only exist on this phone
            val thumb = song.albumArtUri?.takeIf { it.startsWith("http") } ?: ""

            // A file uploaded just for this share is temporary: every friend's phone saves it,
            // and the last one to do so deletes it from Supabase (see ShareAutoSaver).
            var transferId: String? = null
            if (fileUrl != null && fileSize > 0) {
                val t = firestore.collection("transfers").document()
                t.set(mapOf(
                    "fromUid" to uid, "url" to fileUrl, "pending" to friendUids.distinct(),
                    "title" to song.title, "createdAt" to System.currentTimeMillis(),
                )).await()
                transferId = t.id
            }

            friendUids.forEach { friendUid ->
                val notifRef = inboxCol(friendUid).document()
                val notif = mutableMapOf<String, Any>(
                    "notifId"      to notifRef.id,
                    "type"         to "SONG_SHARED",
                    "fromUid"      to uid,
                    "fromName"     to myName,
                    "fromAvatar"   to myAvatar,
                    "songTitle"    to song.title,
                    "artist"       to song.artist,
                    "durationMs"   to song.duration,
                    "mediaType"    to song.mediaType,
                    "thumbnailUrl" to thumb,
                    "sentAt"       to System.currentTimeMillis(),
                    "isRead"       to false,
                )
                videoId?.let { notif["videoId"] = it }
                fileUrl?.let { notif["fileUrl"] = it }
                fileName?.let { notif["fileName"] = it }
                mimeType?.let { notif["mimeType"] = it }
                if (fileSize > 0) notif["fileSize"] = fileSize
                transferId?.let { notif["transferId"] = it }
                notifRef.set(notif).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Reactions / replies to a shared song ────────────────────────────────
    override suspend fun reactToShare(notif: InboxNotification, emoji: String?, message: String?): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Please sign in first")
        val me = usersCol.document(uid).get().await()
        if (emoji != null) inboxCol(uid).document(notif.notifId).update("myReaction", emoji).await()
        val ref = inboxCol(notif.fromUid).document()
        val data = mutableMapOf<String, Any>(
            "notifId" to ref.id, "type" to "REACTION", "fromUid" to uid,
            "fromName" to (me.getString("displayName") ?: "Someone"), "fromAvatar" to (me.getString("avatarUrl") ?: ""),
            "songTitle" to (notif.songTitle ?: notif.playlistName ?: "your share"),
            "sentAt" to System.currentTimeMillis(), "isRead" to false,
        )
        emoji?.let { data["reaction"] = it }
        message?.trim()?.take(200)?.takeIf { it.isNotEmpty() }?.let { data["message"] = it }
        notif.thumbnailUrl?.let { data["thumbnailUrl"] = it }
        ref.set(data).await()
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
