package com.waleve.player.presentation.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.InboxNotification
import com.waleve.player.domain.model.InboxNotificationType
import com.waleve.player.presentation.theme.*
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    isNightMode: Boolean,
    onOpenSharedPlaylist: (shareId: String) -> Unit,
    onPlaySharedSong: (InboxNotification) -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    val gc = LocalGhibliColors.current
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val outgoingRequests by viewModel.outgoingRequests.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val events = viewModel.events

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Collect events
    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is FriendsUiEvent.Success -> snackbarHostState.showSnackbar(event.message)
                is FriendsUiEvent.Error   -> snackbarHostState.showSnackbar("⚠ ${event.message}")
            }
        }
    }

    val friendRequestCount = inbox.count {
        it.type == InboxNotificationType.FRIEND_REQUEST && !it.isRead
    }
    val sharedPlaylistCount = inbox.count {
        it.type == InboxNotificationType.PLAYLIST_SHARED && !it.isRead
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colorStops = if (isNightMode) arrayOf(
                                // Solid dark floor through the title + tab strip,
                                // then a clean fade into the scrollable content.
                                0.00f to Color(0xFF060D1E),
                                0.72f to Color(0xFF060D1E),
                                1.00f to Color.Transparent,
                            ) else arrayOf(
                                0.00f to GhibliDayBg,
                                0.72f to GhibliDayBg,
                                1.00f to Color.Transparent,
                            )
                        )
                    )
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Friends",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = gc.textPrimary,
                        ),
                    )
                    Row {
                        IconButton(onClick = { showSearch = !showSearch; if (!showSearch) { searchQuery = ""; viewModel.clearSearch() } }) {
                            Icon(
                                imageVector = if (showSearch) Icons.Rounded.Close else Icons.Rounded.PersonSearch,
                                contentDescription = "Search users",
                                tint = gc.accent,
                            )
                        }
                        IconButton(onClick = onOpenProfile) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = "My Profile",
                                tint = gc.accent,
                            )
                        }
                    }
                }

                // Search bar
                AnimatedVisibility(visible = showSearch, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; viewModel.smartSearch(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = {
                            Text(
                                when (searchMode) {
                                    SearchMode.FRIEND_CODE -> "Enter friend code (WLEV-XXXX)"
                                    SearchMode.EMAIL -> "Search by email…"
                                    SearchMode.NAME -> "Search by name, code, or email…"
                                },
                                color = gc.textSecondary,
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = gc.accent) },
                        trailingIcon = {
                            if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = gc.accent, strokeWidth = 2.dp)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor     = gc.accent,
                            unfocusedBorderColor   = gc.textSecondary.copy(alpha = 0.3f),
                            focusedTextColor       = gc.textPrimary,
                            unfocusedTextColor     = gc.textPrimary,
                            cursorColor            = gc.accent,
                            focusedContainerColor  = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedPlaceholderColor = gc.textSecondary,
                            unfocusedPlaceholderColor = gc.textSecondary,
                        ),
                    )
                }

                // ── Glassmorphism segmented tab control ──────────────────
                // WCAG-compliant: active #7EEDB8 (5.2:1), inactive #8AA8C0 (4.7:1)
                val tabActiveText   = if (isNightMode) Color(0xFF7EEDB8) else GhibliForestDeep
                val tabInactiveText = if (isNightMode) Color(0xFF8AA8C0) else Color(0xFF5A7E6E)
                val tabActivePillBg = gc.accent.copy(alpha = if (isNightMode) 0.18f else 0.14f)
                val tabActivePillBorder = gc.accent.copy(alpha = if (isNightMode) 0.32f else 0.28f)
                val stripBg     = Color.White.copy(alpha = if (isNightMode) 0.06f else 0.55f)
                val stripBorder = Color.White.copy(alpha = if (isNightMode) 0.10f else 0.70f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(stripBg)
                        .border(
                            width = 1.dp,
                            color = stripBorder,
                            shape = RoundedCornerShape(14.dp),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                    ) {
                        listOf("Friends", "Requests", "Inbox").forEachIndexed { i, title ->
                            val isSelected = selectedTab == i
                            val badgeCount = when (i) {
                                1 -> friendRequestCount
                                2 -> sharedPlaylistCount
                                else -> 0
                            }
                            // Animate pill background alpha for smooth switching
                            val pillAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0f,
                                animationSpec = tween(250),
                                label = "tab_pill_$i",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tabActivePillBg.copy(alpha = tabActivePillBg.alpha * pillAlpha))
                                    .then(
                                        if (isSelected) Modifier.border(
                                            width = 1.dp,
                                            color = tabActivePillBorder.copy(alpha = tabActivePillBorder.alpha * pillAlpha),
                                            shape = RoundedCornerShape(10.dp),
                                        ) else Modifier
                                    )
                                    .clickable { selectedTab = i }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (badgeCount > 0) Badge(
                                            containerColor = gc.accent,
                                            contentColor   = if (isNightMode) gc.surface else Color.White,
                                        ) { Text("$badgeCount") }
                                    }
                                ) {
                                    Text(
                                        text       = title,
                                        color      = if (isSelected) tabActiveText else tabInactiveText,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        fontSize   = 13.sp,
                                        letterSpacing = 0.3.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Show search results overlay
            if (showSearch && searchQuery.length >= 2) {
                SearchResultsOverlay(
                    results       = searchResults,
                    friends       = friends,
                    outgoingUids  = outgoingRequests.map { it.toUid }.toSet(),
                    currentUid    = viewModel.currentUid ?: "",
                    isLoading     = isLoading,
                    gc            = gc,
                    onAddFriend   = { friend -> viewModel.sendFriendRequest(friend.uid, friend.displayName) },
                )
            } else {
                when (selectedTab) {
                    0 -> FriendsTab(friends = friends, gc = gc, onRemove = viewModel::removeFriend)
                    1 -> RequestsTab(inbox = inbox.filter { it.type == InboxNotificationType.FRIEND_REQUEST }, gc = gc, onAccept = viewModel::acceptFriendRequest, onDecline = viewModel::declineFriendRequest)
                    2 -> InboxTab(
                        inbox = inbox.filter { 
                            it.type == InboxNotificationType.PLAYLIST_SHARED || 
                            it.type == InboxNotificationType.SONG_SHARED 
                        }, 
                        downloadProgress = viewModel.downloadProgress.collectAsStateWithLifecycle().value,
                        gc = gc, 
                        onOpenPlaylist = { notif ->
                            notif.shareId?.let { shareId ->
                                viewModel.markNotificationRead(notif.notifId)
                                onOpenSharedPlaylist(shareId)
                            }
                        },
                        onDownloadSong = { notif ->
                            viewModel.markNotificationRead(notif.notifId)
                            viewModel.downloadSharedSong(notif)
                        },
                        onPlaySong = { notif ->
                            viewModel.markNotificationRead(notif.notifId)
                            onPlaySharedSong(notif)
                        }
                    )
                }
            }
        }
    }
}

