package com.waleve.player.data.mapper

import com.waleve.player.data.local.dao.AlbumInfo
import com.waleve.player.data.local.dao.ArtistInfo
import com.waleve.player.data.local.entity.PlaylistEntity
import com.waleve.player.data.local.entity.PlaylistWithSongs
import com.waleve.player.data.local.entity.SongEntity
import com.waleve.player.domain.model.Album
import com.waleve.player.domain.model.Artist
import com.waleve.player.domain.model.Playlist
import com.waleve.player.domain.model.Song

fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtUri = albumArtUri,
    duration = duration,
    path = path,
    dateAdded = dateAdded,
    genre = genre,
    folder = folder,
    size = size,
    trackNumber = trackNumber,
    year = year,
    source = source,
    license = license,
    playCount = playCount,
    lastPlayed = lastPlayed,
    isFavorite = isFavorite,
    videoId = videoId,
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtUri = albumArtUri,
    duration = duration,
    path = path,
    dateAdded = dateAdded,
    genre = genre,
    folder = folder,
    size = size,
    trackNumber = trackNumber,
    year = year,
    source = source,
    license = license,
    playCount = playCount,
    lastPlayed = lastPlayed,
    isFavorite = isFavorite,
    videoId = videoId,
)

fun AlbumInfo.toDomain(): Album = Album(
    name = album,
    artist = artist,
    artUri = albumArtUri,
    songCount = songCount,
    year = year,
)

fun ArtistInfo.toDomain(): Artist = Artist(
    name = artist,
    songCount = songCount,
    albumCount = albumCount,
)

fun PlaylistEntity.toDomain(songs: List<Song> = emptyList()): Playlist = Playlist(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    artworkUri = artworkUri,
    songs = songs,
)

fun PlaylistWithSongs.toDomain(): Playlist = playlist.toDomain(
    songs = songs.map { it.toDomain() }
)
