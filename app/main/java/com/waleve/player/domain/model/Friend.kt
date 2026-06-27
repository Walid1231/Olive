package com.waleve.player.domain.model

/**
 * Represents a user who is already a confirmed friend.
 */
data class Friend(
    val uid: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null,
    val friendCode: String = "",
)

/**
 * Represents the current user's full profile.
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null,
    val friendCode: String = "",      // unique code, e.g. "WLEV-A3X9"
    val bio: String? = null,
    val favGenre: String? = null,
    val friendCount: Int = 0,
    val joinedAt: Long = System.currentTimeMillis(),
)


/**
 * Status of a friend request.
 */
enum class FriendRequestStatus { PENDING, ACCEPTED, DECLINED }

/**
 * A friend request between two users.
 */
data class FriendRequest(
    val requestId: String = "",
    val fromUid: String,
    val fromName: String,
    val toUid: String,
    val toName: String,
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val sentAt: Long = System.currentTimeMillis(),
)

/**
 * A single track inside a shared playlist. Only carries metadata —
 * local-only songs are represented with videoId = null.
 */
data class SharedTrack(
    val videoId: String?,          // null = local-only, cannot be re-downloaded
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0,
    val isLocalOnly: Boolean = false,
)

/**
 * A playlist that has been shared on Firebase and can be imported by a friend.
 */
data class SharedPlaylist(
    val shareId: String = "",
    val name: String,
    val description: String? = null,
    val ownerId: String,
    val ownerName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<SharedTrack> = emptyList(),
)

/**
 * An item in a user's notification inbox.
 */
data class InboxNotification(
    val notifId: String = "",
    val type: InboxNotificationType,
    val fromUid: String,
    val fromName: String,
    val shareId: String? = null,          // set for PLAYLIST_SHARED
    val playlistName: String? = null,     // set for PLAYLIST_SHARED
    val songTitle: String? = null,        // set for SONG_SHARED
    val thumbnailUrl: String? = null,     // set for SONG_SHARED
    val videoId: String? = null,          // set for SONG_SHARED
    val requestId: String? = null,        // set for FRIEND_REQUEST
    val sentAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
)

enum class InboxNotificationType { FRIEND_REQUEST, PLAYLIST_SHARED, SONG_SHARED }
