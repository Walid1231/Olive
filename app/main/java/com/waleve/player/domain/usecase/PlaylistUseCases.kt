package com.waleve.player.domain.usecase

import com.waleve.player.domain.model.Playlist
import com.waleve.player.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

data class PlaylistUseCases(
    val getAllPlaylists: GetAllPlaylistsUseCase,
    val getPlaylistById: GetPlaylistByIdUseCase,
    val createPlaylist: CreatePlaylistUseCase,
    val deletePlaylist: DeletePlaylistUseCase,
    val addSongToPlaylist: AddSongToPlaylistUseCase,
    val removeSongFromPlaylist: RemoveSongFromPlaylistUseCase,
    val reorderSongs: ReorderPlaylistSongsUseCase,
)

class GetAllPlaylistsUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    operator fun invoke(): Flow<List<Playlist>> = repository.getAllPlaylists()
}

class GetPlaylistByIdUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    operator fun invoke(playlistId: Long): Flow<Playlist?> = repository.getPlaylistById(playlistId)
}

class CreatePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(name: String, description: String? = null): Long =
        repository.createPlaylist(name, description)
}

class DeletePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long) = repository.deletePlaylist(playlistId)
}

class AddSongToPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long, songId: Long) =
        repository.addSongToPlaylist(playlistId, songId)
}

class RemoveSongFromPlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long, songId: Long) =
        repository.removeSongFromPlaylist(playlistId, songId)
}

class ReorderPlaylistSongsUseCase @Inject constructor(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long, songIds: List<Long>) =
        repository.reorderSongs(playlistId, songIds)
}
