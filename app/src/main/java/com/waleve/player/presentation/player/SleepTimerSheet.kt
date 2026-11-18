package com.waleve.player.presentation.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.presentation.components.GlassButton
import com.waleve.player.presentation.components.GlassChip
import com.waleve.player.presentation.components.GradientButton
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassAccentDeep
import com.waleve.player.presentation.theme.GlassAccentLight
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassSub
import com.waleve.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SleepTimerViewModel @Inject constructor(private val controller: PlaybackController) : ViewModel() {
    val remaining = controller.sleepTimerRemaining
    val total = controller.sleepTimerTotal
    val endOfTrack = controller.sleepEndOfTrack
    val fadeOut = controller.sleepFadeOut
    val shake = controller.sleepShake
    fun setShake(on: Boolean) = controller.setSleepShake(on)
    fun start(minutes: Int) = controller.setSleepTimer(minutes)
    fun extend(minutes: Int) = controller.extendSleepTimer(minutes)
    fun endOfTrack() = controller.setSleepAtEndOfTrack()
    fun setFade(on: Boolean) = controller.setSleepFadeOut(on)
    fun cancel() = controller.cancelSleepTimer()
}

fun formatSleep(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Sleep mode: presets, custom length, "end of this track", fade-out, extend and cancel. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit, vm: SleepTimerViewModel = hiltViewModel()) {
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val endOfTrack by vm.endOfTrack.collectAsStateWithLifecycle()
    val fade by vm.fadeOut.collectAsStateWithLifecycle()
    val shake by vm.shake.collectAsStateWithLifecycle()
    val active = remaining > 0 || endOfTrack
    var custom by remember { mutableFloatStateOf(25f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFFAF9FD),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = GlassAccentDeep, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sleep mode", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("Music stops by itself. Good night.", color = GlassSub, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(18.dp))

            if (active) {
                // ── Running: countdown ring ──
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val frac = if (endOfTrack || total <= 0) 1f else remaining.toFloat() / total
                    Canvas(Modifier.size(170.dp)) {
                        val sw = 12.dp.toPx()
                        val arc = Size(size.width - sw, size.height - sw)
                        val tl = Offset(sw / 2, sw / 2)
                        drawArc(GlassInk.copy(alpha = 0.07f), -90f, 360f, false, tl, arc, style = Stroke(sw))
                        drawArc(
                            androidx.compose.ui.graphics.Brush.sweepGradient(listOf(GlassAccentLight, GlassAccentDeep, GlassAccentLight)),
                            -90f, 360f * frac, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (endOfTrack) "End of" else formatSleep(remaining), color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = if (endOfTrack) 24.sp else 32.sp)
                        Text(if (endOfTrack) "this track" else "left", color = GlassSub, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!endOfTrack) {
                        GlassButton("+5 min", { vm.extend(5) }, Modifier.weight(1f), height = 46.dp)
                        GlassButton("+15 min", { vm.extend(15) }, Modifier.weight(1f), height = 46.dp)
                    }
                    GlassButton("Turn off", { vm.cancel() }, Modifier.weight(1f), icon = Icons.Rounded.Close, tint = GlassDanger, height = 46.dp)
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Presets ──
            Text(if (active) "Change timer" else "Stop music in", color = GlassSub, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20, 30, 45, 60, 90, 120).forEach { m ->
                    GlassChip(if (m >= 60) "${m / 60}h${if (m % 60 > 0) " ${m % 60}m" else ""}" else "$m min", false, { vm.start(m); onDismiss() })
                }
                GlassChip("End of track", endOfTrack, { vm.endOfTrack(); onDismiss() })
            }
            Spacer(Modifier.height(16.dp))

            // ── Custom ──
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.9f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Custom", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    val c = custom.toInt()
                    Text(if (c >= 60) "${c / 60} h ${c % 60} min" else "$c min", color = GlassAccent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                Slider(
                    value = custom, onValueChange = { custom = it }, valueRange = 5f..180f, steps = 34,
                    colors = SliderDefaults.colors(thumbColor = GlassAccent, activeTrackColor = GlassAccent, inactiveTrackColor = GlassInk.copy(alpha = 0.1f)),
                )
                GradientButton("Start ${custom.toInt()} min timer", { vm.start(custom.toInt()); onDismiss() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Bedtime, height = 46.dp)
            }
            Spacer(Modifier.height(12.dp))

            // ── Fade out ──
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.9f).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fade out", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Lower the volume slowly in the last 30 seconds", color = GlassSub, fontSize = 12.sp)
                }
                Switch(fade, vm::setFade, colors = SwitchDefaults.colors(checkedTrackColor = GlassAccent, checkedThumbColor = Color.White))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.9f).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Shake for 5 more minutes", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Still awake? Give the phone a shake", color = GlassSub, fontSize = 12.sp)
                }
                Switch(shake, vm::setShake, colors = SwitchDefaults.colors(checkedTrackColor = GlassAccent, checkedThumbColor = Color.White))
            }
        }
    }
}
