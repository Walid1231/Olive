package com.waleve.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao


    companion object {
        const val DATABASE_NAME = "waleve_db"

        /** v7: "remove from library" without deleting the file. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6: songs vs audiobooks. Keeps all existing playlists & favorites. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'music'")
                db.execSQL("ALTER TABLE songs ADD COLUMN mediaTypeManual INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
