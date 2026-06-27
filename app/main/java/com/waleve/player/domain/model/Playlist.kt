package com.waleve.player.domain.model

data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val artworkUri: String? = null,
    val songs: List<Song> = emptyList(),
)
