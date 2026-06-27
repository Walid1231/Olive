package com.waleve.player.di

import com.waleve.player.data.repository.AuthRepositoryImpl
import com.waleve.player.data.repository.FriendRepositoryImpl
import com.waleve.player.data.repository.LocalMusicRepositoryImpl
import com.waleve.player.data.repository.PlaylistRepositoryImpl
import com.waleve.player.data.repository.SharedPlaylistRepositoryImpl
import com.waleve.player.data.repository.StreamRepositoryImpl
import com.waleve.player.domain.repository.AuthRepository
import com.waleve.player.domain.repository.FriendRepository
import com.waleve.player.domain.repository.LocalMusicRepository
import com.waleve.player.domain.repository.PlaylistRepository
import com.waleve.player.domain.repository.SharedPlaylistRepository
import com.waleve.player.domain.repository.StreamRepository
import com.waleve.player.data.repository.LyricsRepositoryImpl
import com.waleve.player.domain.repository.LyricsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindLocalMusicRepository(impl: LocalMusicRepositoryImpl): LocalMusicRepository

    @Binds @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds @Singleton
    abstract fun bindStreamRepository(impl: StreamRepositoryImpl): StreamRepository

    @Binds @Singleton
    abstract fun bindFriendRepository(impl: FriendRepositoryImpl): FriendRepository

    @Binds @Singleton
    abstract fun bindSharedPlaylistRepository(impl: SharedPlaylistRepositoryImpl): SharedPlaylistRepository

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository
}

