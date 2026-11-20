package com.waleve.player.presentation.together

import android.widget.Toast
import com.waleve.player.presentation.components.springClick
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.ListenSession
import com.waleve.player.presentation.components.Artwork
import com.waleve.player.presentation.components.Avatar
import com.waleve.player.presentation.components.GlassButton
import com.waleve.player.presentation.components.GlassGradient
import com.waleve.player.presentation.components.GlassGradientH
import com.waleve.player.presentation.components.GradientButton
import com.waleve.player.presentation.components.Overline
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub

/** Shows errors + repository events from [TogetherViewModel] as toasts. */
@Composable
fun TogetherToasts(vm: TogetherViewModel) {
    val ctx = LocalContext.current
    val error by vm.error.collectAsStateWithLifecycle()
    LaunchedEffect(error) { error?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.clearError() } }
    LaunchedEffect(Unit) { vm.events.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
}

/** Multi-select list of friends. [disabled] are shown as already added. */
@Composable
fun FriendPicker(
    friends: List<Friend>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    disabled: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    if (friends.isEmpty()) {
        Box(modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), alpha = 0.9f).padding(18.dp), contentAlignment = Alignment.Center) {
            Text("Add friends first (Friends → search by name or code).", color = GlassSub, fontSize = 13.sp)
        }
        return
    }
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.9f).padding(vertical = 4.dp)) {
        friends.forEach { f ->
            val off = f.uid in disabled
            val on = f.uid in selected || off
            Row(
                Modifier.springClick(enabled = !off) { onToggle(f.uid) }.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(f.avatarUrl, f.displayName, 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(f.displayName, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (off) Text("Added", color = GlassMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                else Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .then(if (on) Modifier.background(GlassGradient) else Modifier.background(GlassInk.copy(alpha = 0.08f))),
                    contentAlignment = Alignment.Center,
                ) { if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

/** Avatars of everyone in the session, overlapped. */
@Composable
private fun MemberStack(s: ListenSession, friends: List<Friend>) {
    val avatars = friends.associate { it.uid to it.avatarUrl }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shown = s.members.take(6)
        Box(Modifier.width((34 + 24 * (shown.size - 1).coerceAtLeast(0)).dp).height(34.dp)) {
            shown.forEachIndexed { i, uid ->
                val name = s.memberNames[uid] ?: "?"
                Avatar(if (uid == s.hostUid) s.hostAvatar else avatars[uid], name, 34.dp, Modifier.offset(x = (24 * i).dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            s.members.mapNotNull { s.memberNames[it] }.joinToString(", "),
            color = GlassSub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Listen Together: start a session and invite friends, see who's listening, join an invite, leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSheet(onDismiss: () -> Unit, vm: TogetherViewModel = hiltViewModel()) {
    val friends by vm.friends.collectAsStateWithLifecycle()
    val invites by vm.invites.collectAsStateWithLifecycle()
    val hosting by vm.hosting.collectAsStateWithLifecycle()
    val guest by vm.guest.collectAsStateWithLifecycle()
    val upload by vm.hostUpload.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf(setOf<String>()) }
    var inviting by remember { mutableStateOf(false) }
    var openControl by remember { mutableStateOf(false) }
    val chat by vm.chat.collectAsStateWithLifecycle()
    val liveState by vm.liveState.collectAsStateWithLifecycle()
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.setLive(true) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    TogetherToasts(vm)
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.setChatOpen(true)
        onDispose { vm.setChatOpen(false) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = com.waleve.player.presentation.theme.GlassPanel,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(GlassGradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Listen together", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("Same song, same second, on every phone.", color = GlassSub, fontSize = 13.sp)
                }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = GlassAccent, strokeWidth = 2.dp)
            }

            val h = hosting
            val g = guest
            when {
                // ── I'm hosting ──
                h != null -> {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), alpha = 0.9f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Overline("You're hosting")
                        h.track?.let { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Artwork(t.artUrl, Modifier.size(52.dp), corner = 12.dp, seed = t.title.hashCode())
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        when {
                                            upload != null -> "Sending the file to friends… ${upload!!.toInt()}%"
                                            h.preparing -> "Getting ready…"
                                            else -> t.artist
                                        },
                                        color = GlassSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } ?: Text("Play any song — everyone in the session hears it.", color = GlassSub, fontSize = 13.sp)
                        MemberStack(h, friends)
                        val waiting = h.invited.filter { it !in h.members }
                        if (waiting.isNotEmpty()) {
                            Text("Waiting for ${waiting.size} friend${if (waiting.size == 1) "" else "s"} to join", color = GlassMuted, fontSize = 12.sp)
                        }
                    }
                    val liveSub = when (val ls = liveState) {
                        is com.waleve.player.service.live.LiveStreamer.State.Connecting -> "Connecting…"
                        is com.waleve.player.service.live.LiveStreamer.State.Live -> "On air · ${ls.listeners} listening · no downloads, perfect sync"
                        is com.waleve.player.service.live.LiveStreamer.State.Failed -> ls.message
                        else -> "Friends hear exactly what you play — instantly, local files too"
                    }
                    ToggleRow("Live stream", liveSub, h.live) { on ->
                        if (!on) vm.setLive(false)
                        else if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED) vm.setLive(true)
                        else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                    if (h.live) {
                        Text(
                            "Android shows the mic dot while you're live — Olive sends only your music, never your voice.",
                            color = GlassMuted, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                    ToggleRow("Everyone can control", "Friends can play, pause, skip and pick songs for all", h.openControl) { vm.setOpenControl(it) }
                    ChatPanel(chat, vm.myUid, vm::send)
                    AnimatedVisibility(inviting, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FriendPicker(friends, picked, { id -> picked = if (id in picked) picked - id else picked + id }, disabled = h.invited.toSet())
                            GradientButton("Send invites", { vm.inviteMore(picked.toList()); picked = emptySet(); inviting = false }, Modifier.fillMaxWidth(), enabled = picked.isNotEmpty(), height = 46.dp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!inviting) GlassButton("Invite more", { inviting = true }, Modifier.weight(1f), icon = Icons.Rounded.PersonAdd, height = 46.dp)
                        GlassButton("End session", { vm.end() }, Modifier.weight(1f), icon = Icons.Rounded.Close, tint = GlassDanger, height = 46.dp)
                    }
                }

                // ── I'm a guest ──
                g != null -> {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), alpha = 0.9f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Overline(if (g.live) "Live with ${g.hostName}" else "Listening with ${g.hostName}")
                        if (g.live) {
                            val ls = liveState
                            Text(
                                when (ls) {
                                    is com.waleve.player.service.live.LiveStreamer.State.Live -> "● On air — you hear ${g.hostName}'s phone live"
                                    is com.waleve.player.service.live.LiveStreamer.State.Failed -> ls.message
                                    else -> "Tuning in…"
                                },
                                color = if (ls is com.waleve.player.service.live.LiveStreamer.State.Live) GlassAccent else GlassSub,
                                fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
                            )
                        }
                        g.track?.let { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Artwork(t.artUrl, Modifier.size(52.dp), corner = 12.dp, seed = t.title.hashCode())
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (g.preparing) "${g.hostName} is getting the next song ready…" else t.artist, color = GlassSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        MemberStack(g, friends)
                        if (g.openControl) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                RoundControl(Icons.Rounded.SkipPrevious, "Previous") { vm.guestControl("prev") }
                                Spacer(Modifier.width(18.dp))
                                RoundControl(if (g.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play / pause", big = true) {
                                    vm.guestControl(if (g.isPlaying) "pause" else "play")
                                }
                                Spacer(Modifier.width(18.dp))
                                RoundControl(Icons.Rounded.SkipNext, "Next") { vm.guestControl("next") }
                            }
                            Text("Everyone can control. Play any song from your library and it plays for the whole group.", color = GlassMuted, fontSize = 12.sp)
                        } else {
                            Text("${g.hostName} controls play, pause and skip. Picking another song yourself leaves the session.", color = GlassMuted, fontSize = 12.sp)
                        }
                    }
                    ChatPanel(chat, vm.myUid, vm::send)
                    GlassButton("Leave session", { vm.leave() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Close, tint = GlassDanger, height = 46.dp)
                }

                // ── Not in a session ──
                else -> {
                    if (invites.isNotEmpty()) {
                        Overline("Invitations")
                        invites.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.9f).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(s.hostAvatar, s.hostName, 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${s.hostName}'s session", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.track?.let { "▶ ${it.title}" } ?: "${s.members.size} listening", color = GlassSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                GradientButton("Join", { vm.join(s.hostUid) }, height = 38.dp)
                            }
                        }
                    }
                    Overline("Start a session")
                    FriendPicker(friends, picked, { id -> picked = if (id in picked) picked - id else picked + id })
                    ToggleRow("Everyone can control", "Friends can play, pause, skip and pick songs too", openControl) { openControl = it }
                    GradientButton(
                        if (picked.isEmpty()) "Pick friends to invite" else "Start with ${picked.size} friend${if (picked.size == 1) "" else "s"}",
                        { vm.start(picked.toList(), openControl); picked = emptySet() },
                        Modifier.fillMaxWidth(), icon = Icons.Rounded.Headphones, enabled = picked.isNotEmpty() && !busy, height = 48.dp,
                    )
                    Text("Tip: local songs are sent to friends automatically (up to 50 MB each).", color = GlassMuted, fontSize = 12.sp)
                    val history = remember { vm.chatHistory() }
                    if (history.isNotEmpty()) ChatHistory(history, vm.myUid)
                }
            }
        }
    }
}

/** Thin bar above the mini player while you're in a session. */
@Composable
fun TogetherBanner(onClick: () -> Unit, modifier: Modifier = Modifier, vm: TogetherViewModel = hiltViewModel()) {
    val hosting by vm.hosting.collectAsStateWithLifecycle()
    val guest by vm.guest.collectAsStateWithLifecycle()
    val s = hosting ?: guest
    AnimatedVisibility(s != null, modifier = modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val session = s ?: return@AnimatedVisibility
        val others = session.members.size - 1
        Row(
            Modifier.springClick(onClick = onClick).fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp))
                .background(GlassGradientH).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (hosting != null) (if (others > 0) "Listening together · $others friend${if (others == 1) "" else "s"} with you" else "Session on · waiting for friends")
                else "Listening with ${session.hostName}",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val unread by vm.chatUnread.collectAsStateWithLifecycle()
            if (unread > 0) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("💬 $unread", color = GlassAccent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(6.dp))
            }
            Text("Open", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), alpha = 0.9f).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(sub, color = GlassSub, fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(
            on, onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = GlassAccent, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) {
    val size = if (big) 56.dp else 44.dp
    Box(
        Modifier.springClick(onClick = onClick).size(size).clip(CircleShape)
            .then(if (big) Modifier.background(GlassGradient) else Modifier.background(GlassInk.copy(alpha = 0.07f))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (big) Color.White else GlassInk, modifier = Modifier.size(if (big) 28.dp else 22.dp)) }
}

private fun clock(at: Long): String = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(at))

@Composable
private fun Bubble(m: com.waleve.player.domain.model.ChatMessage, mine: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine) Text(m.name.substringBefore(' '), color = GlassSub, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
        Box(
            Modifier.widthIn(max = 260.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 6.dp, bottomEnd = if (mine) 6.dp else 18.dp))
                .then(if (mine) Modifier.background(GlassGradientH) else Modifier.background(com.waleve.player.presentation.theme.GlassMenu))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val emojiOnly = m.text.length <= 4 && m.text.none { it.isLetterOrDigit() }
            Text(m.text, color = if (mine) Color.White else GlassInk, fontSize = if (emojiOnly) 24.sp else 14.sp)
        }
        Text(clock(m.at), color = GlassMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

/** Little chat for the people in the session. Saved on this phone only. */
@Composable
private fun ChatPanel(messages: List<com.waleve.player.domain.model.ChatMessage>, myUid: String?, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), alpha = 0.9f).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chat", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("saved on your phone", color = GlassMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (messages.isEmpty()) {
            Text("Say hi 👋 — or react to the song", color = GlassMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages.size, key = { messages[it].id }) { i -> Bubble(messages[i], messages[i].uid == myUid) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("❤️", "🔥", "😂", "😭", "👏", "🎶").forEach { e ->
                Box(
                    Modifier.springClick { onSend(e) }.size(34.dp).clip(CircleShape).background(GlassInk.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 16.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(com.waleve.player.presentation.theme.GlassMenu)
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                if (text.isEmpty()) Text("Message…", color = GlassMuted, fontSize = 14.sp)
                androidx.compose.foundation.text.BasicTextField(
                    value = text, onValueChange = { if (it.length <= 300) text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.waleve.player.presentation.theme.OutfitFamily, color = GlassInk, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(GlassAccent),
                    maxLines = 3, modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.springClick(enabled = text.isNotBlank()) { onSend(text); text = "" }
                    .size(44.dp).clip(CircleShape)
                    .background(if (text.isNotBlank()) GlassGradient else androidx.compose.ui.graphics.SolidColor(GlassInk.copy(alpha = 0.1f))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Chats from earlier sessions (kept on this phone). */
@Composable
private fun ChatHistory(history: List<com.waleve.player.domain.model.ChatLog>, myUid: String?) {
    var open by remember { mutableStateOf<String?>(null) }
    Overline("Earlier chats")
    history.take(5).forEach { log ->
        val expanded = open == log.key
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), alpha = 0.85f)
                .springClick { open = if (expanded) null else log.key }.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${log.hostName}'s session", color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(log.startedAt)) + " · ${log.messages.size} messages",
                        color = GlassSub, fontSize = 12.sp,
                    )
                }
                Text(if (expanded) "Hide" else "Show", color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    log.messages.takeLast(60).forEach { Bubble(it, it.uid == myUid) }
                }
            }
        }
    }
}
