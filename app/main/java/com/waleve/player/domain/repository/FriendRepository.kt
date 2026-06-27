package com.waleve.player.domain.repository

import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.FriendRequest
import com.waleve.player.domain.model.InboxNotification
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for all friend-related Firebase operations.
 */
interface FriendRepository {

    /** Ensure this user's profile exists in Firestore (called on app start). */
    suspend fun ensureUserProfile(uid: String, displayName: String, email: String)

    /** Search users by display name (prefix search) — excludes the current user. */
    suspend fun searchUsers(query: String): List<Friend>

    /** Find a user by their unique Friend Code (e.g. "WLEV-A3X9"). */
    suspend fun searchByFriendCode(code: String): Friend?

    /** Find a user by their email address. */
    suspend fun searchByEmail(email: String): Friend?

    /** Send a friend request from the current user to [toUid]. */
    suspend fun sendFriendRequest(toUid: String, toName: String): Result<Unit>

    /** Accept an incoming friend request — updates both users' friendIds. */
    suspend fun acceptFriendRequest(requestId: String, fromUid: String): Result<Unit>

    /** Decline a friend request — marks it declined in Firestore. */
    suspend fun declineFriendRequest(requestId: String): Result<Unit>

    /** Observe the current user's confirmed friends list in real-time. */
    fun observeFriends(): Flow<List<Friend>>

    /** Observe the current user's inbox (friend requests + shared playlists) in real-time. */
    fun observeInbox(): Flow<List<InboxNotification>>

    /** Mark an inbox notification as read. */
    suspend fun markNotificationRead(notifId: String)

    /** Get all pending outgoing friend requests sent by the current user. */
    suspend fun getOutgoingRequests(): List<FriendRequest>

    /** Remove a friend from both users' friend lists. */
    suspend fun removeFriend(friendUid: String): Result<Unit>

    /** Share a single song directly to friends' inboxes. */
    suspend fun shareSong(song: com.waleve.player.domain.model.Song, friendUids: List<String>): Result<Unit>
}
