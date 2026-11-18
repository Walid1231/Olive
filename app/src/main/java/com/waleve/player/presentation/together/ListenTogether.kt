package com.waleve.player.presentation.together

import android.widget.Toast
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
                Modifier.fillMaxWidth().clickable(enabled = !off) { onToggle(f.uid) }.padding(horizontal = 14.dp, vertical = 9.dp),
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
    TogetherToasts(vm)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFFAF9FD),
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
                        Overline("Listening with ${g.hostName}")
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
                        Text("${g.hostName} controls play, pause and skip. Picking another song yourself leaves the session.", color = GlassMuted, fontSize = 12.sp)
                    }
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
                    GradientButton(
                        if (picked.isEmpty()) "Pick friends to invite" else "Start with ${picked.size} friend${if (picked.size == 1) "" else "s"}",
                        { vm.start(picked.toList()); picked = emptySet() },
                        Modifier.fillMaxWidth(), icon = Icons.Rounded.Headphones, enabled = picked.isNotEmpty() && !busy, height = 48.dp,
                    )
                    Text("Tip: local songs are sent to friends automatically (up to 50 MB each).", color = GlassMuted, fontSize = 12.sp)
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
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp))
                .background(GlassGradientH).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
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
            Text("Manage", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(0.dp))
}
