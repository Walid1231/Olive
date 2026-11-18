package com.waleve.player.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassMuted

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
)

val bottomNavItems = listOf(
    BottomNavItem("Home",     Icons.Rounded.Home,          Icons.Rounded.Home,          HomeRoute),
    BottomNavItem("Search",   Icons.Rounded.Search,        Icons.Rounded.Search,        SearchRoute),
    BottomNavItem("Library",  Icons.Rounded.LibraryMusic,  Icons.Rounded.LibraryMusic,  LibraryRoute),
    BottomNavItem("Download", Icons.Rounded.CloudDownload, Icons.Rounded.CloudDownload, DownloadRoute),
    BottomNavItem("Friends",  Icons.Rounded.Group,         Icons.Rounded.Group,         FriendsRoute),
)

/** Floating light-glass navigation pill (Figma "Light/Bottom nav"). [badges] = unread counts per label. */
@Composable
fun BottomNavBar(
    currentRoute: Any?,
    onNavigate: (Any) -> Unit,
    isNightMode: Boolean = false,
    modifier: Modifier = Modifier,
    badges: Map<String, Int> = emptyMap(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
            .glass(RoundedCornerShape(34.dp), alpha = 0.8f, elevation = 10.dp)
            .height(68.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute?.let { it::class == item.route::class } ?: false
            val tint by animateColorAsState(if (selected) GlassAccent else GlassMuted, tween(200), label = "navTint")
            val pill by animateColorAsState(if (selected) GlassAccent.copy(alpha = 0.14f) else Color.Transparent, tween(200), label = "navPill")
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onNavigate(item.route) }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(pill).padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Icon(item.selectedIcon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    val count = badges[item.label] ?: 0
                    if (count > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = (-2).dp)
                                .widthIn(min = 18.dp).height(18.dp).clip(CircleShape).background(GlassDanger)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.label,
                    color = tint,
                    fontSize = 10.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
