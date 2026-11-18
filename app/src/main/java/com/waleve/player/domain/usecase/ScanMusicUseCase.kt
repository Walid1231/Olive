package com.waleve.player.domain.usecase

import com.waleve.player.domain.repository.LocalMusicRepository
import javax.inject.Inject

class ScanMusicUseCase @Inject constructor(
    private val repository: LocalMusicRepository,
) {
    suspend operator fun invoke() = repository.scanMusic()
}
