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
        ).fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()

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
