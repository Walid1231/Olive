package com.waleve.player.di

import com.waleve.player.data.remote.InnerTubeApiService
import com.waleve.player.data.remote.SearchResponse
import com.waleve.player.data.remote.PlayerResponse
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val INNERTUBE_BASE_URL = "https://music.youtube.com/youtubei/v1/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            // Try IPv4 addresses first: on networks with broken IPv6 each IPv6 attempt would
            // otherwise wait for a full timeout before falling back (that alone can add 10–30 s).
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> =
                    okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
            })
            .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideInnerTubeApiService(
        okHttpClient: OkHttpClient,
        json: Json,
    ): InnerTubeApiService {
        return Retrofit.Builder()
            .baseUrl(INNERTUBE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(InnerTubeApiService::class.java)
    }
}
