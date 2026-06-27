package com.waleve.player.domain.usecase

import com.waleve.player.domain.model.Song
import com.waleve.player.domain.repository.LocalMusicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMostPlayedUseCase @Inject constructor(
    private val repository: LocalMusicRepository,
) {
    operator fun invoke(limit: Int = 50): Flow<List<Song>> = repository.getMostPlayed(limit)
}
