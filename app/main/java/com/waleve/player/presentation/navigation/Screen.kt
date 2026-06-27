package com.waleve.player.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object LibraryRoute
@Serializable object NowPlayingRoute
@Serializable object DownloadRoute
@Serializable object FavoritesRoute
@Serializable object PlaylistListRoute
@Serializable data class AlbumDetailRoute(val albumName: String, val artistName: String)
@Serializable data class ArtistDetailRoute(val artistName: String)
@Serializable data class GenreSongsRoute(val genreName: String)
@Serializable data class FolderSongsRoute(val folderPath: String)
@Serializable data class PlaylistDetailRoute(val playlistId: Long)
@Serializable object SearchRoute
@Serializable object FriendsRoute
@Serializable data class SharedPlaylistRoute(val shareId: String)
@Serializable object AuthRoute
@Serializable object ProfileRoute

