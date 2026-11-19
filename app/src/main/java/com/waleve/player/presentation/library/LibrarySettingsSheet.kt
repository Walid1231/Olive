package com.waleve.player.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.waleve.player.data.cover.CoverArtFetcher
import com.waleve.player.data.cover.LibraryPrefs
import com.waleve.player.domain.repository.LocalMusicRepository
import com.waleve.player.presentation.components.GlassButton
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassAccentDeep
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassSub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibrarySettingsViewModel @Inject constructor(
    private val prefs: LibraryPrefs,
    private val covers: CoverArtFetcher,
    private val library: LocalMusicRepository,
) : ViewModel() {
    val hideJunk = prefs.hideJunk
    val autoCovers = prefs.autoCovers
    private val _working = MutableStateFlow<String?>(null)
    val working = _working.asStateFlow()

    fun setHideJunk(on: Boolean) { prefs.setHideJunk(on); rescan() }
    fun setAutoCovers(on: Boolean) { prefs.setAutoCovers(on); if (on) { covers.resetMisses(); findCovers() } }

    fun rescan() = viewModelScope.launch {
        _working.value = "Scanning your phone…"
        runCatching { library.scanMusic() }
        _working.value = null
    }

    fun findCovers() = viewModelScope.launch {
        _working.value = "Looking for missing covers…"
        runCatching { covers.run(maxLookups = 150) }
        val n = covers.found.value
        _working.value = if (n > 0) "Added $n cover${if (n == 1) "" else "s"}" else "No new covers found"
        kotlinx.coroutines.delay(2500)
        _working.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsSheet(onDismiss: () -> Unit, vm: LibrarySettingsViewModel = hiltViewModel()) {
    val hideJunk by vm.hideJunk.collectAsStateWithLifecycle()
    val autoCovers by vm.autoCovers.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = com.waleve.player.presentation.theme.GlassPanel,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = GlassAccentDeep, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Settings", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            // ── Appearance ──
            val themeVm: com.waleve.player.presentation.theme.ThemeViewModel = hiltViewModel()
            val appearance by themeVm.mode.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.9f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Appearance", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Dark glass keeps the blurred cover behind everything", color = GlassSub, fontSize = 12.sp)
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.waleve.player.presentation.components.GlassChip("Auto", appearance == 0, { themeVm.setMode(0) }, icon = Icons.Rounded.BrightnessAuto)
                    com.waleve.player.presentation.components.GlassChip("Light", appearance == 1, { themeVm.setMode(1) }, icon = Icons.Rounded.LightMode)
                    com.waleve.player.presentation.components.GlassChip("Dark", appearance == 2, { themeVm.setMode(2) }, icon = Icons.Rounded.DarkMode)
                }
            }
            SettingRow("Hide voice notes & recordings", "WhatsApp voice notes, call recordings, ringtones and clips under 20 s", hideJunk, vm::setHideJunk)
            SettingRow("Find missing covers online", "Songs and audiobooks without artwork get a matching cover", autoCovers, vm::setAutoCovers)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Rescan phone", { vm.rescan() }, Modifier.weight(1f), icon = Icons.Rounded.Refresh, enabled = working == null, height = 46.dp)
                GlassButton("Find covers", { vm.findCovers() }, Modifier.weight(1f), icon = Icons.Rounded.Image, enabled = working == null && autoCovers, height = 46.dp)
            }
            working?.let { Text(it, color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp) }

            // ── App ──
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val updateVm: com.waleve.player.presentation.update.AppUpdateViewModel = hiltViewModel()
            val checking by updateVm.checking.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                updateVm.messages.collect { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show() }
            }
            Text("App · version ${com.waleve.player.BuildConfig.VERSION_NAME}", color = GlassSub, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(if (checking) "Checking…" else "Check for updates", { updateVm.checkNow() }, Modifier.weight(1f), icon = Icons.Rounded.SystemUpdate, enabled = !checking, height = 46.dp)
                GlassButton("Add widget", { com.waleve.player.widget.WidgetPin.request(ctx) }, Modifier.weight(1f), icon = Icons.Rounded.Widgets, height = 46.dp)
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.9f).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(sub, color = GlassSub, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = GlassAccent, checkedThumbColor = Color.White))
    }
}
