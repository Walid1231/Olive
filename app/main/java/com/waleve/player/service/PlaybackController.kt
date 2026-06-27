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

    // ── Sleep Timer ──────────────────────────────────────────
    private val _sleepTimerRemaining = MutableStateFlow(0L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null

    companion object {
        private const val SLEEP_TIMER_CHANNEL_ID = "waleve_sleep_timer"
        private const val SLEEP_TIMER_NOTIFICATION_ID = 9001
    }

    init {
        connectToService()
        createSleepTimerNotificationChannel()
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
            override fun onIsPlayingChanged(isPlaying: Boolean) = updateState()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateState()
            override fun onPlaybackStateChanged(playbackState: Int) = updateState()
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
                updateState()
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
            )
        }
    }

    fun play() { mediaController?.play() }
    fun pause() { mediaController?.pause() }
    fun playPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    fun next() { mediaController?.seekToNextMediaItem() }
    fun previous() { mediaController?.seekToPreviousMediaItem() }
    fun seekTo(position: Long) { mediaController?.seekTo(position) }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        songMap.clear()
        currentQueue = songs

        scope.launch {
            // Resolve any ytm://stream/<videoId> pseudo-paths to real stream URLs
            val resolvedSongs = withContext(Dispatchers.IO) {
                songs.map { song ->
                    if (song.path.startsWith("ytm://stream/")) {
                        val videoId = song.path.substringAfter("ytm://stream/")
                        try {
                            val streamUrl = streamRepository.getStreamUrl(videoId)
                            if (streamUrl != null) song.copy(path = streamUrl) else song
                        } catch (_: Exception) { song }
                    } else {
                        song
                    }
                }
            }

            resolvedSongs.forEach { song -> songMap[song.id.toString()] = song }
            currentQueue = resolvedSongs

            val mediaItems = resolvedSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.path)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.albumArtUri?.let { Uri.parse(it) })
                            .build()
                    )
                    .build()
            }
            mediaController?.setMediaItems(mediaItems, startIndex, 0L)
            mediaController?.prepare()
            mediaController?.play()
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
    }

    // ── Sleep Timer Methods ──────────────────────────────────

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        cancelSleepTimerNotification()

        if (minutes <= 0) {
            _sleepTimerRemaining.value = 0
            return
        }

        sleepTimerJob = scope.launch {
            _sleepTimerRemaining.value = minutes * 60 * 1000L
            showSleepTimerNotification(_sleepTimerRemaining.value)

            while (_sleepTimerRemaining.value > 0) {
                delay(1000)
                _sleepTimerRemaining.update { it - 1000 }


                // Update notification every second for live MM:SS countdown
                showSleepTimerNotification(_sleepTimerRemaining.value)

            }

            // Timer expired — pause playback
            pause()
            _sleepTimerRemaining.value = 0
            cancelSleepTimerNotification()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = 0
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
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val timeText = "%d:%02d".format(minutes, seconds)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, SLEEP_TIMER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle("💤 Sleep Timer Active")
            .setContentText("Music will stop in $timeText")
            .setSubText("WaLEve Sleep Timer")
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
