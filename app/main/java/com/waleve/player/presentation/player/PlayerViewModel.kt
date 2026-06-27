package com.waleve.player.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.domain.model.PlayerState
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.model.Lyrics
import com.waleve.player.domain.repository.LocalMusicRepository
import com.waleve.player.domain.repository.LyricsRepository
import com.waleve.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playbackController: PlaybackController,
    private val localMusicRepository: LocalMusicRepository,
    private val lyricsRepository: LyricsRepository,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playbackController.playerState

    val sleepTimerRemaining: StateFlow<Long> = playbackController.sleepTimerRemaining

    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private var lyricsJob: Job? = null

    init {
        viewModelScope.launch {
            playerState.distinctUntilChangedBy { it.currentSong?.id }
                .collect { state ->
                    state.currentSong?.let { song ->
                        fetchLyricsFor(song)
                    } ?: run {
                        _lyrics.value = null
                    }
                }
        }
    }

    private fun fetchLyricsFor(song: Song) {
        // Simple deduplication, if we already have lyrics for this song, don't refetch
        // We track this by checking if the job is already running for the same song.
        // For brevity, we just cancel and refetch.
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _isLyricsLoading.value = true
            _lyrics.value = null
            
            val result = lyricsRepository.getLyrics(
                trackName = song.title,
                artistName = song.artist,
                durationMs = song.duration
            )
            
            _lyrics.value = result.getOrNull()
            _isLyricsLoading.value = false
        }
    }


    fun playPause() = playbackController.playPause()
    fun next() = playbackController.next()
    fun previous() = playbackController.previous()
    fun seekTo(position: Long) = playbackController.seekTo(position)
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun cycleRepeatMode() = playbackController.cycleRepeatMode()
    fun setPlaybackSpeed(speed: Float) = playbackController.setPlaybackSpeed(speed)

    fun playSong(songs: List<Song>, startIndex: Int = 0) {
        playbackController.setQueue(songs, startIndex)
    }

    fun onSongPlayed(songId: Long) {
        viewModelScope.launch {
            localMusicRepository.updatePlayCount(songId)
        }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            localMusicRepository.toggleFavorite(songId)
            val currentIsFav = playerState.value.currentSong?.isFavorite == true
            playbackController.updateSongFavoriteStatus(songId, !currentIsFav)
        }
    }

    fun setSleepTimer(minutes: Int) {
        playbackController.setSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
    }
}
