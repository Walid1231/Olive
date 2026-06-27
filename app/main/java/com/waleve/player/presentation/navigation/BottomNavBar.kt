package com.waleve.player.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Search
import com.waleve.player.presentation.theme.GhibliLeaf
import com.waleve.player.presentation.theme.GhibliMist
import com.waleve.player.presentation.theme.GhibliNightCard
import com.waleve.player.presentation.theme.GhibliNightMid
import com.waleve.player.presentation.theme.GhibliDaySurface

import androidx.compose.material.icons.rounded.Group

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
)

val bottomNavItems = listOf(
    BottomNavItem("Home",     Icons.Rounded.Home,           Icons.Rounded.Home,           HomeRoute),
    BottomNavItem("Search",   Icons.Rounded.Search,         Icons.Rounded.Search,         SearchRoute),
    BottomNavItem("Library",  Icons.Rounded.LibraryMusic,   Icons.Rounded.LibraryMusic,   LibraryRoute),
    BottomNavItem("Download", Icons.Rounded.CloudDownload,  Icons.Rounded.CloudDownload,  DownloadRoute),
    BottomNavItem("Friends",  Icons.Rounded.Group,          Icons.Rounded.Group,          FriendsRoute),
)


@Composable
fun BottomNavBar(
    currentRoute: Any?,
    onNavigate: (Any) -> Unit,
    isNightMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val navBg by animateColorAsState(
        targetValue = if (isNightMode) GhibliNightMid else GhibliDaySurface,
        animationSpec = tween(1200),
        label = "navBg",
    )
    val selectedColor by animateColorAsState(
        targetValue = if (isNightMode) GhibliLeaf else Color(0xFF2D6A4F),
        animationSpec = tween(800),
        label = "navSelected",
    )

    NavigationBar(
        modifier = modifier,
        containerColor = navBg,
        contentColor = selectedColor,
        tonalElevation = 0.dp,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute?.let { it::class == item.route::class } ?: false
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.18f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "tab_scale_${item.label}",
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp).scale(scale),
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = selectedColor,
                    selectedTextColor   = selectedColor,
                    unselectedIconColor = GhibliMist,
                    unselectedTextColor = GhibliMist,
                    indicatorColor      = Color.Transparent,
                ),
            )
        }
    }
}