// ─── Search Results Overlay ───────────────────────────────────────────────────
@Composable
private fun SearchResultsOverlay(
    results: List<Friend>,
    friends: List<Friend>,
    outgoingUids: Set<String>,
    currentUid: String,
    isLoading: Boolean,
    gc: GhibliColors,
    onAddFriend: (Friend) -> Unit,
) {
    val friendUids = friends.map { it.uid }.toSet()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (results.isEmpty()) {
            item {
                Text("No users found. Try a different name.", color = gc.textSecondary, modifier = Modifier.padding(top = 24.dp))
            }
        } else {
            items(results, key = { it.uid }) { friend ->
                val isFriend  = friend.uid in friendUids
                val isPending = friend.uid in outgoingUids
                UserSearchCard(
                    friend    = friend,
                    isFriend  = isFriend,
                    isPending = isPending,
                    isLoading = isLoading,
                    gc        = gc,
                    onAdd     = { onAddFriend(friend) },
                )
            }
        }
    }
}

@Composable
private fun UserSearchCard(
    friend: Friend,
    isFriend: Boolean,
    isPending: Boolean,
    isLoading: Boolean,
    gc: GhibliColors,
    onAdd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = gc.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Avatar circle
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(gc.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = friend.displayName.take(1).uppercase(),
                    color = gc.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.displayName, color = gc.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(friend.email, color = gc.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                isFriend  -> Icon(Icons.Rounded.CheckCircle, null, tint = gc.accent)
                isPending -> OutlinedButton(onClick = {}, enabled = false, shape = RoundedCornerShape(12.dp)) {
                    Text("Pending", color = gc.textSecondary, fontSize = 12.sp)
                }
                else -> Button(
                    onClick = onAdd,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = gc.accent, contentColor = gc.surface),
                ) {
                    Icon(Icons.Rounded.PersonAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", fontSize = 13.sp)
                }
            }
        }
    }
}

