package com.waleve.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [Index(value = ["path"], unique = true)]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUri: String? = null,
    val duration: Long = 0,
    val path: String,
    val dateAdded: Long = 0,
    val genre: String? = null,
    val folder: String? = null,
    val size: Long = 0,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val source: String? = "local",
    val license: String? = null,
    val playCount: Int = 0,
    val lastPlayed: Long? = null,
    val isFavorite: Boolean = false,
    val videoId: String? = null,   // YouTube videoId — null for local-only songs
)
