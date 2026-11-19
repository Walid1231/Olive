package com.waleve.player.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub

/**
 * One place to choose a download: Audio | Video switch, where to save it, a big "recommended"
 * card and the rest as a compact two-column grid (less scrolling, nothing cut off).
 * Used by the Download page and the download sheet in Search.
 */
@Composable
fun FormatPicker(
    audio: List<MediaFormat>,
    video: List<MediaFormat>,
    asAudiobook: Boolean,
    onAsAudiobook: (Boolean) -> Unit,
    onPick: (format: MediaFormat, asAudiobook: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    advanced: List<MediaFormat> = emptyList(),
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth()) {
        if (video.isNotEmpty()) {
            GlassSegmented(
                options = listOf(
                    SegmentOption("Audio", Icons.Rounded.GraphicEq, audio.size),
                    SegmentOption("Video", Icons.Rounded.Videocam, video.size),
                ),
                selected = tab, onSelect = { tab = it }, height = 46.dp,
            )
            Spacer(Modifier.height(16.dp))
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "formatTab",
        ) { t ->
            Column {
                if (t == 0) {
                    PickerLabel("Save to")
                    SaveAsToggle(asAudiobook = asAudiobook, onChange = onAsAudiobook)
                    Spacer(Modifier.height(16.dp))
                    audio.firstOrNull()?.let { best ->
                        PickerLabel("Recommended")
                        HeroFormatCard(best) { onPick(best, asAudiobook) }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (audio.size > 1) {
                        PickerLabel("Other formats")
                        FormatGrid(audio.drop(1)) { onPick(it, asAudiobook) }
                    }
                    InfoNote("YouTube audio is about 128–160 kbps. M4A and Opus keep it exactly as it is. MP3, FLAC and WAV convert it on your phone, so they take longer and are bigger, not better.")
                } else {
                    val best = video.firstOrNull { it.height == 720 } ?: video.first()
                    PickerLabel("Recommended")
                    HeroFormatCard(best) { onPick(best, false) }
                    Spacer(Modifier.height(16.dp))
                    if (video.size > 1) {
                        PickerLabel("Other sizes")
                        FormatGrid(video.filter { it !== best }) { onPick(it, false) }
                    }
                    InfoNote("Videos are saved to Movies/Olive. Bigger sizes take longer to download and join.")
                }
            }
        }

        if (advanced.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.springClick { open = !open }.fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp), alpha = 0.5f)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.HighQuality, null, tint = GlassAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("All original formats", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${advanced.size} streams straight from the site", color = GlassSub, fontSize = 11.5.sp)
                }
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = GlassAccent)
            }
            AnimatedVisibility(open, enter = expandVertically(tween(300)) + fadeIn(), exit = shrinkVertically(tween(250)) + fadeOut()) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    advanced.forEach { f -> CompactFormatRow(f) { onPick(f, asAudiobook && f.isAudioOnly) } }
                }
            }
        }
    }
}

// ── pieces ───────────────────────────────────────────────────────────────────

private data class FormatLook(val badge: String, val title: String, val subtitle: String, val color: Color, val icon: ImageVector)

