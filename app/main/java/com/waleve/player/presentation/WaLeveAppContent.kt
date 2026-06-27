package com.waleve.player.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    val isNightMode by themeViewModel.isNightMode.collectAsStateWithLifecycle()

    WaLeveTheme(isNightMode = isNightMode) {
        val navController = rememberNavController()
        val playerViewModel: PlayerViewModel = hiltViewModel()
        val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
        val sleepTimerRemaining by playerViewModel.sleepTimerRemaining.collectAsStateWithLifecycle()

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val currentRoute: Any? = bottomNavItems.firstOrNull { item ->
            currentDestination?.hasRoute(item.route::class) == true
        }?.route
        val showBottomBar = currentDestination?.hasRoute(NowPlayingRoute::class) != true

        val updateViewModel: AppUpdateViewModel = hiltViewModel()
        val updateInfo by updateViewModel.updateInfo.collectAsStateWithLifecycle()
        
        LaunchedEffect(Unit) {
            updateViewModel.checkForUpdates()
        }

        updateInfo?.let { info ->
            UpdateDialog(
                updateInfo = info,
                updateChecker = updateViewModel.updateChecker,
                onDismiss = updateViewModel::dismissUpdate
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
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
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
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
