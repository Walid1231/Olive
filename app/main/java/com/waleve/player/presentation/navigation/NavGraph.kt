package com.waleve.player.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.waleve.player.domain.model.Song
import com.waleve.player.presentation.friends.FriendsScreen
import com.waleve.player.presentation.friends.FriendsViewModel
import com.waleve.player.presentation.friends.SharedPlaylistScreen
import com.waleve.player.presentation.friends.SharedPlaylistViewModel
import com.waleve.player.presentation.home.HomeScreen
import com.waleve.player.presentation.home.HomeViewModel
import com.waleve.player.presentation.library.AlbumDetailScreen
import com.waleve.player.presentation.library.ArtistDetailScreen
import com.waleve.player.presentation.library.FolderSongsScreen
import com.waleve.player.presentation.library.GenreSongsScreen
import com.waleve.player.presentation.library.LibraryScreen
import com.waleve.player.presentation.library.LibraryViewModel
import com.waleve.player.presentation.player.NowPlayingScreen
import com.waleve.player.presentation.player.PlayerViewModel
import com.waleve.player.presentation.playlist.FavoritesScreen
import com.waleve.player.presentation.playlist.PlaylistDetailScreen
import com.waleve.player.presentation.playlist.PlaylistListScreen
import com.waleve.player.presentation.playlist.PlaylistViewModel
import com.waleve.player.presentation.download.DownloadScreen
import com.waleve.player.presentation.download.DownloadViewModel
import com.waleve.player.presentation.search.SearchScreen
import com.waleve.player.presentation.search.SearchViewModel
import kotlinx.coroutines.launch



