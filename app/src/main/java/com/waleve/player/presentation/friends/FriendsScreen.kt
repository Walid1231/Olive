package com.waleve.player.presentation.friends

import android.content.ClipData
import com.waleve.player.presentation.components.springClick
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.shadow
import com.waleve.player.presentation.components.PhotoCard
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.waleve.player.presentation.components.GlassGradient
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.FriendRequest
import com.waleve.player.domain.model.InboxNotification
import com.waleve.player.domain.model.InboxNotificationType
import com.waleve.player.domain.model.UserProfile
import com.waleve.player.presentation.components.Artwork
import com.waleve.player.presentation.components.Avatar
import com.waleve.player.presentation.components.DownloadFormatSheet
import com.waleve.player.presentation.components.GlassButton
import com.waleve.player.presentation.components.GlassCard
import com.waleve.player.presentation.components.GlassProgress
import com.waleve.player.presentation.components.GlassSegmented
import com.waleve.player.presentation.components.GradientButton
import com.waleve.player.presentation.components.Overline
import com.waleve.player.presentation.components.SegmentOption
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub

/**
 * Friends hub (Figma "06 Friends – Inbox"):
 *  • your friend code (copy / share) + people search with live status buttons
 *  • Inbox  — shared songs, audiobooks, playlists, "request accepted" notices (play, download, delete, mark read)
 *  • Friends — your friends (remove with confirmation)
 *  • Requests — received (accept / decline) and sent (cancel)
 */
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    isNightMode: Boolean,
    onOpenSharedPlaylist: (shareId: String) -> Unit,
    onPlaySharedSong: (InboxNotification) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    myProfile: UserProfile? = null,
    onOpenGroupPlaylists: () -> Unit = {},
    onOpenGroupPlaylist: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val togetherVm: com.waleve.player.presentation.together.TogetherViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val activity by togetherVm.activity.collectAsStateWithLifecycle()
    val invites by togetherVm.invites.collectAsStateWithLifecycle()
    val shareActivity by togetherVm.shareActivity.collectAsStateWithLifecycle()
    var showTogether by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<InboxNotification?>(null) }
    com.waleve.player.presentation.together.TogetherToasts(togetherVm)
    if (showTogether) com.waleve.player.presentation.together.ListenTogetherSheet(onDismiss = { showTogether = false }, vm = togetherVm)
    replyTo?.let { n ->
        var text by remember(n.notifId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { replyTo = null },
            containerColor = Color.White,
            title = { Text("Reply to ${n.fromName}", fontWeight = FontWeight.ExtraBold, color = GlassInk) },
            text = {
                Column {
                    Text("About “${n.songTitle ?: n.playlistName ?: "your share"}”", color = GlassSub, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = text, onValueChange = { if (it.length <= 200) text = it },
                        placeholder = { Text("Loved this one!", color = GlassMuted) },
                        textStyle = TextStyle(color = GlassInk, fontSize = 15.sp), maxLines = 4,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = GlassAccent, cursorColor = GlassAccent),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.react(n, null, text); replyTo = null }, enabled = text.isNotBlank()) { Text("Send", color = GlassAccent, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { replyTo = null }) { Text("Cancel", color = GlassSub) } },
        )
    }
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val incoming by viewModel.incomingRequests.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoingRequests.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val unread by viewModel.unreadInboxCount.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(if (incoming.isNotEmpty() && unread == 0) 2 else 0) }
    var formatSheetFor by remember { mutableStateOf<InboxNotification?>(null) }
    var confirmRemove by remember { mutableStateOf<Friend?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            val msg = when (e) { is FriendsUiEvent.Success -> e.message; is FriendsUiEvent.Error -> "⚠ " + e.message }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    formatSheetFor?.let { notif ->
        DownloadFormatSheet(
            title = notif.songTitle ?: "Shared song",
            durationSec = notif.durationMs / 1000,
            onDismiss = { formatSheetFor = null },
            onPick = { format, asAudiobook ->
                viewModel.downloadSharedSong(notif, format, asAudiobook || (notif.isAudiobook && format.isAudioOnly))
                formatSheetFor = null
            },
        )
    }
    confirmRemove?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            containerColor = Color.White,
            title = { Text("Remove ${f.displayName}?", fontWeight = FontWeight.ExtraBold, color = GlassInk) },
            text = { Text("You'll stop being friends on both sides. You can send a new request any time.", color = GlassSub) },
            confirmButton = { TextButton(onClick = { viewModel.removeFriend(f); confirmRemove = null }) { Text("Remove", color = GlassDanger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel", color = GlassSub) } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Header ──
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Friends", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, modifier = Modifier.weight(1f))
                Avatar(myProfile?.avatarUrl, myProfile?.displayName ?: "Me", 44.dp, Modifier.springClick(onClick = onOpenProfile))
            }
        }
        // ── My friend code ──
        item {
            val code = myProfile?.friendCode.orEmpty()
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Overline("Your friend code", GlassSub)
                    Text(code.ifBlank { "…" }, color = GlassAccent, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 1.sp)
                }
                GlassButton("Copy", { copyToClipboard(context, code) }, icon = Icons.Rounded.ContentCopy, height = 38.dp, enabled = code.isNotBlank())
                Spacer(Modifier.width(8.dp))
                GradientButton("Invite", { shareCode(context, code) }, icon = Icons.Rounded.Share, height = 38.dp, enabled = code.isNotBlank())
            }
        }
        // ── Together: Listen together + Group playlists ──
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TogetherTile(
                    Icons.Rounded.Headphones, "Listen together",
                    if (invites.isNotEmpty()) "${invites.size} invite${if (invites.size == 1) "" else "s"} waiting" else "Same song, same second",
                    colors = listOf(Color(0xFF46C190), Color(0xFF2E8FA8)),
                    highlight = invites.isNotEmpty(), onClick = { showTogether = true }, modifier = Modifier.weight(1f),
                )
                TogetherTile(
                    Icons.AutoMirrored.Rounded.PlaylistPlay, "Group playlists", "Everyone adds songs",
                    colors = listOf(Color(0xFFA08CFF), Color(0xFF6A5ACD)),
                    onClick = onOpenGroupPlaylists, modifier = Modifier.weight(1f),
                )
            }
        }
        // ── Friends listening right now ──
        val live = friends.mapNotNull { f -> activity[f.uid]?.let { f to it } }.sortedByDescending { it.second.isPlaying }
        if (live.isNotEmpty() && query.trim().length < 2) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Listening now", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text("${live.count { it.second.isPlaying }} live", color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        live.forEach { (f, np) ->
                            val inv = invites.firstOrNull { it.hostUid == f.uid }
                            val action = when { inv != null -> "Join session"; np.track.playable -> "Tap to play"; else -> "On their phone" }
                            PhotoCard(
                                model = np.track.artUrl,
                                title = np.track.title,
                                subtitle = "${f.displayName.substringBefore(' ')} · $action",
                                seed = np.track.title.hashCode(),
                                onClick = { if (inv != null) togetherVm.join(f.uid) else togetherVm.playFriend(np) },
                                modifier = Modifier.width(148.dp).aspectRatio(0.78f),
                                topStart = { Avatar(f.avatarUrl, f.displayName, 30.dp, Modifier.padding(8.dp)) },
                                topEnd = {
                                    if (np.isPlaying) Row(
                                        Modifier.padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        EqualizerBars()
                                        Spacer(Modifier.width(5.dp))
                                        Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        // ── Search people ──
        item {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), alpha = 0.72f, elevation = 0.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = GlassSub, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Find people by name, email or code", color = GlassMuted, fontSize = 14.sp)
                    BasicTextField(
                        value = query, onValueChange = viewModel::updateQuery, singleLine = true,
                        textStyle = TextStyle(color = GlassInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(GlassAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isSearching) CircularProgressIndicator(Modifier.size(18.dp), color = GlassAccent, strokeWidth = 2.dp)
                else if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = GlassMuted, modifier = Modifier.springClick { viewModel.clearSearch() }.size(20.dp))
            }
        }

        if (query.trim().length >= 2) {
            // ── Search results with relationship-aware buttons ──
            if (people.isEmpty() && !isSearching) {
                item { EmptyNote(Icons.Rounded.Search, "No one found", "Try their full name, Gmail address, or friend code like OLV-7K3P.") }
            }
            items(people, key = { "p_" + it.person.uid }) { p ->
                PersonRow(p.person) {
                    val isBusy = p.person.uid in busy || (p.requestId != null && p.requestId in busy)
                    when (p.relation) {
                        Relation.NONE -> GradientButton("Add", { viewModel.sendFriendRequest(p.person.uid, p.person.displayName) }, icon = Icons.Rounded.PersonAdd, height = 36.dp, loading = isBusy)
                        Relation.REQUEST_SENT -> GlassButton("Requested", { viewModel.cancelRequest(p.requestId!!, p.person.displayName) }, icon = Icons.Rounded.Undo, tint = GlassSub, height = 36.dp, enabled = !isBusy)
                        Relation.REQUEST_RECEIVED -> GradientButton("Accept", { viewModel.acceptFromSearch(p) }, icon = Icons.Rounded.Check, height = 36.dp, loading = isBusy)
                        Relation.FRIEND -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = GlassAccent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text("Friends", color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            // ── Tabs ──
            item {
                GlassSegmented(
                    options = listOf(
                        SegmentOption("Inbox", count = unread.takeIf { it > 0 } ?: inbox.size),
                        SegmentOption("Friends", count = friends.size),
                        SegmentOption("Requests", count = incoming.size),
                    ),
                    selected = tab, onSelect = { tab = it },
                )
            }
            when (tab) {
                0 -> inboxTab(inbox, progress, unread,
                    onOpen = { n ->
                        viewModel.markNotificationRead(n.notifId)
                        when (n.type) {
                            InboxNotificationType.PLAYLIST_SHARED -> n.shareId?.let(onOpenSharedPlaylist)
                            InboxNotificationType.FRIEND_ACCEPTED -> tab = 1
                            InboxNotificationType.LISTEN_INVITE -> showTogether = true
                            InboxNotificationType.COLLAB_INVITE -> n.shareId?.let(onOpenGroupPlaylist)
                            else -> {}
                        }
                    },
                    onJoin = { n -> viewModel.markNotificationRead(n.notifId); n.shareId?.let { togetherVm.join(it) } },
                    liveHosts = invites.map { it.hostUid }.toSet(),
                    onReact = { n, e -> viewModel.markNotificationRead(n.notifId); viewModel.react(n, e) },
                    onReply = { n -> replyTo = n },
                    onPlay = { n -> viewModel.markNotificationRead(n.notifId); onPlaySharedSong(n) },
                    onDownload = { n -> viewModel.markNotificationRead(n.notifId); if (n.fileUrl != null) viewModel.downloadSharedSong(n) else formatSheetFor = n },
                    onDelete = { n -> viewModel.deleteNotification(n.notifId) },
                    onMarkAll = viewModel::markAllRead,
                    onClear = viewModel::clearInbox,
                )
                1 -> friendsTab(
                    friends, busy, activity, shareActivity,
                    onShareActivity = togetherVm::setShareActivity,
                    onPlay = { togetherVm.playFriend(it) },
                    onRemove = { confirmRemove = it },
                )
                else -> requestsTab(incoming, outgoing, busy, viewModel::acceptRequest, viewModel::declineRequest) { r -> viewModel.cancelRequest(r.requestId, r.toName) }
            }
        }
    }
}

// ─────────────────────────────── Inbox ───────────────────────────────

private fun LazyListScope.inboxTab(
    inbox: List<InboxNotification>,
    progress: Map<String, SharedSongDownloadProgress>,
    unread: Int,
    onOpen: (InboxNotification) -> Unit,
    onPlay: (InboxNotification) -> Unit,
    onDownload: (InboxNotification) -> Unit,
    onDelete: (InboxNotification) -> Unit,
    onMarkAll: () -> Unit,
    onClear: () -> Unit,
    onJoin: (InboxNotification) -> Unit,
    liveHosts: Set<String>,
    onReact: (InboxNotification, String) -> Unit,
    onReply: (InboxNotification) -> Unit,
) {
    if (inbox.isEmpty()) {
        item { EmptyNote(Icons.Rounded.Inbox, "Your inbox is empty", "Songs, audiobooks and playlists your friends share will show up here.") }
        return
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (unread > 0) "$unread new" else "All caught up", color = GlassSub, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (unread > 0) TextLink("Mark all read", Icons.Rounded.DoneAll, onMarkAll)
            Spacer(Modifier.width(14.dp))
            TextLink("Clear", Icons.Rounded.Delete, onClear, GlassDanger)
        }
    }
    items(inbox, key = { "n_" + it.notifId }) { n ->
        val p = progress[n.notifId]
        GlassCard(alpha = if (n.isRead) 0.55f else 0.78f, onClick = { onOpen(n) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(n.fromAvatar, n.fromName, 26.dp, borderWidth = 1.dp)
                Spacer(Modifier.width(8.dp))
                Text(headline(n), color = GlassSub, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(timeAgo(n.sentAt), color = GlassMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                if (!n.isRead) { Spacer(Modifier.width(6.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(GlassAccent)) }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.MoreVert, "More", tint = GlassMuted, modifier = Modifier.size(18.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color.White) {
                        DropdownMenuItem(text = { Text("Delete", color = GlassDanger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = GlassDanger) }, onClick = { menu = false; onDelete(n) })
                    }
                }
            }
            when (n.type) {
                InboxNotificationType.FRIEND_ACCEPTED -> {
                    Spacer(Modifier.height(4.dp))
                    Text("You're friends now — share something with ${n.fromName.substringBefore(' ')}!", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                InboxNotificationType.LISTEN_INVITE -> {
                    Spacer(Modifier.height(10.dp))
                    val live = n.fromUid in liveHosts
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(GlassGradient), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Listen together", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text(if (live) (n.songTitle?.let { "Now: $it" } ?: "Session is live") else "This session has ended", color = GlassSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (live) GradientButton("Join", { onJoin(n) }, height = 38.dp)
                    }
                }
                InboxNotificationType.COLLAB_INVITE -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF7B6CF0).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Group, null, tint = Color(0xFF7B6CF0), modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.playlistName ?: "Group playlist", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Tap to open and add your songs", color = GlassSub, fontSize = 12.sp)
                        }
                    }
                }
                InboxNotificationType.REACTION -> {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.reaction ?: "💬", fontSize = 30.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            n.message?.let { Text("“$it”", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                            Text("on “${n.songTitle ?: "your share"}”", color = GlassSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                InboxNotificationType.PLAYLIST_SHARED -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(GlassAccent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = GlassAccent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.playlistName ?: "Playlist", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Tap to open and import", color = GlassSub, fontSize = 12.sp)
                        }
                    }
                }
                else -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(n.thumbnailUrl, Modifier.size(56.dp), seed = (n.songTitle ?: "").hashCode())
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.songTitle ?: "Shared", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (n.isAudiobook) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.MusicNote, null, tint = if (n.isAudiobook) Color(0xFF7B6CF0) else GlassAccent, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(metaLine(n), color = GlassSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    when {
                        !n.isDownloadable -> Text("This item can't be played (it was shared without a file).", color = GlassMuted, fontSize = 12.sp)
                        p?.isComplete == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = GlassAccent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text("Saved to ${if (n.isAudiobook) "Audiobooks" else "Songs"}", color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        p != null && !p.isFailed -> Column {
                            GlassProgress(p.progress / 100f, height = 6.dp)
                            Spacer(Modifier.height(4.dp))
                            Text("Downloading… ${p.status}", color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton("Play", { onPlay(n) }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, height = 40.dp)
                            GradientButton(if (p?.isFailed == true) "Retry" else "Download", { onDownload(n) }, Modifier.weight(1f), icon = Icons.Rounded.FileDownload, height = 40.dp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("❤️", "🔥", "😂", "😢", "👏").forEach { e ->
                            val mine = n.myReaction == e
                            Box(
                                Modifier.springClick(enabled = !mine) { onReact(n, e) }.size(36.dp).clip(CircleShape)
                                    .background(if (mine) GlassAccent.copy(alpha = 0.18f) else GlassInk.copy(alpha = 0.05f))
                                    ,
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 17.sp) }
                        }
                        Spacer(Modifier.weight(1f))
                        TextLink("Reply", Icons.Rounded.ChatBubbleOutline, { onReply(n) })
                    }
                }
            }
        }
    }
}

// ─────────────────────────────── Friends ───────────────────────────────

private fun LazyListScope.friendsTab(
    friends: List<Friend>,
    busy: Set<String>,
    activity: Map<String, com.waleve.player.domain.model.NowPlaying>,
    shareActivity: Boolean,
    onShareActivity: (Boolean) -> Unit,
    onPlay: (com.waleve.player.domain.model.NowPlaying) -> Unit,
    onRemove: (Friend) -> Unit,
) {
    item {
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), alpha = 0.62f, elevation = 0.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Share what I'm listening to", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Friends see your current song", color = GlassSub, fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(
                shareActivity, onShareActivity,
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = GlassAccent, checkedThumbColor = Color.White),
            )
        }
    }
    if (friends.isEmpty()) {
        item { EmptyNote(Icons.Rounded.Group, "No friends yet", "Search by name, Gmail or friend code above, or tap Invite to send your code.") }
        return
    }
    items(friends, key = { "f_" + it.uid }) { f ->
        val np = activity[f.uid]
        PersonRow(f, status = np?.let { (if (it.isPlaying) "▶ " else "Paused · ") + it.track.title + (it.track.artist.takeIf { a -> a.isNotBlank() }?.let { a -> " · $a" } ?: "") }) {
            if (np != null && np.track.playable) {
                IconButton(onClick = { onPlay(np) }) { Icon(Icons.Rounded.PlayArrow, "Play ${np.track.title}", tint = GlassAccent) }
            }
            if (f.uid in busy) CircularProgressIndicator(Modifier.size(20.dp), color = GlassAccent, strokeWidth = 2.dp)
            else Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = GlassMuted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color.White) {
                    DropdownMenuItem(
                        text = { Text("Remove friend", color = GlassDanger) },
                        leadingIcon = { Icon(Icons.Rounded.PersonRemove, null, tint = GlassDanger) },
                        onClick = { menu = false; onRemove(f) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────── Requests ───────────────────────────────

private fun LazyListScope.requestsTab(
    incoming: List<FriendRequest>,
    outgoing: List<FriendRequest>,
    busy: Set<String>,
    onAccept: (FriendRequest) -> Unit,
    onDecline: (FriendRequest) -> Unit,
    onCancel: (FriendRequest) -> Unit,
) {
    if (incoming.isEmpty() && outgoing.isEmpty()) {
        item { EmptyNote(Icons.Rounded.PersonAdd, "No pending requests", "Requests you receive or send will appear here until they're answered.") }
        return
    }
    if (incoming.isNotEmpty()) {
        item { Overline("Received · ${incoming.size}", GlassSub) }
        items(incoming, key = { "in_" + it.requestId }) { r ->
            GlassCard(alpha = 0.72f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.fromAvatar, r.fromName, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.fromName, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("wants to be friends · ${timeAgo(r.sentAt)}", color = GlassSub, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val isBusy = r.requestId in busy
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Decline", { onDecline(r) }, Modifier.weight(1f), icon = Icons.Rounded.Close, tint = GlassSub, height = 40.dp, enabled = !isBusy)
                    GradientButton("Accept", { onAccept(r) }, Modifier.weight(1f), icon = Icons.Rounded.Check, height = 40.dp, loading = isBusy)
                }
            }
        }
    }
    if (outgoing.isNotEmpty()) {
        item { Spacer(Modifier.height(4.dp)); Overline("Sent · waiting for answer", GlassSub) }
        items(outgoing, key = { "out_" + it.requestId }) { r ->
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), alpha = 0.55f, elevation = 0.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(r.toAvatar, r.toName, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.toName, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Sent ${timeAgo(r.sentAt)}", color = GlassMuted, fontSize = 12.sp)
                }
                GlassButton("Cancel", { onCancel(r) }, tint = GlassSub, height = 36.dp, enabled = r.requestId !in busy)
            }
        }
    }
}

// ─────────────────────────────── Bits ───────────────────────────────

@Composable
private fun PersonRow(f: Friend, status: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), alpha = 0.62f, elevation = 0.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(f.avatarUrl, f.displayName, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.displayName, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) Text(status, color = GlassAccent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text(listOf(f.friendCode, f.email).filter { it.isNotBlank() }.joinToString("  ·  "), color = GlassSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun EmptyNote(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), alpha = 0.5f, elevation = 0.dp).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(GlassAccent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = GlassAccent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = GlassSub, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TextLink(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, color: Color = GlassAccent) {
    Row(Modifier.springClick(onClick = onClick).clip(RoundedCornerShape(10.dp)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

private fun headline(n: InboxNotification): String = when (n.type) {
    InboxNotificationType.FRIEND_ACCEPTED -> "${n.fromName} accepted your friend request"
    InboxNotificationType.PLAYLIST_SHARED -> "${n.fromName} shared a playlist"
    InboxNotificationType.SONG_SHARED -> "${n.fromName} shared ${if (n.isAudiobook) "an audiobook" else "a song"}"
    InboxNotificationType.FRIEND_REQUEST -> "${n.fromName} sent a friend request"
    InboxNotificationType.LISTEN_INVITE -> "${n.fromName} invited you to listen together"
    InboxNotificationType.COLLAB_INVITE -> "${n.fromName} added you to a group playlist"
    InboxNotificationType.REACTION -> "${n.fromName} ${if (n.message != null) "replied" else "reacted"}"
}

@Composable
private fun TogetherTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String,
    onClick: () -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false,
    colors: List<Color> = listOf(Color(0xFF46C190), Color(0xFF2E8FA8)),
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.springClick(onClick = onClick).glass(shape, alpha = 0.78f)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(colors[0].copy(alpha = 0.16f), Color.Transparent)))
    ) {
        // soft colour glow in the corner
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 24.dp, y = (-24).dp).size(90.dp).clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(colors[1].copy(alpha = 0.22f), Color.Transparent)))
        )
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(42.dp).shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = colors[1], spotColor = colors[1])
                    .clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.height(12.dp))
            Text(title, color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (highlight) { Box(Modifier.size(7.dp).clip(CircleShape).background(GlassDanger)); Spacer(Modifier.width(5.dp)) }
                Text(sub, color = if (highlight) GlassDanger else GlassSub, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Three little bars bouncing like an equalizer (for "LIVE"). */
@Composable
private fun EqualizerBars(color: Color = Color.White) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq")
    Row(Modifier.height(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        listOf(420, 300, 520).forEachIndexed { i, ms ->
            val h by t.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(ms, delayMillis = i * 60),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(Modifier.width(2.5.dp).fillMaxHeight(h).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}
