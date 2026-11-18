package com.waleve.player.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.usecase.GetMostPlayedUseCase
import com.waleve.player.domain.usecase.GetRecentlyPlayedUseCase
import com.waleve.player.domain.usecase.ScanMusicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanMusicUseCase: ScanMusicUseCase,
    getRecentlyPlayedUseCase: GetRecentlyPlayedUseCase,
    getMostPlayedUseCase: GetMostPlayedUseCase,
    localMusicRepository: com.waleve.player.domain.repository.LocalMusicRepository,
    authRepository: com.waleve.player.domain.repository.AuthRepository,
) : ViewModel() {

    /** Signed-in profile for the greeting + avatar (null when signed out). */
    val profile: StateFlow<com.waleve.player.domain.model.UserProfile?> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val songCount: StateFlow<Int> = localMusicRepository.getAllSongs()
        .map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)


    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Songs only — audiobooks get their own "Continue listening" row
    val recentlyPlayed: StateFlow<List<Song>> = getRecentlyPlayedUseCase(40)
        .map { list -> list.filterNot { it.isAudiobook }.take(20) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<Song>> = getMostPlayedUseCase(40)
        .map { list -> list.filterNot { it.isAudiobook }.take(20) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** One entry per book: the chapter you played most recently. */
    val continueListening: StateFlow<List<Song>> = localMusicRepository.getAudiobooks()
        .map { list ->
            list.filter { it.lastPlayed != null }
                .groupBy { it.album to it.artist }
                .map { (_, chapters) -> chapters.maxBy { it.lastPlayed ?: 0L } }
                .sortedByDescending { it.lastPlayed ?: 0L }
                .take(10)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Chapters of a book in order — so "next chapter" works when you resume. */
    val audiobooks: StateFlow<List<Song>> = localMusicRepository.getAudiobooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        scanMusic()
    }

    fun scanMusic() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanMusicUseCase()
            } catch (_: Exception) { }
            _isScanning.value = false
        }
    }
}
