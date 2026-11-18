package com.waleve.player.domain.usecase

import com.waleve.player.domain.model.Album
import com.waleve.player.domain.repository.LocalMusicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAlbumsUseCase @Inject constructor(
    private val repository: LocalMusicRepository,
) {
    operator fun invoke(): Flow<List<Album>> = repository.getAlbums()
}
