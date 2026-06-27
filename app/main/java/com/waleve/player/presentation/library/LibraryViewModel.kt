package com.waleve.player.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.domain.model.Album
import com.waleve.player.domain.model.Artist
import com.waleve.player.domain.model.Song
import com.waleve.player.domain.usecase.GetAlbumsUseCase
import com.waleve.player.domain.usecase.GetAllSongsUseCase
import com.waleve.player.domain.usecase.GetArtistsUseCase
import com.waleve.player.domain.usecase.ToggleFavoriteUseCase
import com.waleve.player.domain.repository.LocalMusicRepository
import com.waleve.player.domain.repository.UserPreferencesRepository
import com.waleve.player.domain.model.SortOption
import com.waleve.player.domain.model.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    getAllSongsUseCase: GetAllSongsUseCase,
    getAlbumsUseCase: GetAlbumsUseCase,
    getArtistsUseCase: GetArtistsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val localMusicRepository: LocalMusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val sortOption = userPreferencesRepository.sortOption.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOption.DATE_ADDED)
    val sortOrder = userPreferencesRepository.sortOrder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.DESCENDING)

    val songs: StateFlow<List<Song>> = combine(
        getAllSongsUseCase(),
        userPreferencesRepository.sortOption,
        userPreferencesRepository.sortOrder,
        _searchQuery
    ) { songsList, option, order, query ->
        val filtered = if (query.isBlank()) songsList else songsList.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) || it.album.contains(query, ignoreCase = true)
        }
        val sorted = when (option) {
            SortOption.DATE_ADDED -> filtered.sortedBy { it.dateAdded }
            SortOption.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortOption.SIZE -> filtered.sortedBy { it.size }
            SortOption.DURATION -> filtered.sortedBy { it.duration }
            SortOption.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortOption.ALBUM -> filtered.sortedBy { it.album.lowercase() }
        }
        if (order == SortOrder.DESCENDING) sorted.reversed() else sorted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = combine(
        getAlbumsUseCase(),
        _searchQuery
    ) { albumList, query ->
        if (query.isBlank()) albumList else albumList.filter {
            it.name.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = combine(
        getArtistsUseCase(),
        _searchQuery
    ) { artistList, query ->
        if (query.isBlank()) artistList else artistList.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val genres: StateFlow<List<String>> = combine(
        localMusicRepository.getGenres(),
        _searchQuery
    ) { genreList, query ->
        if (query.isBlank()) genreList else genreList.filter {
            it.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<String>> = combine(
        localMusicRepository.getFolders(),
        _searchQuery
    ) { folderList, query ->
        if (query.isBlank()) folderList else folderList.filter {
            it.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(index: Int) { _selectedTab.value = index }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { toggleFavoriteUseCase(songId) }
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch { userPreferencesRepository.setSortOption(option) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { userPreferencesRepository.setSortOrder(order) }
    }

    fun deleteSong(songId: Long) {
        viewModelScope.launch { localMusicRepository.deleteSong(songId) }
    }
}
