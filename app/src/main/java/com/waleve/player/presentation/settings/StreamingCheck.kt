package com.waleve.player.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.waleve.player.domain.model.StreamResolution
import com.waleve.player.domain.repository.StreamRepository
import com.waleve.player.presentation.components.GlassButton
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub
import com.waleve.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How each way of getting a song's link behaves on this phone + network. */
@HiltViewModel
class StreamingCheckViewModel @Inject constructor(
    private val streams: StreamRepository,
    private val player: PlaybackController,
) : ViewModel() {

    data class Line(val via: String, val state: Int, val ms: Long = 0, val note: String = "") // 0 testing, 1 ok, 2 failed

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()
    private val _subject = MutableStateFlow("")
    val subject: StateFlow<String> = _subject.asStateFlow()
    private val _recent = MutableStateFlow<List<StreamResolution>>(emptyList())
    val recent: StateFlow<List<StreamResolution>> = _recent.asStateFlow()
    private var job: Job? = null

    fun run() {
        job?.cancel()
        _recent.value = streams.recentResolutions()
        val cur = player.playerState.value.currentSong
        val vid = cur?.videoId ?: cur?.path?.substringAfter("ytm://stream/", "")?.takeIf { it.isNotEmpty() }
        _subject.value = if (vid != null) "“${cur?.title}”" else "a test song"
        _lines.value = METHODS.map { Line(it, 0) }
        job = viewModelScope.launch {
            streams.diagnose(vid ?: TEST_VIDEO).collect { a ->
                _lines.update { list -> list.map { if (it.via == a.via) Line(a.via, if (a.ok) 1 else 2, a.ms, a.note) else it } }
            }
        }
    }

    companion object {
        val METHODS = listOf("visionos", "tv_samsung", "tv", "android_vr", "yt-dlp")
        private const val TEST_VIDEO = "kJQP7kiw5Fk"
        fun label(via: String?) = when (via) {
            "visionos" -> "Vision Pro link"
            "tv_samsung" -> "Samsung TV link"
            "tv" -> "TV link"
            "android_vr" -> "VR link (old way)"
            "yt-dlp" -> "yt-dlp (backup)"
            null -> "nothing worked"
            else -> via
        }
    }
}

private fun secs(ms: Long) = if (ms < 10_000) "%.1f s".format(ms / 1000f) else "${ms / 1000} s"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingCheckSheet(onDismiss: () -> Unit, vm: StreamingCheckViewModel = hiltViewModel()) {
    val lines by vm.lines.collectAsStateWithLifecycle()
    val subject by vm.subject.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.run() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = com.waleve.player.presentation.theme.GlassPanel,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = GlassAccent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Streaming check", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("Testing with $subject", color = GlassSub, fontSize = 12.5.sp)
                }
            }
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.85f).padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                lines.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                            when (l.state) {
                                0 -> CircularProgressIndicator(Modifier.size(18.dp), color = GlassAccent, strokeWidth = 2.dp)
                                1 -> Box(Modifier.size(24.dp).clip(CircleShape).background(GlassAccent), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                else -> Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFE5486C).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Close, null, tint = Color(0xFFE5486C), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(StreamingCheckViewModel.label(l.via), color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            if (l.state == 2 && l.note.isNotBlank()) Text(l.note, color = GlassMuted, fontSize = 11.5.sp, maxLines = 2)
                        }
                        Text(
                            when (l.state) { 0 -> "…"; else -> secs(l.ms) },
                            color = if (l.state == 1) GlassAccent else GlassSub, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp,
                        )
                    }
                }
            }
            if (recent.isNotEmpty()) {
                Text("LAST SONGS", color = GlassSub, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.7f, elevation = 0.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recent.take(6).forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(StreamingCheckViewModel.label(r.winner), color = GlassInk, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(secs(r.totalMs), color = if (r.totalMs <= 5_000) GlassAccent else Color(0xFFE5486C), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }
            }
            Text(
                "Olive tries all of these at once and plays the first link that works. If songs still load slowly, send a screenshot of this page.",
                color = GlassMuted, fontSize = 11.5.sp, lineHeight = 15.sp,
            )
            GlassButton("Run again", { vm.run() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh, height = 46.dp)
        }
    }
}