private fun lookOf(f: MediaFormat): FormatLook {
    if (!f.isAudioOnly) {
        val h = f.height
        val title = when { h >= 2160 -> "4K"; h >= 1440 -> "2K"; h > 0 -> "${h}p"; else -> f.quality.ifBlank { "Video" } }
        val sub = when { h >= 2160 -> "Ultra HD"; h >= 1440 -> "Quad HD"; h >= 1080 -> "Full HD"; h >= 720 -> "HD"; h > 0 -> "Standard"; else -> f.label }
        return FormatLook("MP4", title, sub, Color(0xFF5B9BE6), Icons.Rounded.Videocam)
    }
    val ext = f.extension.lowercase()
    val color = when (ext) {
        "m4a", "aac" -> GlassAccent
        "opus", "webm" -> Color(0xFF26A69A)
        "mp3" -> Color(0xFFE5737A)
        "flac", "wav", "alac" -> Color(0xFFF0A23B)
        "ogg" -> Color(0xFF9575CD)
        else -> GlassAccent
    }
    val parts = f.label.split(" · ", limit = 2)
    val title = parts[0].ifBlank { ext.uppercase() }
    val subtitle = parts.getOrNull(1)?.replaceFirstChar { it.uppercase() } ?: when {
        f.bitrate >= 900 -> "Lossless"
        f.bitrate > 0 -> "${f.bitrate} kbps"
        else -> f.quality
    }
    val icon = if (ext in setOf("flac", "wav", "alac")) Icons.Rounded.HighQuality else Icons.Rounded.MusicNote
    return FormatLook(if (ext == "webm") "OPUS" else ext.uppercase(), title, subtitle, color, icon)
}

private fun sizeText(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1_048_576.0
    return when {
        mb >= 1000 -> "%.1f GB".format(mb / 1024)
        mb >= 10 -> "%.0f MB".format(mb)
        else -> "%.1f MB".format(mb)
    }
}

@Composable
private fun PickerLabel(text: String) {
    Text(
        text.uppercase(), color = GlassSub, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
    )
}

/** The big card for the best choice: gradient edge, badge, size and a clear Download button. */
@Composable
private fun HeroFormatCard(f: MediaFormat, onClick: () -> Unit) {
    val look = lookOf(f)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.springClick(onClick = onClick).fillMaxWidth()
            .glass(shape, alpha = 0.8f, elevation = 6.dp, border = false)
            .border(1.5.dp, Brush.linearGradient(listOf(look.color, look.color.copy(alpha = 0.25f), look.color.copy(alpha = 0.7f))), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(look.color, look.color.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(look.icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(look.title, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(GlassGradientH).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Text("BEST", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 9.5.sp)
                    }
                }
                Text(look.subtitle, color = GlassSub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(f.tag, color = GlassSub, fontSize = 12.5.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(look.badge, look.color)
            sizeText(f.fileSize).takeIf { it.isNotEmpty() }?.let { Spacer(Modifier.width(6.dp)); Chip("~$it", GlassMuted) }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(GlassGradientH).padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
            }
        }
    }
}

/** Two tiles per row; each tile has equal height so the grid stays tidy. */
@Composable
private fun FormatGrid(items: List<MediaFormat>, onPick: (MediaFormat) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { f -> FormatTile(f, Modifier.weight(1f).fillMaxHeight()) { onPick(f) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FormatTile(f: MediaFormat, modifier: Modifier, onClick: () -> Unit) {
    val look = lookOf(f)
    Column(
        modifier.springClick(onClick = onClick)
            .glass(RoundedCornerShape(18.dp), alpha = 0.72f)
            .heightIn(min = 112.dp)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(look.badge, look.color)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(28.dp).clip(CircleShape).background(look.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Download, "Download ${look.title}", tint = look.color, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(look.title, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(look.subtitle, color = GlassSub, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(6.dp))
        Text(f.tag, color = GlassMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        sizeText(f.fileSize).takeIf { it.isNotEmpty() }?.let {
            Text("~$it", color = GlassInk.copy(alpha = 0.75f), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun CompactFormatRow(f: MediaFormat, onClick: () -> Unit) {
    val look = lookOf(f)
    Row(
        Modifier.springClick(onClick = onClick).fillMaxWidth()
            .glass(RoundedCornerShape(14.dp), alpha = 0.6f)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(look.badge, look.color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.label, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(f.tag, sizeText(f.fileSize)).filter { it.isNotBlank() }.joinToString("  ·  "),
                color = GlassSub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.Download, null, tint = GlassAccent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun InfoNote(text: String) {
    Row(Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp)) {
        Icon(Icons.Rounded.Info, null, tint = GlassMuted, modifier = Modifier.size(14.dp).padding(top = 1.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = GlassMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
    }
}
