package com.waleve.player.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.FriendRequest
import com.waleve.player.domain.model.FriendRequestStatus
import com.waleve.player.domain.model.InboxNotification
import com.waleve.player.domain.model.InboxNotificationType
import com.waleve.player.domain.repository.FriendRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : FriendRepository {

    private val currentUid get() = auth.currentUser?.uid

    // ─── Collections ─────────────────────────────────────────────────────────
    private val usersCol    get() = firestore.collection("users")
    private val requestsCol get() = firestore.collection("friendRequests")

    private fun inboxCol(uid: String) =
        firestore.collection("users").document(uid).collection("inbox")

    // ─── Ensure profile ───────────────────────────────────────────────────────
    override suspend fun ensureUserProfile(uid: String, displayName: String, email: String) {
        val data = mapOf(
            "uid"         to uid,
            "displayName" to displayName,
            "email"       to email,
            "displayNameLower" to displayName.lowercase(), // for prefix search
        )
        usersCol.document(uid).set(data, SetOptions.merge()).await()
    }

    // ─── Search users ─────────────────────────────────────────────────────────
    override suspend fun searchUsers(query: String): List<Friend> {
        val uid = currentUid ?: return emptyList()
        val lower = query.lowercase().trim()
        if (lower.length < 2) return emptyList()

        val snapshot = usersCol
            .whereGreaterThanOrEqualTo("displayNameLower", lower)
            .whereLessThan("displayNameLower", lower + "\uf8ff")
            .limit(20)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val docUid = doc.getString("uid") ?: return@mapNotNull null
            if (docUid == uid) return@mapNotNull null // exclude self
            docToFriend(doc)
        }
    }

    // ─── Search by Friend Code ────────────────────────────────────────────────
    override suspend fun searchByFriendCode(code: String): Friend? {
        val uid = currentUid ?: return null
        val upper = code.uppercase().trim()
        if (!upper.startsWith("WLEV-")) return null

        val snapshot = usersCol
            .whereEqualTo("friendCode", upper)
            .limit(1)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let { doc ->
            val docUid = doc.getString("uid") ?: return null
            if (docUid == uid) return null // can't add self
            docToFriend(doc)
        }
    }

    // ─── Search by Email ──────────────────────────────────────────────────────
    override suspend fun searchByEmail(email: String): Friend? {
        val uid = currentUid ?: return null
        val trimmed = email.lowercase().trim()
        if (!trimmed.contains("@")) return null

        val snapshot = usersCol
            .whereEqualTo("email", trimmed)
            .limit(1)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let { doc ->
            val docUid = doc.getString("uid") ?: return null
            if (docUid == uid) return null // can't add self
            docToFriend(doc)
        }
    }

    /** Helper: map a Firestore document to a Friend. */
    private fun docToFriend(doc: com.google.firebase.firestore.DocumentSnapshot): Friend {
        return Friend(
            uid         = doc.getString("uid") ?: doc.id,
            displayName = doc.getString("displayName") ?: "Unknown",
            email       = doc.getString("email") ?: "",
            avatarUrl   = doc.getString("avatarUrl"),
            friendCode  = doc.getString("friendCode") ?: "",
        )
    }


    // ─── Send friend request ──────────────────────────────────────────────────
    override suspend fun sendFriendRequest(toUid: String, toName: String): Result<Unit> {
        return try {
            val uid = currentUid ?: return Result.failure(Exception("Not signed in"))
            val meDoc = usersCol.document(uid).get().await()
            val myName = meDoc.getString("displayName") ?: "Unknown"

            // Check if request already exists
            val existing = requestsCol
                .whereEqualTo("fromUid", uid)
                .whereEqualTo("toUid", toUid)
                .whereEqualTo("status", "PENDING")
                .get().await()
            if (!existing.isEmpty) return Result.failure(Exception("Request already sent"))

            val docRef = requestsCol.document()
            val requestId = docRef.id
            val data = mapOf(
                "requestId" to requestId,
                "fromUid"   to uid,
                "fromName"  to myName,
                "toUid"     to toUid,
                "toName"    to toName,
                "status"    to "PENDING",
                "sentAt"    to System.currentTimeMillis(),
            )
            docRef.set(data).await()

            // Push inbox notification to recipient
            val notifRef = inboxCol(toUid).document()
            val notif = mapOf(
                "notifId"   to notifRef.id,
                "type"      to "FRIEND_REQUEST",
                "fromUid"   to uid,
                "fromName"  to myName,
                "requestId" to requestId,
                "sentAt"    to System.currentTimeMillis(),
                "isRead"    to false,
            )
            notifRef.set(notif).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Accept friend request ────────────────────────────────────────────────
    override suspend fun acceptFriendRequest(requestId: String, fromUid: String): Result<Unit> {
        return try {
            val uid = currentUid ?: return Result.failure(Exception("Not signed in"))

            // Update request status
            requestsCol.document(requestId).update("status", "ACCEPTED").await()

            // Add each other to friend lists (array union) + increment friendCount
            usersCol.document(uid).update(
                mapOf(
                    "friendIds" to com.google.firebase.firestore.FieldValue.arrayUnion(fromUid),
                    "friendCount" to com.google.firebase.firestore.FieldValue.increment(1),
                )
            ).await()
            usersCol.document(fromUid).update(
                mapOf(
                    "friendIds" to com.google.firebase.firestore.FieldValue.arrayUnion(uid),
                    "friendCount" to com.google.firebase.firestore.FieldValue.increment(1),
                )
            ).await()

            // Remove the inbox notification
            val notifs = inboxCol(uid)
                .whereEqualTo("requestId", requestId).get().await()
            notifs.documents.forEach { it.reference.delete() }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Decline friend request ───────────────────────────────────────────────
    override suspend fun declineFriendRequest(requestId: String): Result<Unit> {
        return try {
            val uid = currentUid ?: return Result.failure(Exception("Not signed in"))
            requestsCol.document(requestId).update("status", "DECLINED").await()
            val notifs = inboxCol(uid)
                .whereEqualTo("requestId", requestId).get().await()
            notifs.documents.forEach { it.reference.delete() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Observe friends (real-time) ──────────────────────────────────────────
    override fun observeFriends(): Flow<List<Friend>> {
        val uid = currentUid ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = usersCol.document(uid)
                .addSnapshotListener { snap, _ ->
                    if (snap == null) { trySend(emptyList()); return@addSnapshotListener }
                    @Suppress("UNCHECKED_CAST")
                    val friendIds = snap.get("friendIds") as? List<String> ?: emptyList()
                    if (friendIds.isEmpty()) { trySend(emptyList()); return@addSnapshotListener }

                    // Fetch friend profiles (Firestore "in" query max 30 per chunk)
                    friendIds.chunked(30).forEach { chunk ->
                        usersCol.whereIn("uid", chunk).get()
                            .addOnSuccessListener { result ->
                                val friends = result.documents.mapNotNull { doc ->
                                    val fUid = doc.getString("uid") ?: return@mapNotNull null
                                    docToFriend(doc)
                                }

                                trySend(friends)
                            }
                    }
                }
            awaitClose { listener.remove() }
        }
    }

    // ─── Observe inbox (real-time) ─────────────────────────────────────────────
    override fun observeInbox(): Flow<List<InboxNotification>> {
        val uid = currentUid ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = inboxCol(uid)
                .orderBy("sentAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snap, _ ->
                    if (snap == null) { trySend(emptyList()); return@addSnapshotListener }
                    val items = snap.documents.mapNotNull { doc ->
                        val typeStr = doc.getString("type") ?: return@mapNotNull null
                        val type = when (typeStr) {
                            "FRIEND_REQUEST"  -> InboxNotificationType.FRIEND_REQUEST
                            "PLAYLIST_SHARED" -> InboxNotificationType.PLAYLIST_SHARED
                            "SONG_SHARED"     -> InboxNotificationType.SONG_SHARED
                            else              -> return@mapNotNull null
                        }
                        InboxNotification(
                            notifId      = doc.getString("notifId") ?: doc.id,
                            type         = type,
                            fromUid      = doc.getString("fromUid") ?: "",
                            fromName     = doc.getString("fromName") ?: "Unknown",
                            shareId      = doc.getString("shareId"),
                            playlistName = doc.getString("playlistName"),
                            songTitle    = doc.getString("songTitle"),
                            thumbnailUrl = doc.getString("thumbnailUrl"),
                            videoId      = doc.getString("videoId"),
                            requestId    = doc.getString("requestId"),
                            sentAt       = doc.getLong("sentAt") ?: 0L,
                            isRead       = doc.getBoolean("isRead") ?: false,
                        )
                    }
                    trySend(items)
                }
            awaitClose { listener.remove() }
        }
    }

    // ─── Mark notification read ───────────────────────────────────────────────
    override suspend fun markNotificationRead(notifId: String) {
        val uid = currentUid ?: return
        inboxCol(uid).document(notifId).update("isRead", true).await()
    }

    // ─── Outgoing requests ────────────────────────────────────────────────────
    override suspend fun getOutgoingRequests(): List<FriendRequest> {
        val uid = currentUid ?: return emptyList()
        return try {
            val snap = requestsCol
                .whereEqualTo("fromUid", uid)
                .whereEqualTo("status", "PENDING")
                .get().await()
            snap.documents.mapNotNull { doc ->
                FriendRequest(
                    requestId = doc.getString("requestId") ?: doc.id,
                    fromUid   = uid,
                    fromName  = doc.getString("fromName") ?: "",
                    toUid     = doc.getString("toUid") ?: "",
                    toName    = doc.getString("toName") ?: "",
                    status    = FriendRequestStatus.PENDING,
                    sentAt    = doc.getLong("sentAt") ?: 0L,
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ─── Remove friend ────────────────────────────────────────────────────────
    override suspend fun removeFriend(friendUid: String): Result<Unit> {
        return try {
            val uid = currentUid ?: return Result.failure(Exception("Not signed in"))
            usersCol.document(uid).update(
                mapOf(
                    "friendIds" to com.google.firebase.firestore.FieldValue.arrayRemove(friendUid),
                    "friendCount" to com.google.firebase.firestore.FieldValue.increment(-1),
                )
            ).await()
            usersCol.document(friendUid).update(
                mapOf(
                    "friendIds" to com.google.firebase.firestore.FieldValue.arrayRemove(uid),
                    "friendCount" to com.google.firebase.firestore.FieldValue.increment(-1),
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Share a single song ─────────────────────────────────────────────────
    override suspend fun shareSong(song: com.waleve.player.domain.model.Song, friendUids: List<String>): Result<Unit> {
        return try {
            val uid = currentUid ?: return Result.failure(Exception("Not signed in"))
            val meDoc = usersCol.document(uid).get().await()
            val myName = meDoc.getString("displayName") ?: "Unknown"

            // Extract or get videoId (local-only songs cannot be meaningfully shared directly via this method unless we build a P2P sync)
            val isRemote = song.source == "stream" && song.path.startsWith("http")
            val videoId = song.videoId 
                ?: if (isRemote) extractVideoIdFromUrl(song.path) else null
                
            if (videoId == null) {
                return Result.failure(Exception("Cannot share local-only songs directly."))
            }

            friendUids.forEach { friendUid ->
                val notifRef = inboxCol(friendUid).document()
                val notif = mapOf(
                    "notifId"      to notifRef.id,
                    "type"         to "SONG_SHARED",
                    "fromUid"      to uid,
                    "fromName"     to myName,
                    "songTitle"    to song.title,
                    "thumbnailUrl" to (song.albumArtUri ?: ""),
                    "videoId"      to videoId,
                    "sentAt"       to System.currentTimeMillis(),
                    "isRead"       to false,
                )
                notifRef.set(notif).await()
            }
            Result.success(Unit)
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
