package com.waleve.player.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.waleve.player.presentation.navigation.BottomNavBar
import com.waleve.player.presentation.navigation.HomeRoute
import com.waleve.player.presentation.navigation.NavGraph
import com.waleve.player.presentation.navigation.NowPlayingRoute
import com.waleve.player.presentation.navigation.bottomNavItems
import com.waleve.player.presentation.player.MiniPlayer
import com.waleve.player.presentation.player.PlayerViewModel
import com.waleve.player.presentation.theme.ThemeViewModel
import com.waleve.player.presentation.theme.WaLeveTheme
import com.waleve.player.presentation.update.AppUpdateViewModel
import com.waleve.player.presentation.update.UpdateDialog

@Composable
fun WaLeveAppContent() {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    @Suppress("UNUSED_VARIABLE") val unusedTheme = themeViewModel
    // Light / Dark / follow the phone (Library → ⚙ → Appearance)
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.remember { com.waleve.player.presentation.theme.AppearancePrefs.init(appCtx.applicationContext) }
    val appearance by themeViewModel.mode.collectAsStateWithLifecycle()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isNightMode = when (appearance) { 1 -> false; 2 -> true; else -> systemDark }

    WaLeveTheme(isNightMode = isNightMode) {
      // The playing song's cover becomes the blurred backdrop behind all the glass
      val bgPlayer: PlayerViewModel = hiltViewModel()
      val bgState by bgPlayer.playerState.collectAsStateWithLifecycle()
      com.waleve.player.presentation.components.GlassBackground(artwork = bgState.shownSong?.albumArtUri) {
        val navController = rememberNavController()
        // "Share → Olive": jump to the Download page
        val sharedLink by com.waleve.player.data.download.SharedLinkInbox.pending.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(sharedLink) {
            if (sharedLink != null) runCatching {
                navController.navigate(com.waleve.player.presentation.navigation.DownloadRoute) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        val playerViewModel: PlayerViewModel = hiltViewModel()
        val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
        val sleepTimerRemaining by playerViewModel.sleepTimerRemaining.collectAsStateWithLifecycle()

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        // Highlight the tab the current screen belongs to (Profile opened from Home → Home stays lit)
        val backStack by navController.currentBackStack.collectAsStateWithLifecycle()
        val currentRoute: Any? = backStack.asReversed().firstNotNullOfOrNull { entry ->
            bottomNavItems.firstOrNull { item -> entry.destination.hasRoute(item.route::class) }?.route
        }
        // Is the current screen exactly a tab root (not Profile / Album / … on top of it)?
        val atTabRoot = bottomNavItems.any { currentDestination?.hasRoute(it.route::class) == true }
        val showBottomBar = currentDestination?.hasRoute(NowPlayingRoute::class) != true

        val badgeVm: com.waleve.player.presentation.friends.SocialBadgeViewModel = hiltViewModel()
        val friendsBadge by badgeVm.badge.collectAsStateWithLifecycle()

        val updateViewModel: AppUpdateViewModel = hiltViewModel()
        val updateInfo by updateViewModel.updateInfo.collectAsStateWithLifecycle()
        
        // Playback problems (e.g. a stream YouTube refused) as a short toast
        val appContext = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            playerViewModel.playbackController.messages.collect { android.widget.Toast.makeText(appContext, it, android.widget.Toast.LENGTH_LONG).show() }
        }

        // Check for a new release at start and whenever Olive comes back (at most every 6 h)
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) updateViewModel.checkForUpdates()
            }
            lifecycleOwner.lifecycle.addObserver(obs)
            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
        }

        updateInfo?.let { info ->
            UpdateDialog(
                updateInfo = info,
                updateChecker = updateViewModel.updateChecker,
                onDismiss = updateViewModel::dismissUpdate,
                onLater = updateViewModel::later,
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            // Screens handle the status bar themselves; don't add it twice (that was the big empty gap on top)
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        var togetherSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                        com.waleve.player.presentation.together.TogetherBanner(onClick = { togetherSheet = true })
                        if (togetherSheet) com.waleve.player.presentation.together.ListenTogetherSheet(onDismiss = { togetherSheet = false })
                        MiniPlayer(
                            playerState = playerState,
                            onPlayPause = playerViewModel::playPause,
                            onNext = playerViewModel::next,
                            onClick = { navController.navigate(NowPlayingRoute) },
                            isNightMode = isNightMode,
                            sleepTimerRemaining = sleepTimerRemaining,
                        )
                        BottomNavBar(
                            currentRoute = currentRoute,
                            isNightMode = isNightMode,
                            badges = mapOf("Friends" to friendsBadge),
                            onNavigate = { route ->
                                val sameTab = currentRoute != null && currentRoute::class == route::class
                                when {
                                    // Tapping the tab you're in: go back to its first screen
                                    sameTab -> if (!atTabRoot) navController.popBackStack(route, inclusive = false)
                                    // Home is the root of everything: just pop back to it
                                    route is HomeRoute -> {
                                        if (!navController.popBackStack(HomeRoute, inclusive = false)) {
                                            navController.navigate(HomeRoute) { launchSingleTop = true }
                                        }
                                    }
                                    else -> navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        )
                    }
                }
            },
        ) { paddingValues ->
            NavGraph(
                navController = navController,
                playerViewModel = playerViewModel,
                isNightMode = isNightMode,
                onThemeToggle = themeViewModel::toggleTheme,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            )
        }
    }
    }
}
