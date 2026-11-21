package com.waleve.player.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.waleve.player.data.local.AppDatabase
import com.waleve.player.data.local.dao.PlaylistDao
import com.waleve.player.data.local.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        ).addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
         .fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()

    /** Listening history + "Not interested" list: a separate database, so it never touches the library. */
    @Provides
    @Singleton
    fun provideListeningDatabase(@ApplicationContext context: Context): com.waleve.player.data.history.ListeningDatabase =
        Room.databaseBuilder(context, com.waleve.player.data.history.ListeningDatabase::class.java, com.waleve.player.data.history.ListeningDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideListeningDao(db: com.waleve.player.data.history.ListeningDatabase): com.waleve.player.data.history.ListeningDao = db.dao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()


    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(@ApplicationContext context: Context): com.waleve.player.domain.repository.UserPreferencesRepository {
        return com.waleve.player.data.repository.UserPreferencesRepositoryImpl(context)
    }
}
