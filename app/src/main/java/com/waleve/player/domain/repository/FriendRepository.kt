package com.waleve.player.domain.repository

import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.FriendRequest
import com.waleve.player.domain.model.InboxNotification
import kotlinx.coroutines.flow.Flow

/** Result of "Add friend": a new request, or instant friendship because they had already asked you. */
enum class SendRequestResult { SENT, BECAME_FRIENDS }

/**
 * Everything social: profiles, friend requests (send → accept / decline / cancel), friends, inbox.
 * All observe* flows follow sign-in / sign-out automatically.
 */
interface FriendRepository {
    /** Emits the signed-in uid, or null when signed out. */
    fun observeUid(): Flow<String?>

    suspend fun ensureUserProfile(uid: String, displayName: String, email: String)

    suspend fun searchUsers(query: String): List<Friend>
    /** Friend codes look like OLV-AB12 (older accounts: WLEV-AB12). The prefix is optional. */
    suspend fun searchByFriendCode(code: String): Friend?
    suspend fun searchByEmail(email: String): Friend?

    suspend fun sendFriendRequest(toUid: String, toName: String): Result<SendRequestResult>
    suspend fun acceptFriendRequest(requestId: String, fromUid: String): Result<Unit>
    suspend fun declineFriendRequest(requestId: String): Result<Unit>
    /** Take back a request you sent that hasn't been answered yet. */
    suspend fun cancelFriendRequest(requestId: String): Result<Unit>
    suspend fun removeFriend(friendUid: String): Result<Unit>

    fun observeFriends(): Flow<List<Friend>>
    fun observeIncomingRequests(): Flow<List<FriendRequest>>
    fun observeOutgoingRequests(): Flow<List<FriendRequest>>
    fun observeInbox(): Flow<List<InboxNotification>>

    suspend fun markNotificationRead(notifId: String)
    suspend fun markAllRead()
    suspend fun deleteNotification(notifId: String)
    /** Delete every inbox item (friend-request notices are handled by the Requests tab). */
    suspend fun clearInbox()

    suspend fun getOutgoingRequests(): List<FriendRequest>

    suspend fun shareSong(
        song: com.waleve.player.domain.model.Song,
        friendUids: List<String>,
        onUploadProgress: (Float) -> Unit = {},
    ): Result<Unit>
}
