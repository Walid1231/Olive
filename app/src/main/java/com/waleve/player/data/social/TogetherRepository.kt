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
        )
    }

    /** Active sessions I've been invited to (not my own). */
    fun observeInvites(): Flow<List<ListenSession>> = uidFlow().flatMapLatest { me ->
        if (me == null) flowOf(emptyList()) else callbackFlow {
            val l = sessions.whereArrayContains("invited", me).whereEqualTo("active", true)
                .addSnapshotListener { snap, _ ->
                    val list = snap?.documents?.mapNotNull(::parseSession)
                        ?.filter { it.hostUid != me && System.currentTimeMillis() - it.updatedAt < 2 * 3_600_000 }
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

    suspend fun startSession(friendUids: List<String>): Result<Unit> = runCatching {
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
            "startedAt" to now, "updatedAt" to now,
        )).await()
        sendInvites(me, name, avatar, friendUids)
        runHost(me)
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
        hostListener = ref.addSnapshotListener { snap, _ ->
            val s = snap?.let(::parseSession)
            _hosting.value = s?.takeIf { it.active }
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
        runCatching { sessions.document(me).update(mapOf("active" to false, "updatedAt" to System.currentTimeMillis())).await() }
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
        runGuest(me, ref.id)
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
            if (s.isPlaying && !st.isPlaying) player.play()
            if (!s.isPlaying && st.isPlaying) player.pause()
            val exp = expectedPos(s)
            if (abs(st.currentPosition - exp) > 2_500) player.seekTo(exp)
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
            while (isActive) {
                delay(4_000)
                val s = _guest.value ?: continue
                val st = player.playerState.value
                // The guest picked another song themselves → they left the session
                if (expectedSongId != null && st.currentSong != null && st.currentSong.id != expectedSongId &&
                    System.currentTimeMillis() - loadedAt > 8_000) {
                    say("You left ${s.hostName}'s session")
                    leaveSession(silent = true)
                    break
                }
                if (!applyLock.isLocked) sync(s)
            }
        }
    }

    suspend fun leaveSession(silent: Boolean = false) {
        val s = _guest.value
        guestJob?.cancel(); guestJob = null
        guestListener?.remove(); guestListener = null
        _guest.value = null
        val me = uid ?: return
        if (s != null) {
            runCatching {
                sessions.document(s.hostUid).update(mapOf("members" to FieldValue.arrayRemove(me), "memberNames.$me" to FieldValue.delete())).await()
            }
            if (!silent) say("Left ${s.hostName}'s session")
        }
    }

    /** Play what a friend is playing (on my own, not synced). */
    suspend fun playFriendTrack(np: NowPlaying): Boolean {
        val song = resolver.toQueueSong(np.track) ?: return false
        player.setQueue(listOf(song), 0)
        return true
    }
}
