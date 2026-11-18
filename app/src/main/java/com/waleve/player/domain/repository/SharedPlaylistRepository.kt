package com.waleve.player.domain.repository

import com.waleve.player.domain.model.SharedPlaylist
import com.waleve.player.domain.model.Song

/**
 * Repository interface for sharing playlists via Firebase.
 */
interface SharedPlaylistRepository {

    /**
     * Publish a playlist to Firebase and notify the given list of friends.
     * Local-only songs (no videoId) are included with isLocalOnly=true so
     * the recipient can see them but knows they can't download them.
     *
     * @return the generated shareId on success.
     */
    suspend fun sharePlaylist(
        playlistName: String,
        playlistDescription: String?,
        songs: List<Song>,
        friendUids: List<String>,
    ): Result<String>

    /**
     * Fetch a shared playlist by its shareId (called when recipient opens inbox).
     */
    suspend fun getSharedPlaylist(shareId: String): Result<SharedPlaylist>

    /**
     * Accept/import a shared playlist — saves it to the user's local Room DB
     * as a new playlist with all remote tracks marked as stream-only.
     * Returns the new local playlist ID.
     */
    suspend fun importSharedPlaylist(shareId: String): Result<Long>
}
