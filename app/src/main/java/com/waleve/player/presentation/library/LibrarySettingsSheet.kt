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
        containerColor = Color(0xFFFAF9FD),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = GlassAccentDeep, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Library settings", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            SettingRow("Hide voice notes & recordings", "WhatsApp voice notes, call recordings, ringtones and clips under 20 s", hideJunk, vm::setHideJunk)
            SettingRow("Find missing covers online", "Songs and audiobooks without artwork get a matching cover", autoCovers, vm::setAutoCovers)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Rescan phone", { vm.rescan() }, Modifier.weight(1f), icon = Icons.Rounded.Refresh, enabled = working == null, height = 46.dp)
                GlassButton("Find covers", { vm.findCovers() }, Modifier.weight(1f), icon = Icons.Rounded.Image, enabled = working == null && autoCovers, height = 46.dp)
            }
            working?.let { Text(it, color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
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
