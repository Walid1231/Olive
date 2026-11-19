package com.waleve.player.domain.model

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    /** A song you just tapped that is still being fetched (shown with a spinner). */
    val loadingSong: Song? = null,
    /** The player is waiting for data (start of a stream, slow internet). */
    val isBuffering: Boolean = false,
) {
    /** What the mini player / widget should show right now. */
    val shownSong: Song? get() = loadingSong ?: currentSong
    val isLoading: Boolean get() = loadingSong != null || isBuffering
}

enum class RepeatMode {
    OFF, ONE, ALL
}
