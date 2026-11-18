package com.waleve.player.presentation.components

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.LocalMusicRepository
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassSub
import com.waleve.player.service.MediaDeleteHelper
import com.waleve.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeleteSongsViewModel @Inject constructor(
    private val repo: LocalMusicRepository,
    private val helper: MediaDeleteHelper,
    private val playback: PlaybackController,
) : ViewModel() {

    sealed class Event {
        data class Toast(val text: String) : Event()
        data class AskSystem(val sender: android.content.IntentSender, val songs: List<Song>) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private fun skipIfPlaying(ids: Set<Long>) {
        val cur = playback.playerState.value.currentSong ?: return
        if (cur.id in ids) playback.next()
    }

    fun removeFromLibrary(songs: List<Song>) = viewModelScope.launch {
        skipIfPlaying(songs.map { it.id }.toSet())
        repo.removeFromLibrary(songs.map { it.id })
        _events.tryEmit(Event.Toast(if (songs.size == 1) "Removed “${songs[0].title}” from library" else "Removed ${songs.size} items from library"))
    }

    fun deleteFromPhone(songs: List<Song>) = viewModelScope.launch {
        when (val o = helper.delete(songs.map { it.path })) {
            is MediaDeleteHelper.Outcome.Deleted -> finish(songs.filter { it.path in o.paths })
            is MediaDeleteHelper.Outcome.NeedsConsent -> _events.tryEmit(Event.AskSystem(o.sender, songs.filter { it.path in o.paths }))
            is MediaDeleteHelper.Outcome.Failed -> _events.tryEmit(Event.Toast("⚠ ${o.message}"))
        }
    }

    /** The user tapped "Allow" in the system dialog. */
    fun onSystemApproved(songs: List<Song>, remaining: List<Song>) = viewModelScope.launch {
        helper.finishAfterConsent(songs.map { it.path })
        finish(songs)
        // Android 10 asks one file at a time — continue with the rest
        if (remaining.isNotEmpty()) deleteFromPhone(remaining)
    }

    private suspend fun finish(songs: List<Song>) {
        if (songs.isEmpty()) return
        skipIfPlaying(songs.map { it.id }.toSet())
        repo.forgetSongs(songs.map { it.id })
        _events.tryEmit(Event.Toast(if (songs.size == 1) "Deleted “${songs[0].title}”" else "Deleted ${songs.size} files"))
    }
}

/**
 * Hosts the "Delete" flow for a screen. Call the returned function with the songs/chapters to delete:
 * the user picks "Remove from library" (keep file) or "Delete from phone" (Android confirms), and
 * everything else — playlists, playback, rescans — is handled.
 */
@Composable
fun rememberSongDeleter(): (List<Song>) -> Unit {
    val vm: DeleteSongsViewModel = hiltViewModel()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<Song>?>(null) }
    var waitingOnSystem by remember { mutableStateOf<List<Song>>(emptyList()) }
    var allSelected by remember { mutableStateOf<List<Song>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val done = waitingOnSystem
            val doneIds = done.map { it.id }.toSet()
            // Android 11+ deletes everything in one dialog; Android 10 asks file by file
            val rest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) emptyList()
                       else allSelected.filter { it.id !in doneIds }
            vm.onSystemApproved(done, rest)
            allSelected = rest
        } else {
            Toast.makeText(context, "Nothing was deleted", Toast.LENGTH_SHORT).show()
        }
        waitingOnSystem = emptyList()
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is DeleteSongsViewModel.Event.Toast -> Toast.makeText(context, e.text, Toast.LENGTH_SHORT).show()
                is DeleteSongsViewModel.Event.AskSystem -> {
                    waitingOnSystem = e.songs
                    launcher.launch(IntentSenderRequest.Builder(e.sender).build())
                }
            }
        }
    }

    pending?.let { songs ->
        val isBook = songs.all { it.isAudiobook }
        val what = when {
            songs.size == 1 -> "“${songs[0].title}”"
            isBook -> "${songs.size} chapters"
            else -> "${songs.size} songs"
        }
        val onPhone = songs.any { !it.path.startsWith("http") }
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete $what?", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeleteOption(
                        Icons.Rounded.VisibilityOff, "Remove from library",
                        "Hide it in Olive. The file stays on your phone.", GlassInk,
                    ) { vm.removeFromLibrary(songs); pending = null }
                    if (onPhone) DeleteOption(
                        Icons.Rounded.DeleteForever, "Delete from phone",
                        "Permanently delete the file${if (songs.size > 1) "s" else ""}. Android will ask you to confirm.", GlassDanger,
                    ) { allSelected = songs; vm.deleteFromPhone(songs); pending = null }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel", color = GlassSub, fontWeight = FontWeight.Bold) } },
        )
    }
    return remember { { songs: List<Song> -> if (songs.isNotEmpty()) pending = songs } }
}

@Composable
private fun DeleteOption(icon: ImageVector, title: String, body: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.06f)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(body, color = GlassSub, fontSize = 12.5.sp)
        }
    }
}
