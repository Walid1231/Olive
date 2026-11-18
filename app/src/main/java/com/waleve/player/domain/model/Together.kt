package com.waleve.player.domain.model

/**
 * A song / chapter in a form every friend's phone can play:
 * a YouTube id (streamed) or a public file URL (Supabase upload). Local-only tracks carry
 * neither and are shown as metadata only.
 */
data class RemoteTrack(
    val key: String,
    val title: String,
    val artist: String,
    val artUrl: String? = null,
    val videoId: String? = null,
    val fileUrl: String? = null,
    val mediaType: String = "music",
    val durationMs: Long = 0,
    val addedBy: String = "",
    val addedByName: String = "",
    val addedAt: Long = 0,
) {
    val playable: Boolean get() = videoId != null || fileUrl != null
    val isAudiobook: Boolean get() = mediaType == "audiobook"

    fun toMap(): Map<String, Any> = buildMap {
        put("key", key); put("title", title); put("artist", artist)
        put("mediaType", mediaType); put("durationMs", durationMs)
        artUrl?.let { put("artUrl", it) }
        videoId?.let { put("videoId", it) }
        fileUrl?.let { put("fileUrl", it) }
        if (addedBy.isNotEmpty()) { put("addedBy", addedBy); put("addedByName", addedByName); put("addedAt", addedAt) }
    }

    companion object {
        fun fromMap(m: Map<*, *>?): RemoteTrack? {
            if (m == null) return null
            val title = m["title"] as? String ?: return null
            return RemoteTrack(
                key = m["key"] as? String ?: title,
                title = title,
                artist = m["artist"] as? String ?: "",
                artUrl = (m["artUrl"] as? String)?.takeIf { it.isNotBlank() },
                videoId = (m["videoId"] as? String)?.takeIf { it.isNotBlank() },
                fileUrl = (m["fileUrl"] as? String)?.takeIf { it.isNotBlank() },
                mediaType = m["mediaType"] as? String ?: "music",
                durationMs = (m["durationMs"] as? Number)?.toLong() ?: 0L,
                addedBy = m["addedBy"] as? String ?: "",
                addedByName = m["addedByName"] as? String ?: "",
                addedAt = (m["addedAt"] as? Number)?.toLong() ?: 0L,
            )
        }
    }
}

/** What a friend is listening to right now (published by their phone). */
data class NowPlaying(val track: RemoteTrack, val isPlaying: Boolean, val updatedAt: Long)

/** A Listen Together session. The doc id is the host's uid. */
data class ListenSession(
    val hostUid: String,
    val hostName: String,
    val hostAvatar: String? = null,
    val active: Boolean = true,
    val invited: List<String> = emptyList(),
    val members: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val track: RemoteTrack? = null,
    val preparing: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val updatedAt: Long = 0,
    /** Guests may play / pause / seek / skip and pick songs for everyone. */
    val openControl: Boolean = false,
    val startedAt: Long = 0,
)

/** A playlist several friends edit together. */
data class CollabPlaylist(
    val id: String,
    val name: String,
    val ownerId: String,
    val ownerName: String,
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val tracks: List<RemoteTrack> = emptyList(),
    val updatedAt: Long = 0,
)

/** One chat line inside a Listen Together session. Kept on each phone (Firestore only relays it). */
data class ChatMessage(val id: String, val uid: String, val name: String, val text: String, val at: Long)

/** A finished (or running) session's chat, saved on this phone. */
data class ChatLog(val key: String, val hostName: String, val startedAt: Long, val messages: List<ChatMessage>)
