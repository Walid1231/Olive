package com.waleve.player.presentation.together

import android.widget.Toast
import com.waleve.player.presentation.components.springClick
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.waleve.player.data.social.CollabPlaylistRepository
import com.waleve.player.domain.model.CollabPlaylist
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.RemoteTrack
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.FriendRepository
import com.waleve.player.domain.repository.LocalMusicRepository
import com.waleve.player.presentation.components.Artwork
import com.waleve.player.presentation.components.GlassButton
import com.waleve.player.presentation.components.GlassGradient
import com.waleve.player.presentation.components.GlassIconButton
import com.waleve.player.presentation.components.GlassProgress
import com.waleve.player.presentation.components.GradientButton
import com.waleve.player.presentation.components.Overline
import com.waleve.player.presentation.components.PhotoCard
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.playlist.SongSelectionBottomSheet
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub
import com.waleve.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupPlaylistViewModel @Inject constructor(
    private val repo: CollabPlaylistRepository,
    private val player: PlaybackController,
    friendRepository: FriendRepository,
    library: LocalMusicRepository,
) : ViewModel() {
    val myUid: String? get() = repo.myUid
    val playlists: StateFlow<List<CollabPlaylist>?> = repo.observeMine()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val friends: StateFlow<List<Friend>> = friendRepository.observeFriends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val librarySongs: StateFlow<List<Song>> = combine(library.getAllSongs(), library.getAudiobooks()) { a, b -> a + b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observe(id: String): Flow<CollabPlaylist?> = repo.observe(id)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    /** "Adding 2 of 5 · 40%" while songs are being added. */
    private val _adding = MutableStateFlow<String?>(null)
    val adding: StateFlow<String?> = _adding.asStateFlow()

    fun create(name: String, friendUids: Set<String>, onCreated: (String) -> Unit) {
        val names = friends.value.filter { it.uid in friendUids }.associate { it.uid to it.displayName }
        viewModelScope.launch {
            repo.create(name, names).onSuccess(onCreated).onFailure { _messages.tryEmit(it.message ?: "Couldn't create") }
        }
    }

    fun addMembers(p: CollabPlaylist, friendUids: Set<String>) {
        val names = friends.value.filter { it.uid in friendUids }.associate { it.uid to it.displayName }
        viewModelScope.launch {
            repo.addMembers(p, names).onSuccess { _messages.tryEmit("Invited ${names.size} friend${if (names.size == 1) "" else "s"}") }
                .onFailure { _messages.tryEmit(it.message ?: "Couldn't invite") }
        }
    }

    fun addSongs(id: String, ids: List<Long>) {
        val songs = librarySongs.value.filter { it.id in ids }
        if (songs.isEmpty() || _adding.value != null) return
        viewModelScope.launch {
            _adding.value = "Preparing…"
            repo.addSongs(id, songs) { i, n, pct -> _adding.value = "Adding ${i + 1} of $n · ${pct.toInt()}%" }
                .onSuccess { _messages.tryEmit("Added $it song${if (it == 1) "" else "s"}") }
                .onFailure { _messages.tryEmit(it.message ?: "Couldn't add songs") }
            _adding.value = null
        }
    }

    fun remove(id: String, t: RemoteTrack) = viewModelScope.launch {
        repo.removeTrack(id, t.key).onFailure { _messages.tryEmit(it.message ?: "Couldn't remove") }
    }
    fun rename(id: String, name: String) = viewModelScope.launch { repo.rename(id, name) }
    fun leave(p: CollabPlaylist, done: () -> Unit) = viewModelScope.launch {
        repo.leave(p).onSuccess { done() }.onFailure { _messages.tryEmit(it.message ?: "Couldn't leave") }
    }

    fun play(p: CollabPlaylist, start: Int = 0, shuffle: Boolean = false) {
        val playable = p.tracks.filter { it.playable }
        if (playable.isEmpty()) { _messages.tryEmit("Nothing playable yet — add some songs"); return }
        val startTrack = p.tracks.getOrNull(start)
        var queue = repo.queueOf(p.copy(tracks = playable))
        var idx = startTrack?.let { t -> queue.indexOfFirst { it.id == t.key.hashCode().toLong() } }?.coerceAtLeast(0) ?: 0
        if (shuffle) { queue = queue.shuffled(); idx = 0 }
        player.setQueue(queue, idx)
    }
}

@Composable
private fun Toasts(vm: GroupPlaylistViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
}

@Composable
private fun NameAndFriendsDialog(
    title: String, confirm: String, friends: List<Friend>, initialName: String?, disabled: Set<String> = emptySet(),
    onDismiss: () -> Unit, onConfirm: (String, Set<String>) -> Unit,
) {
    var name by remember { mutableStateOf(initialName ?: "") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFAF9FD),
        title = { Text(title, fontWeight = FontWeight.ExtraBold, color = GlassInk) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initialName != null) OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 60) name = it }, singleLine = true,
                    placeholder = { Text("Road trip mix", color = GlassMuted) },
                    textStyle = TextStyle(color = GlassInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GlassAccent, cursorColor = GlassAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Friends who can add songs", color = GlassSub, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FriendPicker(friends, picked, { id -> picked = if (id in picked) picked - id else picked + id }, disabled = disabled)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, picked) },
                enabled = if (initialName != null) name.isNotBlank() else picked.isNotEmpty(),
            ) { Text(confirm, color = GlassAccent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = GlassSub) } },
    )
}

