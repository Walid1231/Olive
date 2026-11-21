package com.waleve.player.data.history

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One listen of a song or audiobook: how much was heard, whether it was finished or skipped.
 * Kept in its own small database (not the library one), only on this phone.
 */
@Entity(tableName = "play_events", indices = [Index("startedAt"), Index("itemKey")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "yt:<videoId>" or "local:<path>". */
    val itemKey: String,
    val videoId: String?,
    val title: String,
    val artist: String,
    val album: String,
    val thumb: String?,
    val audiobook: Boolean,
    val startedAt: Long,
    val listenedMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val skipped: Boolean,
    /** 0–23, local time. */
    val hour: Int,
)

/** Something the user said "Not interested" to: an item, an artist / author, or a whole shelf. */
@Entity(tableName = "not_interested")
data class NotInterestedEntity(
    /** "item:<videoId>", "artist:<name>" or "shelf:<title>" (names lower-cased). */
    @PrimaryKey val blockKey: String,
    val label: String,
    val addedAt: Long,
)

@Dao
interface ListeningDao {
    @Insert
    suspend fun insert(event: PlayEventEntity)

    @Query("SELECT * FROM play_events WHERE startedAt >= :since ORDER BY startedAt DESC")
    suspend fun since(since: Long): List<PlayEventEntity>

    @Query("SELECT COUNT(*) FROM play_events")
    suspend fun count(): Int

    @Query("DELETE FROM play_events WHERE startedAt < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM play_events")
    suspend fun clearHistory()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun block(entry: NotInterestedEntity)

    @Query("DELETE FROM not_interested WHERE blockKey = :key")
    suspend fun unblock(key: String)

    @Query("SELECT * FROM not_interested")
    fun blocked(): Flow<List<NotInterestedEntity>>

    @Query("DELETE FROM not_interested")
    suspend fun clearBlocked()
}

@Database(entities = [PlayEventEntity::class, NotInterestedEntity::class], version = 1, exportSchema = false)
abstract class ListeningDatabase : RoomDatabase() {
    abstract fun dao(): ListeningDao

    companion object {
        const val NAME = "listening_db"
    }
}