// ─── Friends Tab ─────────────────────────────────────────────────────────────
@Composable
private fun FriendsTab(friends: List<Friend>, gc: GhibliColors, onRemove: (Friend) -> Unit) {
    if (friends.isEmpty()) {
        EmptyState(icon = Icons.Rounded.Group, message = "No friends yet. Search for users to add!", gc = gc)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(friends, key = { it.uid }) { friend ->
            var showRemoveDialog by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = gc.surfaceCard),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(gc.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(friend.displayName.take(1).uppercase(), color = gc.accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(friend.displayName, color = gc.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(friend.email, color = gc.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { showRemoveDialog = true }) {
                        Icon(Icons.Rounded.PersonRemove, "Remove friend", tint = ErrorRed)
                    }
                }
            }
            if (showRemoveDialog) {
                AlertDialog(
                    onDismissRequest = { showRemoveDialog = false },
                    title = { Text("Remove Friend") },
                    text  = { Text("Remove ${friend.displayName} from your friends?") },
                    confirmButton = {
                        TextButton(onClick = { onRemove(friend); showRemoveDialog = false }) {
                            Text("Remove", color = ErrorRed)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
                    },
                    containerColor = gc.surfaceCard,
                )
            }
        }
    }
}

// ─── Requests Tab ────────────────────────────────────────────────────────────
@Composable
private fun RequestsTab(
    inbox: List<InboxNotification>,
    gc: GhibliColors,
    onAccept: (InboxNotification) -> Unit,
    onDecline: (InboxNotification) -> Unit,
) {
    if (inbox.isEmpty()) {
        EmptyState(icon = Icons.Rounded.MailOutline, message = "No pending friend requests", gc = gc)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(inbox, key = { it.notifId }) { notif ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = gc.surfaceCard),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(GhibliSkyBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(notif.fromName.take(1).uppercase(), color = GhibliSkyBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column {
                            Text(notif.fromName, color = gc.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Sent you a friend request", color = gc.textSecondary, fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onAccept(notif) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = gc.accent, contentColor = gc.surface),
                        ) {
                            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Accept")
                        }
                        OutlinedButton(
                            onClick = { onDecline(notif) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                        ) {
                            Text("Decline")
                        }
                    }
                }
            }
        }
    }
}

// ─── Inbox Tab ───────────────────────────────────────────────────────────────
@Composable
private fun InboxTab(
    inbox: List<InboxNotification>,
    downloadProgress: Map<String, SharedSongDownloadProgress>,
    gc: GhibliColors,
    onOpenPlaylist: (InboxNotification) -> Unit,
    onDownloadSong: (InboxNotification) -> Unit,
    onPlaySong: (InboxNotification) -> Unit = {},
) {
    if (inbox.isEmpty()) {
        EmptyState(icon = Icons.Rounded.LibraryMusic, message = "No shared playlists yet", gc = gc)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(inbox, key = { it.notifId }) { notif ->
            val isPlaylist = notif.type == InboxNotificationType.PLAYLIST_SHARED
            val isSong = notif.type == InboxNotificationType.SONG_SHARED
            
            val progress = downloadProgress[notif.notifId]
            val isDownloading = progress != null && !progress.isComplete && !progress.isFailed
            val isComplete = progress?.isComplete == true
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isPlaylist) { if (isPlaylist) onOpenPlaylist(notif) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (notif.isRead) gc.surfaceCard else gc.accent.copy(alpha = 0.08f)
                ),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Icon / Thumbnail
                        if (isSong && !notif.thumbnailUrl.isNullOrEmpty()) {
                            coil.compose.AsyncImage(
                                model = notif.thumbnailUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                    .background(gc.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isPlaylist) Icons.AutoMirrored.Rounded.PlaylistPlay else Icons.Rounded.MusicNote, 
                                    contentDescription = null, 
                                    tint = gc.accent, 
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPlaylist) (notif.playlistName ?: "Shared Playlist") else (notif.songTitle ?: "Shared Song"),
                                color = gc.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Shared by ${notif.fromName}",
                                color = gc.textSecondary,
                                fontSize = 12.sp,
                            )
                            if (isDownloading && progress != null) {
                                Text(progress.status, color = gc.accent, fontSize = 11.sp)
                            }
                        }
                        
                        if (!notif.isRead) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(gc.accent))
                        }
                        
                        if (isPlaylist) {
                            Icon(Icons.Rounded.ChevronRight, null, tint = gc.textSecondary)
                        } else if (isSong) {
                            when {
                                isComplete -> Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(28.dp))
                                isDownloading -> CircularProgressIndicator(modifier = Modifier.size(28.dp), color = gc.accent, strokeWidth = 3.dp)
                                else -> Row {
                                    IconButton(onClick = { onPlaySong(notif) }) {
                                        Icon(Icons.Rounded.PlayArrow, "Play", tint = gc.accent)
                                    }
                                    IconButton(onClick = { onDownloadSong(notif) }) {
                                        Icon(Icons.Rounded.FileDownload, "Download", tint = gc.accent)
                                    }
                                }
                            }
                        }
                    }
                    
                    if (isDownloading && progress != null && progress.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { progress.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = gc.accent,
                            trackColor = gc.accent.copy(alpha = 0.15f),
                        )
                    }
                }
            }
        }
    }
}

// ─── Empty State ─────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    gc: GhibliColors,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = gc.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
            Text(message, color = gc.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}