/** All group playlists I'm part of. */
@Composable
fun GroupPlaylistsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: GroupPlaylistViewModel = hiltViewModel()) {
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    Toasts(vm)
    if (creating) NameAndFriendsDialog(
        "New group playlist", "Create", friends, initialName = "",
        onDismiss = { creating = false },
        onConfirm = { n, f -> creating = false; vm.create(n, f, onOpen) },
    )

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Group playlists", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("Everyone adds songs, everyone can play", color = GlassSub, fontSize = 13.sp)
                }
            }
        }
        item { GradientButton("New group playlist", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, height = 48.dp) }
        val l = lists
        when {
            l == null -> item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GlassAccent) } }
            l.isEmpty() -> item {
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), alpha = 0.55f).padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(GlassGradient), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Group, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("No group playlists yet", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("Make one and invite friends — their songs show up for you, yours for them.", color = GlassSub, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            else -> items(l, key = { it.id }) { p ->
                Row(
                    Modifier.springClick { onOpen(p.id) }.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.75f).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(p.tracks.firstOrNull { it.artUrl != null }?.artUrl, Modifier.size(58.dp), corner = 14.dp, seed = p.name.hashCode())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.tracks.size} songs · ${p.memberIds.size} people", color = GlassSub, fontSize = 12.sp)
                        Text(p.memberIds.mapNotNull { p.memberNames[it]?.substringBefore(' ') }.joinToString(", "), color = GlassMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** One group playlist: cover, members, play/shuffle, add songs, remove, rename, invite, leave. */
@Composable
fun GroupPlaylistDetailScreen(id: String, onBack: () -> Unit, vm: GroupPlaylistViewModel = hiltViewModel()) {
    val flow = remember(id) { vm.observe(id) }
    val p by flow.collectAsStateWithLifecycle(initialValue = null)
    val friends by vm.friends.collectAsStateWithLifecycle()
    val library by vm.librarySongs.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    Toasts(vm)
    val me = vm.myUid
    val pl = p

    if (pl != null) {
        if (picking) SongSelectionBottomSheet(
            allSongs = library, existingSongIds = emptySet(),
            onAddSongs = { ids -> picking = false; vm.addSongs(pl.id, ids) },
            onDismiss = { picking = false },
        )
        if (inviting) NameAndFriendsDialog(
            "Invite friends", "Invite", friends, initialName = null, disabled = pl.memberIds.toSet(),
            onDismiss = { inviting = false }, onConfirm = { _, f -> inviting = false; vm.addMembers(pl, f) },
        )
        if (renaming) {
            var name by remember { mutableStateOf(pl.name) }
            AlertDialog(
                onDismissRequest = { renaming = false }, containerColor = Color(0xFFFAF9FD),
                title = { Text("Rename", fontWeight = FontWeight.ExtraBold, color = GlassInk) },
                text = {
                    OutlinedTextField(name, { if (it.length <= 60) name = it }, singleLine = true,
                        textStyle = TextStyle(color = GlassInk, fontSize = 16.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GlassAccent, cursorColor = GlassAccent))
                },
                confirmButton = { TextButton(onClick = { vm.rename(pl.id, name); renaming = false }, enabled = name.isNotBlank()) { Text("Save", color = GlassAccent, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel", color = GlassSub) } },
            )
        }
        if (confirmLeave) {
            val owner = pl.ownerId == me
            AlertDialog(
                onDismissRequest = { confirmLeave = false }, containerColor = Color.White,
                title = { Text(if (owner) "Delete “${pl.name}”?" else "Leave “${pl.name}”?", fontWeight = FontWeight.ExtraBold, color = GlassInk) },
                text = { Text(if (owner) "It disappears for everyone. Songs stay on each phone." else "You won't see it any more. Others keep it.", color = GlassSub) },
                confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave(pl, onBack) }) { Text(if (owner) "Delete" else "Leave", color = GlassDanger, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel", color = GlassSub) } },
            )
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
                Spacer(Modifier.width(10.dp))
                Text("Group playlist", color = GlassSub, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (pl != null) Box {
                    var menu by remember { mutableStateOf(false) }
                    GlassIconButton(Icons.Rounded.MoreVert, { menu = true }, contentDescription = "More")
                    DropdownMenu(menu, { menu = false }, containerColor = Color.White) {
                        DropdownMenuItem(text = { Text("Rename", color = GlassInk) }, leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = GlassAccent) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Invite friends", color = GlassInk) }, leadingIcon = { Icon(Icons.Rounded.PersonAdd, null, tint = GlassAccent) }, onClick = { menu = false; inviting = true })
                        DropdownMenuItem(
                            text = { Text(if (pl.ownerId == me) "Delete playlist" else "Leave playlist", color = GlassDanger) },
                            leadingIcon = { Icon(if (pl.ownerId == me) Icons.Rounded.Delete else Icons.AutoMirrored.Rounded.Logout, null, tint = GlassDanger) },
                            onClick = { menu = false; confirmLeave = true },
                        )
                    }
                }
            }
        }
        if (pl == null) {
            item {
                Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.5f).padding(26.dp), contentAlignment = Alignment.Center) {
                    Text("Loading… (or you're no longer in this playlist)", color = GlassSub)
                }
            }
            return@LazyColumn
        }
        item {
            PhotoCard(
                model = pl.tracks.firstOrNull { it.artUrl != null }?.artUrl,
                title = pl.name,
                subtitle = "${pl.tracks.size} songs · " + pl.memberIds.mapNotNull { pl.memberNames[it]?.substringBefore(' ') }.joinToString(", "),
                seed = pl.name.hashCode(), corner = 28.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.3f),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Play", { vm.play(pl) }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, enabled = pl.tracks.isNotEmpty())
                GlassButton("Shuffle", { vm.play(pl, shuffle = true) }, Modifier.weight(1f), icon = Icons.Rounded.Shuffle, enabled = pl.tracks.isNotEmpty())
                GlassIconButton(Icons.AutoMirrored.Rounded.PlaylistAdd, { picking = true }, contentDescription = "Add songs")
            }
        }
        adding?.let { a ->
            item {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), alpha = 0.85f).padding(14.dp)) {
                    Text(a, color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    GlassProgress(a.substringAfterLast("· ", "0").removeSuffix("%").toFloatOrNull()?.div(100f) ?: 0f, height = 5.dp)
                }
            }
        }
        if (pl.tracks.isEmpty()) {
            item {
                Column(
                    Modifier.springClick { picking = true }.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.55f).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, tint = GlassAccent, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Add the first songs", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("Local files are sent so friends can play them too.", color = GlassSub, fontSize = 12.sp)
                }
            }
        } else {
            item { Overline("Songs", GlassSub) }
            itemsIndexed(pl.tracks, key = { _, t -> t.key }) { i, t ->
                Row(
                    Modifier.springClick(enabled = t.playable) { vm.play(pl, i) }.fillMaxWidth().glass(RoundedCornerShape(18.dp), alpha = 0.7f)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t.artUrl, Modifier.size(48.dp), corner = 12.dp, seed = t.title.hashCode())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(t.artist, t.addedByName.takeIf { it.isNotBlank() }?.let { "added by ${it.substringBefore(' ')}" })
                                .filter { !it.isNullOrBlank() }.joinToString(" · "),
                            color = GlassSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (t.addedBy == me || pl.ownerId == me) {
                        IconButton(onClick = { vm.remove(pl.id, t) }) { Icon(Icons.Rounded.Delete, "Remove", tint = GlassMuted) }
                    }
                }
            }
        }
    }
}
