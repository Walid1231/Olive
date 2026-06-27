package com.waleve.player.presentation.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.FriendRepository
import com.waleve.player.domain.repository.SharedPlaylistRepository
import com.waleve.player.domain.model.Friend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ShareUiEvent {
    data class Success(val message: String) : ShareUiEvent()
    data class Error(val message: String) : ShareUiEvent()
}

@HiltViewModel
class SharePlaylistViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val sharedPlaylistRepository: SharedPlaylistRepository,
) : ViewModel() {

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    private val _events = MutableSharedFlow<ShareUiEvent>()
    val events: SharedFlow<ShareUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            friendRepository.observeFriends()
                .catch { /* silent */ }
                .collect { _friends.value = it }
        }
    }

    fun sharePlaylist(
        playlistName: String,
        playlistDescription: String?,
        songs: List<Song>,
        selectedFriendUids: List<String>,
    ) {
        if (selectedFriendUids.isEmpty()) {
            viewModelScope.launch { _events.emit(ShareUiEvent.Error("Please select at least one friend")) }
            return
        }
        viewModelScope.launch {
            _isSharing.value = true
            val result = sharedPlaylistRepository.sharePlaylist(
                playlistName        = playlistName,
                playlistDescription = playlistDescription,
                songs               = songs,
                friendUids          = selectedFriendUids,
            )
            result.onSuccess {
                val n = selectedFriendUids.size
                _events.emit(ShareUiEvent.Success("Playlist shared with $n friend${if (n > 1) "s" else ""}!"))
            }.onFailure { e ->
                _events.emit(ShareUiEvent.Error(e.message ?: "Failed to share playlist"))
            }
            _isSharing.value = false
        }
    }

    fun shareSong(song: Song, selectedFriendUids: List<String>) {
        if (selectedFriendUids.isEmpty()) {
            viewModelScope.launch { _events.emit(ShareUiEvent.Error("Please select at least one friend")) }
            return
        }
        viewModelScope.launch {
            _isSharing.value = true
            val result = friendRepository.shareSong(song, selectedFriendUids)
            result.onSuccess {
                val n = selectedFriendUids.size
                _events.emit(ShareUiEvent.Success("'${song.title}' shared with $n friend${if (n > 1) "s" else ""}!"))
            }.onFailure { e ->
                _events.emit(ShareUiEvent.Error(e.message ?: "Failed to share song"))
            }
            _isSharing.value = false
        }
    }
}
