package com.waleve.player.presentation.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.domain.model.Playlist
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.usecase.AddSongToPlaylistUseCase
import com.waleve.player.domain.usecase.CreatePlaylistUseCase
import com.waleve.player.domain.usecase.DeletePlaylistUseCase
import com.waleve.player.domain.usecase.GetAllPlaylistsUseCase
import com.waleve.player.domain.usecase.GetAllSongsUseCase
import com.waleve.player.domain.usecase.GetPlaylistByIdUseCase
import com.waleve.player.domain.usecase.RemoveSongFromPlaylistUseCase
import com.waleve.player.domain.usecase.GetFavoritesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    getAllPlaylistsUseCase: GetAllPlaylistsUseCase,
    private val getPlaylistByIdUseCase: GetPlaylistByIdUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    private val removeSongFromPlaylistUseCase: RemoveSongFromPlaylistUseCase,
    getFavoritesUseCase: GetFavoritesUseCase,
    getAllSongsUseCase: GetAllSongsUseCase,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = getAllPlaylistsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Song>> = getFavoritesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSongs: StateFlow<List<Song>> = getAllSongsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id != null) getPlaylistByIdUseCase(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectPlaylist(playlistId: Long) {
        _selectedPlaylistId.value = playlistId
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch { createPlaylistUseCase(name, description) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { deletePlaylistUseCase(playlistId) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { addSongToPlaylistUseCase(playlistId, songId) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { removeSongFromPlaylistUseCase(playlistId, songId) }
    }
}
