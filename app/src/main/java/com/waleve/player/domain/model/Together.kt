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
        private val YT_ID = Regex("^[A-Za-z0-9_-]{11}$")

        /** Only https links; nothing on this phone (file://, content://, /data/…) or other schemes. */
        fun safeHttps(u: String?): String? {
            val s = u?.trim() ?: return null
            if (s.length > 2048 || !s.startsWith("https://", ignoreCase = true) || s.any { it.isWhitespace() }) return null
            val host = runCatching { java.net.URI(s).host }.getOrNull() ?: return null
            return s.takeIf { host.isNotBlank() }
        }

        /** Audio files friends send must come from Olive's own sharing storage (Supabase). */
        fun safeFileUrl(u: String?): String? {
            val s = safeHttps(u) ?: return null
            val allowed = runCatching { java.net.URI(com.waleve.player.BuildConfig.SUPABASE_URL).host }.getOrNull()
            val host = runCatching { java.net.URI(s).host }.getOrNull()
            return s.takeIf { allowed != null && host.equals(allowed, ignoreCase = true) && "/storage/v1/object/public/" in s }
        }

        fun safeVideoId(v: String?): String? = v?.trim()?.takeIf { YT_ID.matches(it) }

        /** Data from other people is never trusted: links and ids are checked, text is trimmed. */
        fun fromMap(m: Map<*, *>?): RemoteTrack? {
            if (m == null) return null
            val title = (m["title"] as? String)?.trim()?.take(200)?.takeIf { it.isNotEmpty() } ?: return null
            val videoId = safeVideoId(m["videoId"] as? String)
            val fileUrl = if (videoId == null) safeFileUrl(m["fileUrl"] as? String) else null
            return RemoteTrack(
                key = (m["key"] as? String)?.take(300) ?: title,
                title = title,
                artist = (m["artist"] as? String)?.take(200) ?: "",
                artUrl = safeHttps(m["artUrl"] as? String),
                videoId = videoId,
                fileUrl = fileUrl,
                mediaType = if (m["mediaType"] == "audiobook") "audiobook" else "music",
                durationMs = ((m["durationMs"] as? Number)?.toLong() ?: 0L).coerceIn(0L, 48L * 3_600_000),
                addedBy = (m["addedBy"] as? String)?.take(128) ?: "",
                addedByName = (m["addedByName"] as? String)?.take(60) ?: "",
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
