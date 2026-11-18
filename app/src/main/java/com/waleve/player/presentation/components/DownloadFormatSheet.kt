package com.waleve.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.data.extractor.MediaFormat
import com.waleve.player.presentation.theme.LocalGhibliColors

/**
 * Bottom sheet to pick how to download a track found online:
 *  Audio tab → MP3 / M4A / Opus / FLAC / WAV / OGG + "Song or Audiobook"
 *  Video tab → MP4 360p … 4K (saved to Movies/Olive)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFormatSheet(
    title: String,
    durationSec: Long,
    onPick: (format: MediaFormat, asAudiobook: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val gc = LocalGhibliColors.current
    var tab by remember { mutableIntStateOf(0) }
    var asAudiobook by remember(title) { mutableStateOf(durationSec >= 40 * 60) }
    val audio = remember(durationSec) { MediaExtractor.audioConversionChoices(durationSec) }
    val video = remember(durationSec) { MediaExtractor.videoChoices(durationSec, maxHeight = 2160) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = gc.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text("Download", style = MaterialTheme.typography.titleLarge, color = gc.textPrimary, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = gc.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))

            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = gc.accent,
                indicator = { pos ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = gc.accent)
                },
                divider = {},
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Audio", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Rounded.GraphicEq, null) }, unselectedContentColor = gc.textMuted)
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Video", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Rounded.Videocam, null) }, unselectedContentColor = gc.textMuted)
            }
            Spacer(Modifier.height(12.dp))

            if (tab == 0) {
                SaveAsToggle(asAudiobook = asAudiobook, onChange = { asAudiobook = it })
                Spacer(Modifier.height(10.dp))
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (if (tab == 0) audio else video).forEach { f ->
                    FormatChoiceRow(f) { onPick(f, tab == 0 && asAudiobook) }
                }
                if (tab == 0) {
                    Text(
                        "Online sources are lossy (about 128–160 kbps). FLAC and WAV keep that exact sound in a lossless file — bigger, not better.",
                        style = MaterialTheme.typography.labelSmall,
                        color = gc.textMuted,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatChoiceRow(format: MediaFormat, onClick: () -> Unit) {
    val gc = LocalGhibliColors.current
    val lossless = format.extension == "flac" || format.extension == "wav"
    val tint = when {
        !format.isAudioOnly -> Color(0xFF64B5F6)
        lossless -> Color(0xFFFFB74D)
        else -> gc.accent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(gc.surfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    !format.isAudioOnly -> Icons.Rounded.Videocam
                    lossless -> Icons.Rounded.HighQuality
                    else -> Icons.Rounded.MusicNote
                },
                null, tint = tint, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(format.label, style = MaterialTheme.typography.titleSmall, color = gc.textPrimary, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(format.tag, style = MaterialTheme.typography.labelSmall, color = tint)
                if (format.fileSize > 0) {
                    Text("  ·  ~${"%.0f".format(format.fileSize / 1_048_576.0)} MB",
                        style = MaterialTheme.typography.labelSmall, color = gc.textMuted)
                }
            }
        }
        Icon(Icons.Rounded.Download, null, tint = gc.accent, modifier = Modifier.size(22.dp))
    }
}
