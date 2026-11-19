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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
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
    var asAudiobook by remember(title) { mutableStateOf(durationSec >= 40 * 60) }
    val audio = remember(durationSec) { MediaExtractor.audioConversionChoices(durationSec) }
    val video = remember(durationSec) { MediaExtractor.videoChoices(durationSec, maxHeight = 2160) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = com.waleve.player.presentation.theme.GlassPanel,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            // ── Header: icon, what you're downloading, how long it is ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(GlassGradient),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Download", color = com.waleve.player.presentation.theme.GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(title, color = com.waleve.player.presentation.theme.GlassSub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                }
                if (durationSec > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(com.waleve.player.presentation.theme.GlassAccent.copy(alpha = 0.12f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        val h = durationSec / 3600; val m = (durationSec % 3600) / 60; val sec = durationSec % 60
                        Text(
                            if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec),
                            color = com.waleve.player.presentation.theme.GlassAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.verticalScroll(rememberScrollState())) {
                FormatPicker(
                    audio = audio,
                    video = video,
                    asAudiobook = asAudiobook,
                    onAsAudiobook = { asAudiobook = it },
                    onPick = onPick,
                )
            }
        }
    }
}
