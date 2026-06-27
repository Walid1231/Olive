package com.waleve.player.domain.model

data class Album(
    val name: String,
    val artist: String,
    val artUri: String? = null,
    val songCount: Int = 0,
    val year: Int? = null,
)
