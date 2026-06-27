package com.waleve.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.waleve.player.data.local.dao.PlaylistDao
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.data.local.entity.PlaylistEntity
import com.waleve.player.data.local.entity.PlaylistSongCrossRef
import com.waleve.player.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao


    companion object {
        const val DATABASE_NAME = "waleve_db"
    }
}
