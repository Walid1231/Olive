package com.waleve.player.data.social

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.waleve.player.domain.model.ListenSession
import com.waleve.player.domain.model.NowPlaying
import com.waleve.player.domain.model.RemoteTrack
import com.waleve.player.domain.model.Song
import com.waleve.player.service.PlaybackController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Friend activity ("what my friends are playing") and Listen Together sessions.
 *
 * Firestore:
 *  users/{uid}.nowPlaying   – my current track (if I allow sharing activity)
 *  sessions/{hostUid}       – a Listen Together session; the host writes the track + play state,
 *                             guests add / remove only themselves in `members`.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class TogetherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val player: PlaybackController,
    private val resolver: RemoteMediaResolver,
    private val chatStore: ChatStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs by lazy { context.getSharedPreferences("together", Context.MODE_PRIVATE) }
    private val users get() = firestore.collection("users")
    private val sessions get() = firestore.collection("sessions")
    private fun inbox(uid: String) = users.document(uid).collection("inbox")
    private val uid get() = auth.currentUser?.uid

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()
    private fun say(msg: String) { _events.tryEmit(msg) }

    // ─── Sign-in awareness ───────────────────────────────────────────────────
    private fun uidFlow(): Flow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }.distinctUntilChanged()

    // ─── Activity sharing (privacy switch) ───────────────────────────────────
    private val _shareActivity = MutableStateFlow(true)
    val shareActivity: StateFlow<Boolean> = _shareActivity.asStateFlow()

    fun setShareActivity(on: Boolean) {
        _shareActivity.value = on
        prefs.edit().putBoolean("share_activity", on).apply()
        if (!on) clearPresence()
    }

    private var started = false

    /** Start publishing my now-playing. Safe to call many times. */
    fun start() {
        if (started) return
        started = true
        _shareActivity.value = prefs.getBoolean("share_activity", true)
        scope.launch {
            combine(
                uidFlow(),
                player.playerState.map { it.currentSong to it.isPlaying }
                    .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second },
                shareActivity,
            ) { u, sp, share -> Triple(u, sp, share) }
                .debounce(1500)
                .collect { (u, sp, share) ->
                    if (u == null || !share) return@collect
                    val (song, playing) = sp
                    runCatching {
                        if (song == null) {
                            users.document(u).update("nowPlaying", FieldValue.delete()).await()
                        } else {
                            val t = resolver.quick(song)
                            users.document(u).set(
                                mapOf("nowPlaying" to t.toMap() + mapOf("isPlaying" to playing, "updatedAt" to System.currentTimeMillis())),
                                SetOptions.merge(),
                            ).await()
                        }
                    }
                }
        }
    }

    private fun clearPresence() {
        val u = uid ?: return
        scope.launch { runCatching { users.document(u).update("nowPlaying", FieldValue.delete()).await() } }
    }

    private fun parseNowPlaying(doc: DocumentSnapshot): NowPlaying? {
        val m = doc.get("nowPlaying") as? Map<*, *> ?: return null
        val t = RemoteTrack.fromMap(m) ?: return null
        val at = (m["updatedAt"] as? Number)?.toLong() ?: 0L
        val playing = m["isPlaying"] as? Boolean ?: false
        val age = System.currentTimeMillis() - at
        // Hide stale entries: paused for 30+ min, or "playing" but not updated for 12 h (phone died)
        if ((!playing && age > 30 * 60_000) || age > 12 * 3_600_000) return null
        return NowPlaying(t, playing, at)
    }

    /** uid → what that friend is playing now. Live. */
    fun observeFriendActivity(): Flow<Map<String, NowPlaying>> = uidFlow().flatMapLatest { me ->
        if (me == null) flowOf(emptyMap()) else callbackFlow {
            val current = mutableMapOf<String, NowPlaying>()
            val listeners = mutableMapOf<String, ListenerRegistration>()
            val own = users.document(me).addSnapshotListener { snap, _ ->
                @Suppress("UNCHECKED_CAST")
                val ids = (snap?.get("friendIds") as? List<String>).orEmpty().toSet()
                (listeners.keys - ids).forEach { gone -> listeners.remove(gone)?.remove(); current.remove(gone) }
                (ids - listeners.keys).forEach { id ->
                    listeners[id] = users.document(id).addSnapshotListener { d, _ ->
                        val np = d?.let(::parseNowPlaying)
                        if (np == null) current.remove(id) else current[id] = np
                        trySend(current.toMap())
                    }
                }
                trySend(current.toMap())
            }
            awaitClose { own.remove(); listeners.values.forEach { it.remove() } }
        }
    }

    // ─── Sessions: parsing ───────────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    private fun parseSession(doc: DocumentSnapshot): ListenSession? {
        if (!doc.exists()) return null
        return ListenSession(
            hostUid = doc.getString("hostUid") ?: doc.id,
            hostName = doc.getString("hostName") ?: "Friend",
            hostAvatar = doc.getString("hostAvatar")?.takeIf { it.isNotBlank() },
            active = doc.getBoolean("active") ?: false,
            invited = doc.get("invited") as? List<String> ?: emptyList(),
            members = doc.get("members") as? List<String> ?: emptyList(),
            memberNames = (doc.get("memberNames") as? Map<String, String>) ?: emptyMap(),
            track = RemoteTrack.fromMap(doc.get("track") as? Map<*, *>),
            preparing = doc.getBoolean("preparing") ?: false,
            isPlaying = doc.getBoolean("isPlaying") ?: false,
            positionMs = doc.getLong("positionMs") ?: 0L,
            updatedAt = doc.getLong("updatedAt") ?: 0L,
            openControl = doc.getBoolean("openControl") ?: false,
            startedAt = doc.getLong("startedAt") ?: 0L,
        )
    }

    /** Active sessions I've been invited to (not my own). */
    fun observeInvites(): Flow<List<ListenSession>> = uidFlow().flatMapLatest { me ->
        if (me == null) flowOf(emptyList()) else callbackFlow {
            // array-contains only (no composite index needed); "active" is checked here
            val l = sessions.whereArrayContains("invited", me)
                .addSnapshotListener { snap, _ ->
                    val list = snap?.documents?.mapNotNull(::parseSession)
                        ?.filter { it.active && it.hostUid != me && System.currentTimeMillis() - it.updatedAt < 2 * 3_600_000 }
                        .orEmpty()
                    trySend(list)
                }
            awaitClose { l.remove() }
        }
    }

    // ─── Hosting ─────────────────────────────────────────────────────────────
    private val _hosting = MutableStateFlow<ListenSession?>(null)
    val hosting: StateFlow<ListenSession?> = _hosting.asStateFlow()
    private val _hostUpload = MutableStateFlow<Float?>(null)
    /** 0..100 while the host's local song is being uploaded for guests. */
    val hostUpload: StateFlow<Float?> = _hostUpload.asStateFlow()
    private var hostJob: Job? = null
    private var hostListener: ListenerRegistration? = null

    suspend fun startSession(friendUids: List<String>, openControl: Boolean = false): Result<Unit> = runCatching {
        val me = uid ?: error("Please sign in first")
        if (friendUids.isEmpty()) error("Pick at least one friend")
        leaveSession(silent = true)
        val myDoc = users.document(me).get().await()
        val name = myDoc.getString("displayName") ?: "Friend"
        val avatar = myDoc.getString("avatarUrl") ?: ""
        val now = System.currentTimeMillis()
        sessions.document(me).set(mapOf(
            "hostUid" to me, "hostName" to name, "hostAvatar" to avatar, "active" to true,
            "invited" to friendUids.distinct(), "members" to listOf(me), "memberNames" to mapOf(me to name),
            "preparing" to true, "isPlaying" to false, "positionMs" to 0L,
            "startedAt" to now, "updatedAt" to now, "openControl" to openControl,
        )).await()
        sendInvites(me, name, avatar, friendUids)
        runHost(me)
        attachChat(me, now, name)
    }

    /** Host: let guests control playback (and pick songs) too — or not. */
    suspend fun setOpenControl(on: Boolean) {
        val me = uid ?: return
        if (_hosting.value == null) return
        runCatching { sessions.document(me).update("openControl", on).await() }
    }

    suspend fun inviteMore(friendUids: List<String>): Result<Unit> = runCatching {
        val me = uid ?: error("Please sign in first")
        val s = _hosting.value ?: error("Start a session first")
        val fresh = friendUids.filter { it !in s.invited }
        if (fresh.isEmpty()) return@runCatching
        sessions.document(me).update("invited", FieldValue.arrayUnion(*fresh.toTypedArray())).await()
        sendInvites(me, s.hostName, s.hostAvatar ?: "", fresh)
    }

    private suspend fun sendInvites(me: String, name: String, avatar: String, to: List<String>) {
        val title = player.playerState.value.currentSong?.title
        val batch = firestore.batch()
        to.forEach { f ->
            val ref = inbox(f).document()
            batch.set(ref, buildMap {
                put("notifId", ref.id); put("type", "LISTEN_INVITE"); put("fromUid", me)
                put("fromName", name); put("fromAvatar", avatar); put("shareId", me)
                put("sentAt", System.currentTimeMillis()); put("isRead", false)
                title?.let { put("songTitle", it) }
            })
        }
        batch.commit().await()
    }

    private fun runHost(me: String) {
        hostJob?.cancel()
        hostListener?.remove()
        val ref = sessions.document(me)
        var lastCommand: String? = null
        hostListener = ref.addSnapshotListener { snap, _ ->
            val s = snap?.let(::parseSession)
            _hosting.value = s?.takeIf { it.active }
            // A guest pressed something (only when "Everyone can control" is on)
            val cmd = snap?.get("command") as? Map<*, *> ?: return@addSnapshotListener
            val id = cmd["id"] as? String ?: return@addSnapshotListener
            if (lastCommand == null) { lastCommand = id; return@addSnapshotListener }   // ignore an old one on start
            if (id == lastCommand || s?.openControl != true) return@addSnapshotListener
            lastCommand = id
            val by = cmd["byName"] as? String ?: "A friend"
            when (cmd["type"]) {
                "play" -> { player.remotePlay(); say("$by pressed play") }
                "pause" -> { player.remotePause(); say("$by paused") }
                "seek" -> (cmd["positionMs"] as? Number)?.toLong()?.let { player.remoteSeek(it) }
                "next" -> { player.remoteNext(); say("$by skipped") }
                "prev" -> player.remotePrevious()
                "track" -> RemoteTrack.fromMap(cmd["track"] as? Map<*, *>)?.let { t ->
                    scope.launch {
                        val song = resolver.toSong(t) ?: run { say("Couldn't play “${t.title}”"); return@launch }
                        player.setQueue(listOf(song), 0)
                        say("$by picked “${t.title}”")
                    }
                }
            }
        }
        hostJob = scope.launch {
            var songId: Long? = null
            var ready = false
            var uploadJob: Job? = null
            var lastPlaying = false
            var lastPos = 0L
            var lastPub = 0L

            suspend fun publishState(playing: Boolean, pos: Long) {
                lastPlaying = playing; lastPos = pos; lastPub = System.currentTimeMillis()
                runCatching { ref.update(mapOf("isPlaying" to playing, "positionMs" to pos, "updatedAt" to lastPub)).await() }
            }

            fun prepare(song: Song) {
                ready = false
                uploadJob?.cancel()
                uploadJob = launch {
                    val quick = resolver.quick(song)
                    runCatching {
                        ref.update(mapOf("preparing" to !quick.playable, "track" to quick.toMap(), "updatedAt" to System.currentTimeMillis())).await()
                    }
                    val track = if (quick.playable) quick else {
                        _hostUpload.value = 0f
                        val r = resolver.ensure(song) { _hostUpload.value = it }
                        _hostUpload.value = null
                        r.getOrElse { say("Couldn't share “${song.title}” with friends: ${it.message}"); return@launch }
                    }
                    val st = player.playerState.value
                    if (st.currentSong?.id != song.id) return@launch
                    lastPlaying = st.isPlaying; lastPos = st.currentPosition; lastPub = System.currentTimeMillis()
                    runCatching {
                        ref.update(mapOf(
                            "track" to track.toMap(), "preparing" to false,
                            "isPlaying" to lastPlaying, "positionMs" to lastPos, "updatedAt" to lastPub,
                        )).await()
                    }
                    ready = true
                }
            }

            // Heartbeat so guests can tell the host is still there
            launch {
                while (isActive) {
                    delay(15_000)
                    if (ready) {
                        val st = player.playerState.value
                        publishState(st.isPlaying, st.currentPosition)
                    }
                }
            }

            player.playerState.collect { st ->
                val song = st.currentSong ?: return@collect
                if (song.id != songId) { songId = song.id; prepare(song); return@collect }
                if (!ready) return@collect
                val now = System.currentTimeMillis()
                val expected = lastPos + if (lastPlaying) now - lastPub else 0
                if (st.isPlaying != lastPlaying || abs(st.currentPosition - expected) > 3_000) {
                    publishState(st.isPlaying, st.currentPosition)
                }
            }
        }
    }

    suspend fun endSession() {
        val me = uid ?: return
        hostJob?.cancel(); hostJob = null
        hostListener?.remove(); hostListener = null
        _hosting.value = null
        _hostUpload.value = null
        detachChat()
        runCatching { sessions.document(me).update(mapOf("active" to false, "updatedAt" to System.currentTimeMillis())).await() }
        // Chats live on the phones — wipe this session's messages from the cloud
        runCatching {
            val docs = sessions.document(me).collection("messages").get().await().documents
            docs.chunked(400).forEach { chunk ->
                val b = firestore.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await()
            }
        }
    }

    // ─── Joining ─────────────────────────────────────────────────────────────
    private val _guest = MutableStateFlow<ListenSession?>(null)
    val guest: StateFlow<ListenSession?> = _guest.asStateFlow()
    private var guestJob: Job? = null
    private var guestListener: ListenerRegistration? = null
    private val applyLock = Mutex()

    suspend fun joinSession(hostUid: String): Result<Unit> = runCatching {
        val me = uid ?: error("Please sign in first")
        if (hostUid == me) error("That's your own session")
        if (_hosting.value != null) endSession()
        leaveSession(silent = true)
        val ref = sessions.document(hostUid)
        val first = parseSession(ref.get().await())
        if (first == null || !first.active) error("This session has ended")
        if (me !in first.invited) error("You're not invited to this session")
        val myName = users.document(me).get().await().getString("displayName") ?: "Friend"
        ref.update(mapOf("members" to FieldValue.arrayUnion(me), "memberNames.$me" to myName)).await()
        myGuestName = myName
        runGuest(me, ref.id)
        attachChat(hostUid, first.startedAt, first.hostName)
    }

    private fun runGuest(me: String, hostUid: String) {
        val ref = sessions.document(hostUid)
        var currentKey: String? = null
        var expectedSongId: Long? = null
        var loadedAt = 0L
        var warnedOffline = false

        fun expectedPos(s: ListenSession): Long {
            val drift = if (s.isPlaying) (System.currentTimeMillis() - s.updatedAt).coerceAtLeast(0) else 0
            val p = s.positionMs + drift
            val dur = s.track?.durationMs ?: 0
            return if (dur > 0) p.coerceAtMost(dur) else p
        }

        fun sync(s: ListenSession) {
            val st = player.playerState.value
            if (expectedSongId == null || st.currentSong?.id != expectedSongId) return
            val stale = s.isPlaying && System.currentTimeMillis() - s.updatedAt > 60_000
            if (stale) {
                if (!warnedOffline) { warnedOffline = true; say("${s.hostName} seems offline — waiting…") }
                return
            }
            warnedOffline = false
            if (s.isPlaying && !st.isPlaying) player.remotePlay()
            if (!s.isPlaying && st.isPlaying) player.remotePause()
            val exp = expectedPos(s)
            if (abs(st.currentPosition - exp) > 2_500) player.remoteSeek(exp)
        }

        suspend fun apply(s: ListenSession) = applyLock.withLock {
            val t = s.track ?: return@withLock
            if (s.preparing) return@withLock
            if (t.key != currentKey) {
                if (!t.playable) { say("“${t.title}” can't be streamed"); return@withLock }
                currentKey = t.key
                val song = resolver.toSong(t)
                if (song == null) { say("Couldn't load “${t.title}”"); currentKey = null; return@withLock }
                expectedSongId = song.id
                loadedAt = System.currentTimeMillis()
                player.setQueue(listOf(song), 0, startPositionMs = expectedPos(s), playWhenReady = s.isPlaying)
                return@withLock
            }
            sync(s)
        }

        guestListener?.remove()
        guestListener = ref.addSnapshotListener { snap, _ ->
            val s = snap?.let(::parseSession)
            if (s == null || !s.active) {
                if (_guest.value != null) say("${_guest.value?.hostName ?: "The host"} ended the session")
                scope.launch { leaveSession(silent = true) }
                return@addSnapshotListener
            }
            _guest.value = s
            scope.launch { apply(s) }
        }
        guestJob?.cancel()
        guestJob = scope.launch {
            // What the guest presses: shared with everyone (open control) or undone (host only)
            launch {
                player.userActions.collect { a ->
                    val s = _guest.value ?: return@collect
                    if (a is PlaybackController.UserAction.SleepStop) {
                        say("Sleep timer: left ${s.hostName}'s session"); leaveSession(silent = true); return@collect
                    }
                    if (!s.openControl) {
                        say("Only ${s.hostName} controls this session")
                        delay(300); if (!applyLock.isLocked) sync(s)
                        return@collect
                    }
                    when (a) {
                        PlaybackController.UserAction.Play -> sendCommand(s, mapOf("type" to "play"))
                        PlaybackController.UserAction.Pause -> sendCommand(s, mapOf("type" to "pause"))
                        is PlaybackController.UserAction.Seek -> sendCommand(s, mapOf("type" to "seek", "positionMs" to a.positionMs))
                        PlaybackController.UserAction.Next -> sendCommand(s, mapOf("type" to "next"))
                        PlaybackController.UserAction.Previous -> sendCommand(s, mapOf("type" to "prev"))
                        else -> {}
                    }
                }
            }
            while (isActive) {
                delay(4_000)
                val s = _guest.value ?: continue
                val st = player.playerState.value
                // The guest picked another song themselves
                if (expectedSongId != null && st.currentSong != null && st.currentSong.id != expectedSongId &&
                    System.currentTimeMillis() - loadedAt > 8_000) {
                    if (s.openControl) {
                        // …play it for everyone
                        val song = st.currentSong
                        expectedSongId = song.id
                        loadedAt = System.currentTimeMillis()
                        say("Sending “${song.title}” to everyone…")
                        val r = resolver.ensure(song)
                        val t = r.getOrNull()
                        if (t == null) { say("Couldn't share “${song.title}”: ${r.exceptionOrNull()?.message}"); continue }
                        currentKey = t.key
                        sendCommand(s, mapOf("type" to "track", "track" to t.toMap()))
                        continue
                    }
                    say("You left ${s.hostName}'s session")
                    leaveSession(silent = true)
                    break
                }
                if (!applyLock.isLocked) sync(s)
            }
        }
    }

    private var myGuestName = "Friend"

    private suspend fun sendCommand(s: ListenSession, cmd: Map<String, Any>) {
        val me = uid ?: return
        runCatching {
            sessions.document(s.hostUid).update(
                "command", cmd + mapOf("id" to java.util.UUID.randomUUID().toString(), "by" to me, "byName" to myGuestName, "at" to System.currentTimeMillis()),
            ).await()
        }.onFailure { say("Couldn't reach ${s.hostName}") }
    }

    /** Guest buttons in the Listen Together sheet. */
    fun guestControl(type: String) {
        val s = _guest.value ?: return
        if (!s.openControl) { say("Only ${s.hostName} controls this session"); return }
        scope.launch { sendCommand(s, mapOf("type" to type)) }
    }

    suspend fun leaveSession(silent: Boolean = false) {
        val s = _guest.value
        guestJob?.cancel(); guestJob = null
        guestListener?.remove(); guestListener = null
        _guest.value = null
        if (s != null) detachChat()
        val me = uid ?: return
        if (s != null) {
            runCatching {
                sessions.document(s.hostUid).update(mapOf("members" to FieldValue.arrayRemove(me), "memberNames.$me" to FieldValue.delete())).await()
            }
            if (!silent) say("Left ${s.hostName}'s session")
        }
    }

    // ─── Chat (kept on the phone; Firestore only relays) ─────────────────────
    private val _chat = MutableStateFlow<List<com.waleve.player.domain.model.ChatMessage>>(emptyList())
    val chat: StateFlow<List<com.waleve.player.domain.model.ChatMessage>> = _chat.asStateFlow()
    private val _chatUnread = MutableStateFlow(0)
    val chatUnread: StateFlow<Int> = _chatUnread.asStateFlow()
    private var chatOpen = false
    private var chatListener: ListenerRegistration? = null
    private var chatSession: Triple<String, Long, String>? = null   // hostUid, startedAt, hostName

    private fun chatKey(hostUid: String, startedAt: Long) = "${hostUid}_$startedAt"

    private fun attachChat(hostUid: String, startedAt: Long, hostName: String) {
        detachChat()
        chatSession = Triple(hostUid, startedAt, hostName)
        val known = chatStore.load(chatKey(hostUid, startedAt))?.messages.orEmpty().associateBy { it.id }.toMutableMap()
        _chat.value = known.values.sortedBy { it.at }
        _chatUnread.value = 0
        val me = uid
        chatListener = sessions.document(hostUid).collection("messages")
            .whereGreaterThanOrEqualTo("at", startedAt)
            .addSnapshotListener { snap, _ ->
                var fresh = 0
                snap?.documents?.forEach { d ->
                    val m = com.waleve.player.domain.model.ChatMessage(
                        d.id, d.getString("uid") ?: "", d.getString("name") ?: "Friend",
                        d.getString("text") ?: "", d.getLong("at") ?: 0L,
                    )
                    if (m.id !in known) { known[m.id] = m; if (m.uid != me) fresh++ }
                }
                val list = known.values.sortedBy { it.at }
                _chat.value = list
                if (!chatOpen && fresh > 0) {
                    _chatUnread.value += fresh
                    list.lastOrNull { it.uid != me }?.let { say("💬 ${it.name.substringBefore(' ')}: ${it.text}") }
                }
                chatStore.save(chatKey(hostUid, startedAt), hostName, startedAt, list)
            }
    }

    private fun detachChat() {
        chatListener?.remove(); chatListener = null
        chatSession?.let { (h, t, n) -> chatStore.save(chatKey(h, t), n, t, _chat.value) }
        chatSession = null
        _chat.value = emptyList()
        _chatUnread.value = 0
    }

    fun setChatOpen(open: Boolean) { chatOpen = open; if (open) _chatUnread.value = 0 }

    suspend fun sendChat(text: String): Result<Unit> = runCatching {
        val me = uid ?: error("Please sign in first")
        val (hostUid, _, _) = chatSession ?: error("Not in a session")
        val clean = text.trim().take(300)
        if (clean.isEmpty()) return@runCatching
        val name = if (_hosting.value != null) _hosting.value?.hostName ?: "Host" else myGuestName
        sessions.document(hostUid).collection("messages").add(
            mapOf("uid" to me, "name" to name, "text" to clean, "at" to System.currentTimeMillis()),
        ).await()
    }

    /** Old session chats saved on this phone. */
    fun chatHistory(): List<com.waleve.player.domain.model.ChatLog> = chatStore.history()

    /** Play what a friend is playing (on my own, not synced). */
    suspend fun playFriendTrack(np: NowPlaying): Boolean {
        val song = resolver.toQueueSong(np.track) ?: return false
        player.setQueue(listOf(song), 0)
        return true
    }
}
