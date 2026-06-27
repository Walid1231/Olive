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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanMusicUseCase: ScanMusicUseCase,
    getRecentlyPlayedUseCase: GetRecentlyPlayedUseCase,
    getMostPlayedUseCase: GetMostPlayedUseCase,
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val recentlyPlayed: StateFlow<List<Song>> = getRecentlyPlayedUseCase(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<Song>> = getMostPlayedUseCase(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        scanMusic()
    }

    fun scanMusic() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanMusicUseCase()
            } catch (_: Exception) { }
            _isScanning.value = false
        }
    }
}
