package com.waleve.player.domain.usecase

import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.LocalMusicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchSongsUseCase @Inject constructor(
    private val repository: LocalMusicRepository,
) {
    operator fun invoke(query: String): Flow<List<Song>> = repository.searchSongs(query)
}
