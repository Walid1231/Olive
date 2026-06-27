package com.waleve.player.data.remote

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for the YouTube Music InnerTube API.
 * Base URL: https://music.youtube.com/youtubei/v1/
 */
interface InnerTubeApiService {

    @POST("search")
    @Headers(
        "Content-Type: application/json",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://music.youtube.com/",
        "Origin: https://music.youtube.com",
    )
    suspend fun search(
        @Body body: RequestBody,
        @Query("prettyPrint") prettyPrint: Boolean = false,
    ): SearchResponse

    @POST("player")
    @Headers(
        "Content-Type: application/json",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://music.youtube.com/",
        "Origin: https://music.youtube.com",
    )
    suspend fun player(
        @Body body: RequestBody,
        @Query("prettyPrint") prettyPrint: Boolean = false,
    ): PlayerResponse
}
