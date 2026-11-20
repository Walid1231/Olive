package com.waleve.player.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.waleve.player.domain.repository.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * How Olive streams online songs (YouTube):
 *  • The queue holds "ytm://stream/<id>" — the real link is fetched only when the player needs it
 *    (so the queue appears instantly and the NEXT song gets its link while the current one plays).
 *  • A link that stopped working (expired / refused) is replaced silently: fresh fast link →
 *    yt-dlp link. The player just keeps buffering instead of showing an error.
 *  • Everything streamed is cached (300 MB), so replays and seeking back are instant.
 *  • No internet → keeps retrying calmly instead of failing.
 */
@OptIn(UnstableApi::class)
class StreamEngine(
    context: Context,
    private val repo: StreamRepository,
    okHttp: okhttp3.OkHttpClient,
    /** Headers for a link Olive handed out earlier (links stored inside songs). */
    private val knownHeaders: (String) -> Map<String, String>?,
) {

    /** Link failures per video → asks for a fresh link, skipping the method that failed. */
    private val failures = ConcurrentHashMap<String, Int>()
    private val avoid = ConcurrentHashMap<String, MutableSet<String>>()
    private val lastVia = ConcurrentHashMap<String, String>()

    fun failed(videoId: String) {
        failures.merge(videoId, 1, Int::plus)
        lastVia[videoId]?.let { avoid.getOrPut(videoId) { ConcurrentHashMap.newKeySet() }.add(it) }
        repo.blame(videoId)
        Log.w(TAG, "Link failed for $videoId (${failures[videoId]}×, via ${lastVia[videoId]}) — getting another")
    }

    fun worked(videoId: String) { failures.remove(videoId); avoid.remove(videoId) }

    /** ytm://stream/<id> → playable link (with the headers YouTube expects). */
    private fun resolve(spec: DataSpec): DataSpec {
        val uri = spec.uri
        if (uri.scheme == "ytm" && uri.host == "stream") {
            val id = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: throw IOException("Bad stream id")
            val tries = failures[id] ?: 0
            val link = runBlocking(Dispatchers.IO) {
                repo.getStream(id, fresh = tries > 0, avoid = avoid[id]?.toSet().orEmpty())
            }
            if (link == null) {
                failed(id)
                throw IOException("No stream link for $id")
            }
            lastVia[id] = link.via
            return spec.buildUpon()
                .setUri(Uri.parse(link.url))
                .setCustomData(id)
                .setHttpRequestHeaders(spec.httpRequestHeaders + link.headers)
                .build()
        }
        if (uri.host?.endsWith("googlevideo.com") == true) {
            val h = knownHeaders(uri.toString()) ?: YouTubeHeaders.forClient(uri.getQueryParameter("c").orEmpty())
            return spec.withAdditionalHeaders(h)
        }
        return spec
    }

    // OkHttp (shared connections, IPv4 first) — connects to YouTube's servers faster than the default stack
    private val http = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
        okHttp.newBuilder()
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(true)
            .build(),
    )

    private val plain = DefaultDataSource.Factory(context, http)

    private val online: DataSource.Factory = ResolvingDataSource.Factory(
        DataSource.Factory { ReportingDataSource(plain.createDataSource()) },
    ) { spec -> resolve(spec) }

    private val cached: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache(context))
        .setUpstreamDataSourceFactory(online)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Online songs go through the cache; local files are read directly. */
    val dataSourceFactory: DataSource.Factory = DataSource.Factory {
        object : DataSource {
            private var current: DataSource? = null
            private val listeners = mutableListOf<TransferListener>()
            override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }
            override fun open(dataSpec: DataSpec): Long {
                val s = dataSpec.uri.scheme
                val ds = when (s) {
                    "ytm" -> cached.createDataSource()
                    "http", "https" -> online.createDataSource()
                    else -> plain.createDataSource()
                }
                listeners.forEach { ds.addTransferListener(it) }
                current = ds
                return ds.open(dataSpec)
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int) =
                current?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
            override fun getUri(): Uri? = current?.uri
            override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()
            override fun close() { try { current?.close() } finally { current = null } }
        }
    }

    /** Quick retries for refused links, patient retries while offline. */
    val loadErrorPolicy: LoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val e = info.exception
            return when {
                e is HttpDataSource.InvalidResponseCodeException -> if (info.errorCount <= 3) 250L else C.TIME_UNSET
                isOffline(e) -> 2_500L
                e.message?.startsWith("No stream link") == true -> if (info.errorCount <= 3) 400L else C.TIME_UNSET
                else -> super.getRetryDelayMsFor(info)
            }
        }
        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 12
    }

    /** Start sooner: ~0.7 s of audio is enough to begin. */
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(20_000, 60_000, 700, 2_000)
        .build()

    companion object {
        private const val TAG = "StreamEngine"
        @Volatile private var cacheInstance: SimpleCache? = null

        fun cache(context: Context): SimpleCache = cacheInstance ?: synchronized(this) {
            cacheInstance ?: SimpleCache(
                File(context.cacheDir, "media_cache"),
                LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
                StandaloneDatabaseProvider(context),
            ).also { cacheInstance = it }
        }

        fun isOffline(e: Throwable?): Boolean {
            var t = e
            while (t != null) {
                if (t is UnknownHostException || t is ConnectException || t is SocketTimeoutException) return true
                if (t is HttpDataSource.HttpDataSourceException &&
                    t.type == HttpDataSource.HttpDataSourceException.TYPE_OPEN && t.cause is IOException &&
                    t.cause !is HttpDataSource.InvalidResponseCodeException) return true
                t = t.cause
            }
            return false
        }
    }
}
