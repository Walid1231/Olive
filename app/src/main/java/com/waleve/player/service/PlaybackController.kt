package com.waleve.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.waleve.player.MainActivity
import com.waleve.player.R
import com.waleve.player.domain.model.PlayerState
import com.waleve.player.domain.model.RepeatMode
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.StreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamRepository: StreamRepository,
) {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val songMap = mutableMapOf<String, Song>()
    private var currentQueue = listOf<Song>()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateStarted = false

    // ── Audiobook resume: remember where you stopped in every chapter ──
    private val bookmarks = context.getSharedPreferences("audiobook_positions", Context.MODE_PRIVATE)
    private var lastBookmarkSave = 0L

    /** Online books have a new stream URL every time, so they're remembered by YouTube id. */
    private fun bookmarkKey(song: Song): String = song.videoId?.let { "yt:$it" } ?: song.path

    private fun savedPosition(song: Song?): Long {
        if (song?.isAudiobook != true) return 0L
        val pos = bookmarks.getLong(bookmarkKey(song), 0L)
        if (pos <= 0) return 0L
        // Smart resume: step back a little, more the longer you've been away
        val savedAt = bookmarks.getLong("t:" + bookmarkKey(song), 0L)
        val gap = if (savedAt > 0) System.currentTimeMillis() - savedAt else 0L
        return (pos - smartRewind(gap)).coerceAtLeast(0L)
    }

    /** How far to rewind an audiobook after a break of [gapMs]. */
    private fun smartRewind(gapMs: Long): Long = when {
        gapMs >= 3_600_000 -> 30_000
        gapMs >= 600_000 -> 20_000
        gapMs >= 60_000 -> 10_000
        gapMs >= 15_000 -> 3_000
        else -> 0
    }
    private var pausedAt = 0L

    // ── Speed memory: every audiobook keeps its own speed; music has one shared speed ──
    private val speeds by lazy { context.getSharedPreferences("playback_speeds", Context.MODE_PRIVATE) }
    private fun speedKey(song: Song) = if (song.isAudiobook) "book:${song.album}|${song.artist}" else "music"
    private fun applySpeedFor(song: Song?) {
        val c = mediaController ?: return
        song ?: return
        val target = speeds.getFloat(speedKey(song), 1f)
        if (c.playbackParameters.speed != target) c.setPlaybackSpeed(target)
    }

    private fun saveBookmark(force: Boolean = false) {
        val c = mediaController ?: return
        val song = c.currentMediaItem?.mediaId?.let { songMap[it] } ?: return
        if (!song.isAudiobook) return
        val now = System.currentTimeMillis()
        if (!force && now - lastBookmarkSave < 5_000) return
        lastBookmarkSave = now
        val pos = c.currentPosition
        val dur = c.duration
        // Finished (last 30 s) → start from the beginning next time
        val toSave = if (dur > 0 && pos > dur - 30_000) 0L else pos
        val key = bookmarkKey(song)
        bookmarks.edit().putLong(key, toSave).putLong("t:$key", now).apply()
    }

    // ── Sleep Timer ──────────────────────────────────────────
    private val _sleepTimerRemaining = MutableStateFlow(0L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null
    /** "Stop after the current song / chapter" mode. */
    private val _sleepEndOfTrack = MutableStateFlow(false)
    val sleepEndOfTrack: StateFlow<Boolean> = _sleepEndOfTrack.asStateFlow()
    /** Gently lower the volume during the last 30 s before stopping. */
    private val sleepPrefs by lazy { context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE) }
    private val _sleepFadeOut = MutableStateFlow(true)
    val sleepFadeOut: StateFlow<Boolean> = _sleepFadeOut.asStateFlow()
    /** Shake the phone to add 5 minutes while the timer runs. */
    private val _sleepShake = MutableStateFlow(true)
    val sleepShake: StateFlow<Boolean> = _sleepShake.asStateFlow()
    private var shakeListener: android.hardware.SensorEventListener? = null
    private var lastShake = 0L
    /** Length of the running timer (ms), for progress rings. */
    private val _sleepTimerTotal = MutableStateFlow(0L)
    val sleepTimerTotal: StateFlow<Long> = _sleepTimerTotal.asStateFlow()

    companion object {
        private const val SLEEP_TIMER_CHANNEL_ID = "waleve_sleep_timer"
        private const val SLEEP_TIMER_NOTIFICATION_ID = 9001
        private const val FADE_MS = 30_000L
    }

    init {
        connectToService()
        createSleepTimerNotificationChannel()
        _sleepFadeOut.value = sleepPrefs.getBoolean("fade", true)
        _sleepShake.value = sleepPrefs.getBoolean("shake", true)
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
                startPositionUpdates()
            } catch (_: Exception) { }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val c = mediaController ?: return
                val song = c.currentMediaItem?.mediaId?.let { songMap[it] }
                val vid = song?.videoId
                if (song == null || vid == null || !retried.add(vid)) {
                    setPending(null)
                    _messages.tryEmit("Couldn't play “${song?.title ?: "this song"}”")
                    return
                }
                // The quick link didn't work — get one through yt-dlp and carry on from the same spot
                val idx = c.currentMediaItemIndex
                val pos = c.currentPosition
                setPending(song)
                scope.launch {
                    streamRepository.forget(vid)
                    val url = streamRepository.getStreamUrlSlow(vid)
                    if (url == null) { setPending(null); _messages.tryEmit("Couldn't play “${song.title}”"); return@launch }
                    val fixed = song.copy(path = url)
                    songMap[fixed.id.toString()] = fixed
                    mediaController?.replaceMediaItem(idx, fixed.toMediaItem())
                    mediaController?.seekTo(idx, pos)
                    mediaController?.prepare()
                    mediaController?.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) setPending(null)
                val c = mediaController
                val cur = c?.currentMediaItem?.mediaId?.let { songMap[it] }
                if (!isPlaying) {
                    saveBookmark(force = true)
                    pausedAt = if (cur?.isAudiobook == true) System.currentTimeMillis() else 0L
                } else if (pausedAt > 0 && cur?.isAudiobook == true && c != null) {
                    val back = smartRewind(System.currentTimeMillis() - pausedAt)
                    if (back > 0) c.seekTo((c.currentPosition - back).coerceAtLeast(0L))
                    pausedAt = 0L
                } else pausedAt = 0L
                updateState()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Sleep mode "end of track": the previous item just finished → stop here
                if (_sleepEndOfTrack.value && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
                    mediaController?.pause()
                    mediaController?.seekTo(0)
                    _sleepEndOfTrack.value = false
                    _userActions.tryEmit(UserAction.SleepStop)
                    cancelSleepTimerNotification()
                    updateState()
                    return
                }
                // Auto-advanced into another audiobook chapter → continue where it was left
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    val pos = savedPosition(mediaItem?.mediaId?.let { songMap[it] })
                    if (pos > 0) mediaController?.seekTo(pos)
                }
                pausedAt = 0L
                applySpeedFor(mediaItem?.mediaId?.let { songMap[it] })
                updateState()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) setPending(null)
                updateState()
            }
            override fun onRepeatModeChanged(repeatMode: Int) = updateState()
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateState()
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = updateState()
        })
        updateState()
    }

    private fun startPositionUpdates() {
        if (positionUpdateStarted) return
        positionUpdateStarted = true
        scope.launch {
            while (isActive) {
                // Lost the link to the playback service (e.g. it was restarted)? Reconnect,
                // otherwise the buttons would show a stale "paused" state.
                if (mediaController?.isConnected == false) {
                    mediaController = null
                    connectToService()
                }
                updateState()
                if (mediaController?.isPlaying == true) saveBookmark()
                delay(500)
            }
        }
    }

    private fun updateState() {
        val controller = mediaController ?: return
        val currentMediaItem = controller.currentMediaItem
        val currentSong = currentMediaItem?.mediaId?.let { songMap[it] }

        _playerState.update {
            it.copy(
                currentSong = currentSong,
                isPlaying = controller.isPlaying,
                currentPosition = controller.currentPosition.coerceAtLeast(0),
                duration = controller.duration.coerceAtLeast(0),
                queue = currentQueue,
                currentIndex = controller.currentMediaItemIndex,
                repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> RepeatMode.OFF
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                },
                shuffleEnabled = controller.shuffleModeEnabled,
                playbackSpeed = controller.playbackParameters.speed,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING && controller.playWhenReady,
                loadingSong = pendingSong,
            )
        }
    }

    /** The song being fetched right after a tap (until it starts). */
    @Volatile private var pendingSong: Song? = null
    private fun setPending(song: Song?) {
        pendingSong = song
        _playerState.update { it.copy(loadingSong = song) }
    }

    /** Fast links that failed once → retried through yt-dlp (once per song). */
    private val retried = mutableSetOf<String>()

    /** What the user did with the controls (Listen Together uses this to share or undo it). */
    sealed interface UserAction {
        data object Play : UserAction
        data object Pause : UserAction
        data class Seek(val positionMs: Long) : UserAction
        data object Next : UserAction
        data object Previous : UserAction
        /** The sleep timer stopped the music. */
        data object SleepStop : UserAction
    }
    private val _userActions = kotlinx.coroutines.flow.MutableSharedFlow<UserAction>(extraBufferCapacity = 16)
    val userActions: kotlinx.coroutines.flow.SharedFlow<UserAction> = _userActions

    fun play() { mediaController?.play(); _userActions.tryEmit(UserAction.Play) }
    fun pause() { mediaController?.pause(); _userActions.tryEmit(UserAction.Pause) }
    fun playPause() {
        mediaController?.let { if (it.isPlaying) pause() else play() }
    }
    fun next() { mediaController?.seekToNextMediaItem(); _userActions.tryEmit(UserAction.Next) }
    fun previous() { mediaController?.seekToPreviousMediaItem(); _userActions.tryEmit(UserAction.Previous) }
    fun seekTo(position: Long) { mediaController?.seekTo(position); _userActions.tryEmit(UserAction.Seek(position)) }

    // Same as above but silent: used when another phone (Listen Together) tells us what to do
    fun remotePlay() { mediaController?.play() }
    fun remotePause() { mediaController?.pause() }
    fun remoteSeek(position: Long) { mediaController?.seekTo(position) }
    fun remoteNext() { mediaController?.seekToNextMediaItem() }
    fun remotePrevious() { mediaController?.seekToPreviousMediaItem() }

    private var queueJob: Job? = null

    /** Short messages for the user (e.g. a song that couldn't be streamed). */
    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: kotlinx.coroutines.flow.SharedFlow<String> = _messages

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(path)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri?.let { Uri.parse(it) })
                .build()
        )
        .build()

    /** ytm://stream/<videoId> → real stream URL. Null when the stream can't be found. */
    private suspend fun resolveStream(song: Song): Song? {
        if (!song.path.startsWith("ytm://stream/")) return song
        val videoId = song.path.substringAfter("ytm://stream/")
        return withContext(Dispatchers.IO) {
            runCatching { streamRepository.getStreamUrl(videoId) }.getOrNull()?.let { song.copy(path = it) }
        }
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0, startPositionMs: Long? = null, playWhenReady: Boolean = true) {
        saveBookmark(force = true)   // remember the audiobook we're leaving
        queueJob?.cancel()           // a newer queue always wins
        songMap.clear()
        currentQueue = songs
        if (songs.isEmpty()) return
        val start = startIndex.coerceIn(0, songs.lastIndex)
        retried.clear()
        // Show the tapped song right away with a spinner (online songs take a moment)
        if (songs[start].path.startsWith("ytm://") || songs[start].path.startsWith("http")) setPending(songs[start])

        queueJob = scope.launch {
            if (songs.none { it.path.startsWith("ytm://stream/") }) {
                // Everything is already playable (local files / direct URLs)
                songs.forEach { songMap[it.id.toString()] = it }
                val startPos = startPositionMs ?: savedPosition(songs[start])
                mediaController?.setMediaItems(songs.map { it.toMediaItem() }, start, startPos)
                mediaController?.prepare()
                if (playWhenReady) mediaController?.play() else mediaController?.pause()
                return@launch
            }

            // Online queue: start the chosen song right away, resolve the others in the background
            var firstIdx = start
            var first: Song? = null
            while (firstIdx < songs.size && first == null) {
                first = resolveStream(songs[firstIdx])
                if (first == null) {
                    _messages.tryEmit("Couldn't play “${songs[firstIdx].title}” — trying the next one")
                    firstIdx++
                }
            }
            if (first == null) {
                setPending(null)
                _messages.tryEmit("YouTube didn't give us these songs right now. Check your internet and try again in a minute.")
                return@launch
            }
            setPending(first)
            val ordered = mutableListOf(first)
            songMap[first.id.toString()] = first
            currentQueue = ordered.toList()
            val startPos = startPositionMs ?: savedPosition(first)
            mediaController?.setMediaItems(listOf(first.toMediaItem()), 0, startPos)
            mediaController?.prepare()
            if (playWhenReady) mediaController?.play() else mediaController?.pause()

            for (i in firstIdx + 1 until songs.size) {
                val r = resolveStream(songs[i]) ?: continue
                songMap[r.id.toString()] = r
                mediaController?.addMediaItem(r.toMediaItem())
                ordered.add(r)
                currentQueue = ordered.toList()
                updateState()
            }
            var insertAt = 0
            for (i in 0 until start) {
                val r = resolveStream(songs[i]) ?: continue
                songMap[r.id.toString()] = r
                mediaController?.addMediaItem(insertAt, r.toMediaItem())
                ordered.add(insertAt, r)
                insertAt++
                currentQueue = ordered.toList()
                updateState()
            }
        }
    }

    fun updateSongFavoriteStatus(songId: Long, isFavorite: Boolean) {
        val song = songMap[songId.toString()] ?: return
        val updatedSong = song.copy(isFavorite = isFavorite)
        songMap[songId.toString()] = updatedSong
        
        if (_playerState.value.currentSong?.id == songId) {
            _playerState.update { it.copy(currentSong = updatedSong) }
        }
        
        val index = currentQueue.indexOfFirst { it.id == songId }
        if (index != -1) {
            val newList = currentQueue.toMutableList()
            newList[index] = updatedSong
            currentQueue = newList
        }
    }

    fun toggleShuffle() {
        mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeatMode() {
        mediaController?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _playerState.value.currentSong?.let { speeds.edit().putFloat(speedKey(it), speed).apply() }
    }

    // ── Sleep Timer Methods ──────────────────────────────────

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        stopShake()
        restoreVolume()
        _sleepEndOfTrack.value = false
        cancelSleepTimerNotification()

        if (minutes <= 0) {
            _sleepTimerRemaining.value = 0
            _sleepTimerTotal.value = 0
            return
        }
        startCountdown(minutes * 60 * 1000L)
    }

    /** Add time to a running timer (or start one). */
    fun extendSleepTimer(minutes: Int) {
        val left = _sleepTimerRemaining.value
        if (left <= 0) { setSleepTimer(minutes); return }
        val add = minutes * 60 * 1000L
        _sleepTimerTotal.update { it + add }
        _sleepTimerRemaining.update { it + add }
        restoreVolume()
    }

    /** Stop when the current song / chapter ends. */
    fun setSleepAtEndOfTrack() {
        sleepTimerJob?.cancel()
        stopShake()
        restoreVolume()
        _sleepTimerRemaining.value = 0
        _sleepTimerTotal.value = 0
        _sleepEndOfTrack.value = true
        showSleepTimerNotification(-1)
    }

    fun setSleepFadeOut(enabled: Boolean) {
        _sleepFadeOut.value = enabled
        sleepPrefs.edit().putBoolean("fade", enabled).apply()
        if (!enabled) restoreVolume()
    }

    fun setSleepShake(enabled: Boolean) {
        _sleepShake.value = enabled
        sleepPrefs.edit().putBoolean("shake", enabled).apply()
        if (!enabled) stopShake() else if (_sleepTimerRemaining.value > 0) startShake()
    }

    private fun startShake() {
        if (shakeListener != null || !_sleepShake.value) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager ?: return
        val acc = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val g = android.hardware.SensorManager.GRAVITY_EARTH
                val (x, y, z) = Triple(e.values[0] / g, e.values[1] / g, e.values[2] / g)
                val force = kotlin.math.sqrt(x * x + y * y + z * z)
                val now = System.currentTimeMillis()
                if (force > 2.6f && now - lastShake > 2_000 && _sleepTimerRemaining.value > 0) {
                    lastShake = now
                    extendSleepTimer(5)
                    runCatching {
                        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
                        else (context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator)
                        vib.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    android.widget.Toast.makeText(context, "Sleep timer +5 min", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        sm.registerListener(l, acc, android.hardware.SensorManager.SENSOR_DELAY_UI)
        shakeListener = l
    }

    private fun stopShake() {
        val l = shakeListener ?: return
        (context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager)?.unregisterListener(l)
        shakeListener = null
    }

    private fun startCountdown(totalMs: Long) {
        _sleepTimerTotal.value = totalMs
        _sleepTimerRemaining.value = totalMs
        startShake()
        sleepTimerJob = scope.launch {
            showSleepTimerNotification(_sleepTimerRemaining.value)
            while (_sleepTimerRemaining.value > 0) {
                delay(1000)
                _sleepTimerRemaining.update { (it - 1000).coerceAtLeast(0) }
                val left = _sleepTimerRemaining.value
                // Fade out over the last 30 seconds
                if (_sleepFadeOut.value && left in 1..FADE_MS) {
                    mediaController?.volume = (left.toFloat() / FADE_MS).coerceIn(0.05f, 1f)
                }
                // Refresh the notification every 5 s (and every second in the last minute)
                if (left <= 60_000 || (left / 1000) % 5 == 0L) showSleepTimerNotification(left)
            }
            // Timer expired — pause playback, then put the volume back for next time
            mediaController?.pause()
            _userActions.tryEmit(UserAction.SleepStop)
            delay(300)
            restoreVolume()
            _sleepTimerRemaining.value = 0
            _sleepTimerTotal.value = 0
            stopShake()
            cancelSleepTimerNotification()
        }
    }

    private fun restoreVolume() {
        try { if ((mediaController?.volume ?: 1f) < 1f) mediaController?.volume = 1f } catch (_: Exception) { }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        stopShake()
        restoreVolume()
        _sleepTimerRemaining.value = 0
        _sleepTimerTotal.value = 0
        _sleepEndOfTrack.value = false
        cancelSleepTimerNotification()
    }

    // ── Notification Helpers ─────────────────────────────────

    private fun createSleepTimerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SLEEP_TIMER_CHANNEL_ID,
                "Sleep Timer",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when the sleep timer is active"
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showSleepTimerNotification(remainingMs: Long) {
        val totalSeconds = remainingMs / 1000
        val h = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val timeText = if (h > 0) "%d:%02d:%02d".format(h, minutes, seconds) else "%d:%02d".format(minutes, seconds)
        fun action(act: String, code: Int) = PendingIntent.getBroadcast(
            context, code, Intent(context, SleepTimerReceiver::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, SLEEP_TIMER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle("Sleep timer on")
            .setContentText(if (remainingMs < 0) "Music stops after this track" else "Music stops in $timeText")
            .setSubText("Olive")
            .addAction(0, "Cancel", action(SleepTimerReceiver.ACTION_CANCEL, 11))
            .apply { if (remainingMs >= 0) addAction(0, "+5 min", action(SleepTimerReceiver.ACTION_EXTEND, 12)) }
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(SLEEP_TIMER_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // User denied notification permission — timer still works, just no notification
        }
    }

    private fun cancelSleepTimerNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(SLEEP_TIMER_NOTIFICATION_ID)
    }

    fun release() {
        cancelSleepTimer()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
