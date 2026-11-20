package com.waleve.player.data.remote

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager

/**
 * NewPipe's YouTube player-code helpers: decode the "signature" and the throttling ("n") value
 * of links from TV clients, the same way Echo / Metrolist / NewPipe do — in about a second,
 * instead of yt-dlp's 20–40 s on a phone. The player code is fetched once and cached.
 */
object NewPipeBridge {
    @Volatile private var ready = false

    fun init(client: OkHttpClient) {
        if (ready) return
        synchronized(this) {
            if (!ready) {
                NewPipe.init(OkDownloader(client))
                ready = true
            }
        }
    }

    /** Which player version YouTube must sign links for (sent with the player request). */
    fun signatureTimestamp(videoId: String): Int? =
        runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }.getOrNull()

    fun decipher(videoId: String, obfuscated: String): String =
        YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscated)

    /** Fix the "n" value so YouTube doesn't throttle the download. */
    fun unthrottle(videoId: String, url: String): String =
        runCatching { YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url) }
            .getOrDefault(url)

    private class OkDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val b = okhttp3.Request.Builder().url(request.url())
            request.headers()?.forEach { (name, values) ->
                b.removeHeader(name)
                values.forEach { b.addHeader(name, it) }
            }
            if (request.headers()?.keys?.none { it.equals("User-Agent", true) } != false) {
                b.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            }
            val data = request.dataToSend()
            val method = request.httpMethod()
            b.method(method, data?.toRequestBody() ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody() else null)
            client.newCall(b.build()).execute().use { r ->
                if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
                return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
            }
        }
    }
}
