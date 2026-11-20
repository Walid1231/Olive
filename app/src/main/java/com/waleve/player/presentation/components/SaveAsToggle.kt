package com.waleve.player.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassSub

/**
 * Where a download goes: Songs or Audiobooks. One glass box, a gradient pill slides under the
 * choice; each side shows an icon, a short name and what it means (never cut off).
 */
@Composable
fun SaveAsToggle(
    asAudiobook: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hf = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), alpha = 0.7f, elevation = 0.dp).padding(5.dp)) {
        val half = maxWidth / 2
        val x by animateDpAsState(if (asAudiobook) half else 0.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "saveX")
        Box(
            Modifier.offset(x = x).width(half).height(62.dp)
                .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = GlassAccent, spotColor = GlassAccent)
                .clip(RoundedCornerShape(20.dp)).background(GlassGradientH)
        )
        Row(Modifier.fillMaxWidth().height(62.dp)) {
            listOf(
                Triple(false, Icons.Rounded.MusicNote, "Song" to "Music library"),
                Triple(true, Icons.AutoMirrored.Rounded.MenuBook, "Audiobook" to "Resumes where you stop"),
            ).forEach { (book, icon, text) ->
                val on = book == asAudiobook
                val fg by animateColorAsState(if (on) Color.White else GlassInk, tween(220), label = "saveFg")
                val sub by animateColorAsState(if (on) Color.White.copy(alpha = 0.85f) else GlassSub, tween(220), label = "saveSub")
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !on) {
                            hf.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(book)
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(if (on) Color.White.copy(alpha = 0.22f) else GlassAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = if (on) Color.White else GlassAccent, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        FitText(text.first, color = fg, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                        Text(text.second, color = sub, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
