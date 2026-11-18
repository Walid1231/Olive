package com.waleve.player.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waleve.player.presentation.components.GlassGradientH
import com.waleve.player.presentation.components.springClick
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
)

val bottomNavItems = listOf(
    BottomNavItem("Home",     Icons.Rounded.Home,          Icons.Outlined.Home,          HomeRoute),
    BottomNavItem("Search",   Icons.Rounded.Search,        Icons.Rounded.Search,         SearchRoute),
    BottomNavItem("Library",  Icons.Rounded.LibraryMusic,  Icons.Outlined.LibraryMusic,  LibraryRoute),
    BottomNavItem("Download", Icons.Rounded.CloudDownload, Icons.Outlined.CloudDownload, DownloadRoute),
    BottomNavItem("Friends",  Icons.Rounded.Group,         Icons.Outlined.Group,         FriendsRoute),
)

/**
 * Floating glass dock. The selected tab grows into a gradient capsule with its label;
 * the others stay as calm outline icons. Spring-animated, haptic tap, unread badges.
 * [badges] = unread counts per label.
 */
@Composable
fun BottomNavBar(
    currentRoute: Any?,
    onNavigate: (Any) -> Unit,
    isNightMode: Boolean = false,
    modifier: Modifier = Modifier,
    badges: Map<String, Int> = emptyMap(),
) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
            .shadow(18.dp, shape, ambientColor = Color(0x2223305A), spotColor = Color(0x2223305A))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.97f), Color(0xFFF7F6FB).copy(alpha = 0.95f))))
            .border(1.dp, Color.White, shape)
            .height(64.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute?.let { it::class == item.route::class } ?: false
            val tint by animateColorAsState(if (selected) Color.White else GlassInk.copy(alpha = 0.45f), tween(220), label = "navTint")
            val count = badges[item.label] ?: 0
            Box(
                modifier = Modifier
                    .springClick(pressedScale = 0.88f) { onNavigate(item.route) }
                    .clip(RoundedCornerShape(22.dp))
                    .then(if (selected) Modifier.background(GlassGradientH) else Modifier)
                    .padding(horizontal = if (selected) 14.dp else 11.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Icon(
                            if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label, tint = tint, modifier = Modifier.size(23.dp),
                        )
                        if (count > 0) {
                            // Red badge with a white ring: readable on the white dock and on the green pill
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-8).dp)
                                    .widthIn(min = 18.dp).height(18.dp)
                                    .shadow(3.dp, CircleShape, ambientColor = GlassDanger, spotColor = GlassDanger)
                                    .clip(CircleShape).background(Color.White).padding(1.5.dp)
                                    .clip(CircleShape).background(GlassDanger)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 10.sp)
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = selected,
                        enter = expandHorizontally(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
                        exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(120)),
                    ) {
                        Row {
                            Spacer(Modifier.width(if (count > 0) 12.dp else 7.dp))
                            Text(item.label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
