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
        // Smooth, light transitions between tabs and screens
        enterTransition = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 40)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(260, delayMillis = 40), initialScale = 0.97f)
        },
        exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) },
        popEnterTransition = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 40)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(260, delayMillis = 40), initialScale = 1.03f)
        },
        popExitTransition = {
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) +
                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(160), targetScale = 0.97f)
        },
    ) {
        composable<HomeRoute> {
            val viewModel: HomeViewModel = hiltViewModel()
            val badgeVm: com.waleve.player.presentation.friends.SocialBadgeViewModel = hiltViewModel()
            val homeBadge by badgeVm.badge.collectAsStateWithLifecycle()
            val homePlayer by playerViewModel.playerState.collectAsStateWithLifecycle()
            HomeScreen(
                viewModel = viewModel,
                isNightMode = isNightMode,
                onThemeToggle = onThemeToggle,
                onSongClick = onSongClick,
                onSearchClick = { navController.navigate(LibraryRoute) },
                onSearchOnline = { navController.navigate(SearchRoute) { launchSingleTop = true } },
                onOpenDownloads = { navController.navigate(DownloadRoute) { launchSingleTop = true } },
                onOpenLibrary = { tab ->
                    com.waleve.player.presentation.LibraryTabRequest.tab.value = tab
                    navController.navigate(LibraryRoute) { launchSingleTop = true }
                },
                onOpenFriends = { navController.navigate(FriendsRoute) { launchSingleTop = true } },
                onOpenProfile = { navController.navigate(ProfileRoute) { launchSingleTop = true } },
                notificationCount = homeBadge,
                currentPlayingSongId = homePlayer.currentSong?.id,
            )
        }

        composable<LibraryRoute> {
            val viewModel: LibraryViewModel = hiltViewModel()
            val playlistVm: PlaylistViewModel = hiltViewModel()
            val currentPlayerState by playerViewModel.playerState.collectAsStateWithLifecycle()
            val requestedTab by com.waleve.player.presentation.LibraryTabRequest.tab.collectAsStateWithLifecycle()
            LaunchedEffect(requestedTab) {
                requestedTab?.let { viewModel.selectTab(it); com.waleve.player.presentation.LibraryTabRequest.tab.value = null }
            }
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
                onSongClick = onSongClick,
                onOpenCollection = { item ->
                    item.browseId?.let { navController.navigate(YtCollectionRoute(it, item.title, item.thumb)) }
                },
            )
        }

        composable<YtCollectionRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<YtCollectionRoute>()
            com.waleve.player.presentation.search.YtCollectionScreen(
                browseId = route.browseId, title = route.title, thumb = route.thumb,
                onBack = { navController.popBackStack() },
                onSongClick = onSongClick,
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
                        // First stop after signing in: set up your profile (name, photo, bio)
                        navController.navigate(ProfileRoute)
                    },
                )
            } else {
                val playScope = rememberCoroutineScope()
                FriendsScreen(
                    viewModel          = viewModel,
                    isNightMode        = isNightMode,
                    myProfile          = profile,
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
                    onOpenGroupPlaylists = { navController.navigate(GroupPlaylistsRoute) },
                    onOpenGroupPlaylist = { id -> navController.navigate(GroupPlaylistRoute(id)) },
                )
            }
        }

        composable<GroupPlaylistsRoute> {
            com.waleve.player.presentation.together.GroupPlaylistsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(GroupPlaylistRoute(id)) },
            )
        }

        composable<GroupPlaylistRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GroupPlaylistRoute>()
            com.waleve.player.presentation.together.GroupPlaylistDetailScreen(
                id = route.id,
                onBack = { navController.popBackStack() },
            )
        }

        composable<ProfileRoute> {
            val viewModel: com.waleve.player.presentation.profile.ProfileViewModel = hiltViewModel()
            val authVm: com.waleve.player.presentation.auth.AuthViewModel = hiltViewModel()
            val prof by viewModel.profile.collectAsStateWithLifecycle()
            if (prof == null && !authVm.isSignedIn) {
                // Not signed in yet → sign in first, then the profile appears here
                com.waleve.player.presentation.auth.AuthScreen(viewModel = authVm, isNightMode = isNightMode, onSignInSuccess = {})
            } else {
                com.waleve.player.presentation.profile.ProfileScreen(
                    viewModel   = viewModel,
                    isNightMode = isNightMode,
                    onBack      = { navController.popBackStack() },
                )
            }
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