@Composable
fun NavGraph(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    isNightMode: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSongClick: (List<Song>, Int) -> Unit = { songs, index ->
        playerViewModel.playSong(songs, index)
        if (songs.isNotEmpty()) playerViewModel.onSongPlayed(songs[index].id)
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                isNightMode = isNightMode,
                onThemeToggle = onThemeToggle,
                onSongClick = onSongClick,
                onSearchClick = { navController.navigate(LibraryRoute) },
            )
        }

        composable<LibraryRoute> {
            val viewModel: LibraryViewModel = hiltViewModel()
            val playlistVm: PlaylistViewModel = hiltViewModel()
            val currentPlayerState by playerViewModel.playerState.collectAsStateWithLifecycle()
            LibraryScreen(
                viewModel = viewModel,
                onSongClick = onSongClick,
                onAlbumClick  = { album, artist -> navController.navigate(AlbumDetailRoute(album, artist)) },
                onArtistClick = { navController.navigate(ArtistDetailRoute(it)) },
                onGenreClick  = { navController.navigate(GenreSongsRoute(it)) },
                onFolderClick = { navController.navigate(FolderSongsRoute(it)) },
                onFavoritesClick  = { navController.navigate(FavoritesRoute) },
                onPlaylistsClick  = { navController.navigate(PlaylistListRoute) },
                isNightMode = isNightMode,
                currentPlayingSongId = currentPlayerState.currentSong?.id,
                playlistViewModel = playlistVm,
            )
        }

        composable<NowPlayingRoute> {
            val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
            val sleepTimerRemaining by playerViewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
            val playlistVm: PlaylistViewModel = hiltViewModel()

            NowPlayingScreen(
                playerState         = playerState,
                onBack              = { navController.popBackStack() },
                onPlayPause         = playerViewModel::playPause,
                onNext              = playerViewModel::next,
                onPrevious          = playerViewModel::previous,
                onSeek              = playerViewModel::seekTo,
                onToggleShuffle     = playerViewModel::toggleShuffle,
                onCycleRepeat       = playerViewModel::cycleRepeatMode,
                onSetSpeed          = playerViewModel::setPlaybackSpeed,
                onSetSleepTimer     = playerViewModel::setSleepTimer,
                onToggleFavorite    = {
                    playerState.currentSong?.let { playerViewModel.toggleFavorite(it.id) }
                },
                onAddToPlaylist     = {},
                isFavorite          = playerState.currentSong?.isFavorite == true,
                isNightMode         = isNightMode,
                sleepTimerRemaining = sleepTimerRemaining,
                playlistViewModel   = playlistVm,
            )
        }

        composable<DownloadRoute> {
            val viewModel: DownloadViewModel = hiltViewModel()
            DownloadScreen(viewModel = viewModel)
        }

        composable<SearchRoute> {
            val viewModel: SearchViewModel = hiltViewModel()
            SearchScreen(
                viewModel = viewModel,
                onSongClick = onSongClick
            )
        }


        composable<FavoritesRoute> {
            val viewModel: PlaylistViewModel = hiltViewModel()
            FavoritesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSongClick = onSongClick,
            )
        }

        composable<PlaylistListRoute> {
            val viewModel: PlaylistViewModel = hiltViewModel()
            PlaylistListScreen(
                viewModel = viewModel,
                onPlaylistClick = { navController.navigate(PlaylistDetailRoute(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<PlaylistDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlaylistDetailRoute>()
            val viewModel: PlaylistViewModel = hiltViewModel()
            PlaylistDetailScreen(
                playlistId = route.playlistId,
                viewModel  = viewModel,
                onBack     = { navController.popBackStack() },
                onSongClick = onSongClick,
            )
        }

        composable<AlbumDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AlbumDetailRoute>()
            AlbumDetailScreen(
                albumName  = route.albumName,
                artistName = route.artistName,
                onBack     = { navController.popBackStack() },
                onSongClick = onSongClick,
            )
        }

        composable<ArtistDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ArtistDetailRoute>()
            ArtistDetailScreen(
                artistName = route.artistName,
                onBack     = { navController.popBackStack() },
                onSongClick = onSongClick,
            )
        }

        composable<GenreSongsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GenreSongsRoute>()
            GenreSongsScreen(
                genreName = route.genreName,
                onBack    = { navController.popBackStack() },
                onSongClick = onSongClick,
            )
        }

        composable<FolderSongsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FolderSongsRoute>()
            FolderSongsScreen(
                folderPath = route.folderPath,
                onBack     = { navController.popBackStack() },
                onSongClick = onSongClick,
            )
        }

        composable<FriendsRoute> {
            val viewModel: FriendsViewModel = hiltViewModel()
            val authVm: com.waleve.player.presentation.auth.AuthViewModel = hiltViewModel()
            val profile by authVm.profile.collectAsStateWithLifecycle()

            if (profile == null && !authVm.isSignedIn) {
                // User is not signed in — show auth screen
                com.waleve.player.presentation.auth.AuthScreen(
                    viewModel = authVm,
                    isNightMode = isNightMode,
                    onSignInSuccess = {
                        viewModel.refreshAfterSignIn()
                    },
                )
            } else {
                val playScope = rememberCoroutineScope()
                FriendsScreen(
                    viewModel          = viewModel,
                    isNightMode        = isNightMode,
                    onOpenSharedPlaylist = { shareId ->
                        navController.navigate(SharedPlaylistRoute(shareId))
                    },
                    onPlaySharedSong = { notif ->
                        playScope.launch {
                            val song = viewModel.playSharedSong(notif)
                            if (song != null) {
                                playerViewModel.playSong(listOf(song), 0)
                            }
                        }
                    },
                    onOpenProfile = {
                        navController.navigate(ProfileRoute)
                    },
                )
            }
        }

        composable<ProfileRoute> {
            val viewModel: com.waleve.player.presentation.profile.ProfileViewModel = hiltViewModel()
            com.waleve.player.presentation.profile.ProfileScreen(
                viewModel   = viewModel,
                isNightMode = isNightMode,
                onBack      = { navController.popBackStack() },
            )
        }

        composable<SharedPlaylistRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SharedPlaylistRoute>()
            val viewModel: SharedPlaylistViewModel = hiltViewModel()
            SharedPlaylistScreen(
                shareId     = route.shareId,
                viewModel   = viewModel,
                isNightMode = isNightMode,
                onBack      = { navController.popBackStack() },
                onImported  = { localPlaylistId ->
                    navController.popBackStack()
                    navController.navigate(PlaylistDetailRoute(localPlaylistId))
                },
            )
        }

    }
}

