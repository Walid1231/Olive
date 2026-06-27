package com.waleve.player.data.extractor

import android.content.Context
import com.waleve.player.domain.repository.AudioQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain model for a single downloadable format (audio or video).
 */
data class MediaFormat(
    val formatId: String,
    val extension: String,         // "m4a", "mp3", "mp4", "webm"
    val quality: String,           // "128K", "320K", "720p", "1080p"
    val fileSize: Long,            // bytes (estimated)
    val isAudioOnly: Boolean,
    val isVideoOnly: Boolean,
    val label: String,             // human-friendly: "Fast Audio", "Classic MP3 320K", "HD 720p"
    val tag: String,               // contextual UX tag: "Fast", "Best for mobile", "Slow"
    val bitrate: Int = 0,          // audio bitrate in kbps
    val height: Int = 0,           // video height in pixels
)

/**
 * Result of extracting metadata from a URL.
 */
data class MediaInfo(
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val duration: Long,            // seconds
    val quickFormats: List<MediaFormat>,   // curated top picks
    val advancedFormats: List<MediaFormat>, // all available formats
)

/**
 * A minimal representation of an item inside a playlist.
 */
data class PlaylistItem(
    val title: String,
    val uploader: String,
    val url: String,
    val duration: Long,
)

/**
 * A search result from yt-dlp ytsearch.
 */
data class YtSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSec: Long,           // duration in seconds
)

/**
 * Result returned from [MediaExtractor.download] containing the success message
 * and the YouTube videoId extracted from the URL (used to tag the song in Room DB).
 */
data class DownloadResult(
    val message: String,
    val videoId: String? = null,
    val thumbnailUrl: String? = null,
)


@Singleton
class MediaExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var initialized = false
    private var updated = false

    suspend fun initialize() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().init(context)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
            initialized = true
        }
    }

    /**
     * Updates yt-dlp to the latest version so YouTube extraction works.
     * Called automatically before each extraction if not yet updated.
     */
    suspend fun ensureUpdated() {
        if (updated) return
        withContext(Dispatchers.IO) {
            try {
                // Try updating via Nightly channel first to get fastest bypass for YouTube changes
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                updated = true
            } catch (e: Exception) {
                android.util.Log.e("MediaExtractor", "Failed to update yt-dlp using NIGHTLY channel, trying default...", e)
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(context)
                    updated = true
                } catch (ex: Exception) {
                    android.util.Log.e("MediaExtractor", "Failed to update yt-dlp entirely", ex)
                    // Don't set updated = true so we can retry on next download attempt when internet might be back
                }
            }
        }
    }

    /**
     * Fetch metadata and available formats for a given URL.
     */
    suspend fun extractInfo(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            initialize()
            ensureUpdated() // Update to latest yt-dlp to bypass YouTube anti-bot

            val info: VideoInfo = YoutubeDL.getInstance().getInfo(url)

            val title = info.title ?: "Unknown"
            val artist = info.uploader ?: "Unknown Artist"
            val thumbnail = info.thumbnail
            val duration = info.duration.toLong()

            val allFormats = info.formats?.mapNotNull { fmt ->
                val formatId = fmt.formatId ?: return@mapNotNull null
                val ext = fmt.ext ?: "unknown"
                val acodec = fmt.acodec ?: "none"
                val vcodec = fmt.vcodec ?: "none"
                val isAudioOnly = vcodec == "none" && acodec != "none"
                val isVideoOnly = acodec == "none" && vcodec != "none"
                val height = fmt.height
                val abr = fmt.abr
                val fileSize = if (fmt.fileSize > 0) fmt.fileSize else fmt.fileSizeApproximate

                val quality: String
                val label: String
                val tag: String

                if (isAudioOnly) {
                    quality = "${abr}K"
                    label = when {
                        ext == "m4a" && abr <= 128 -> "Fast Audio (M4A ${abr}K)"
                        ext == "m4a"               -> "HQ Audio (M4A ${abr}K)"
                        else                       -> "Audio ($ext ${abr}K)"
                    }
                    tag = when {
                        abr <= 70  -> "Low"
                        abr <= 128 -> "Fast"
                        abr <= 192 -> "Good"
                        else       -> "Best quality"
                    }
                } else if (isVideoOnly) {
                    quality = "${height}p"
                    label = when {
                        height <= 360  -> "Data Saver (${height}p)"
                        height <= 480  -> "Standard (${height}p)"
                        height <= 720  -> "HD (${height}p)"
                        height <= 1080 -> "Full HD (${height}p)"
                        else           -> "Ultra HD (${height}p)"
                    }
                    tag = when {
                        height <= 360  -> "Data saver"
                        height <= 480  -> "Good"
                        height <= 720  -> "Best for mobile"
                        height <= 1080 -> "High quality"
                        else           -> "Slow / Heavy"
                    }
                } else {
                    // Muxed (has both)
                    quality = if (height > 0) "${height}p" else "${abr}K"
                    label = if (height > 0) "Video ${height}p ($ext)" else "Muxed ($ext)"
                    tag = if (height <= 480) "Fast" else "Good"
                }

                MediaFormat(
                    formatId = formatId,
                    extension = ext,
                    quality = quality,
                    fileSize = fileSize,
                    isAudioOnly = isAudioOnly,
                    isVideoOnly = isVideoOnly,
                    label = label,
                    tag = tag,
                    bitrate = abr,
                    height = height,
                )
            } ?: emptyList()

            // --- Build Quick Formats (curated top picks) ---
            val quickFormats = mutableListOf<MediaFormat>()

            // Fast Audio: best native M4A ≤ 128K
            allFormats
                .filter { it.isAudioOnly && it.extension == "m4a" && it.bitrate <= 128 }
                .maxByOrNull { it.bitrate }
                ?.let { quickFormats.add(it.copy(label = "Fast Audio (M4A)", tag = "Fast · No processing")) }

            // Classic MP3 320K (virtual — will be transcoded)
            allFormats
                .filter { it.isAudioOnly }
                .maxByOrNull { it.bitrate }
                ?.let {
                    quickFormats.add(
                        MediaFormat(
                            formatId = "mp3_320",
                            extension = "mp3",
                            quality = "320K",
                            fileSize = (duration * 320 * 1000 / 8),
                            isAudioOnly = true,
                            isVideoOnly = false,
                            label = "Classic MP3 (320K)",
                            tag = "Classic · Transcoded",
                            bitrate = 320,
                        )
                    )
                }

            // Fast Video: best ≤ 480p muxed or video-only
            allFormats
                .filter { !it.isAudioOnly && it.height in 1..480 }
                .maxByOrNull { it.height }
                ?.let { quickFormats.add(it.copy(label = "Fast Video (${it.height}p)", tag = "Data saver")) }

            // HQ Video: best ≤ 1080p
            allFormats
                .filter { !it.isAudioOnly && it.height in 481..1080 }
                .maxByOrNull { it.height }
                ?.let { quickFormats.add(it.copy(label = "HD Video (${it.height}p)", tag = "Best for mobile")) }

            Result.success(
                MediaInfo(
                    title = title,
                    artist = artist,
                    thumbnailUrl = thumbnail,
                    duration = duration,
                    quickFormats = quickFormats,
                    advancedFormats = allFormats.sortedWith(
                        compareBy<MediaFormat> { !it.isAudioOnly }
                            .thenByDescending { it.bitrate }
                            .thenByDescending { it.height }
                    ),
                )
            )
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            val cleanMsg = when {
                msg.contains("Unsupported URL") -> "Unsupported URL. Please check the link."
                msg.contains("Video unavailable") -> "Video unavailable or private."
                msg.contains("Sign in to confirm") -> "Age-restricted video requires sign-in."
                msg.contains("challenge request") -> "YouTube blocked the request. Try again later."
                else -> "Extraction failed. Ensure the link is valid."
            }
            Result.failure(Exception(cleanMsg))
        }
    }

    /**
     * Search YouTube Music using yt-dlp's ytsearch feature.
     * This is far more reliable than calling the InnerTube API directly,
     * because yt-dlp self-updates to bypass YouTube's anti-bot measures.
     *
     * Returns a list of search results with video IDs, titles, artists, thumbnails, and durations.
     */
    suspend fun searchYouTubeMusic(
        query: String,
        maxResults: Int = 20,
    ): List<YtSearchResult> = withContext(Dispatchers.IO) {
        try {
            initialize()
            ensureUpdated()

            // Use "ytsearchN:" prefix for YouTube search via yt-dlp
            val searchQuery = "ytsearch${maxResults}:$query"
            val request = YoutubeDLRequest(searchQuery)
            request.addOption("--flat-playlist")   // don't download, just list
            request.addOption("-J")                  // JSON output
            request.addOption("--no-download")

            val response = YoutubeDL.getInstance().execute(request)
            val json = org.json.JSONObject(response.out)
            val entries = json.optJSONArray("entries") ?: return@withContext emptyList()

            val results = mutableListOf<YtSearchResult>()
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val videoId = entry.optString("id", null) ?: continue
                val title = entry.optString("title", "Unknown")
                val uploader = entry.optString("uploader", entry.optString("channel", "Unknown Artist"))
                val duration = entry.optDouble("duration", 0.0).toLong()
                val thumbnailUrl = extractBestThumbnail(entry)

                results.add(
                    YtSearchResult(
                        videoId      = videoId,
                        title        = title,
                        artist       = uploader,
                        thumbnailUrl = thumbnailUrl,
                        durationSec  = duration,
                    )
                )
            }
            results
        } catch (e: Exception) {
            android.util.Log.e("MediaExtractor", "searchYouTubeMusic failed for '$query'", e)
            emptyList()
        }
    }

    /**
     * Helper: extract the best thumbnail URL from a yt-dlp JSON entry.
     */
    private fun extractBestThumbnail(entry: org.json.JSONObject): String? {
        // Try "thumbnails" array first (pick largest)
        val thumbnails = entry.optJSONArray("thumbnails")
        if (thumbnails != null && thumbnails.length() > 0) {
            var best: String? = null
            var bestArea = 0
            for (i in 0 until thumbnails.length()) {
                val t = thumbnails.optJSONObject(i) ?: continue
                val url = t.optString("url", null) ?: continue
                val w = t.optInt("width", 0)
                val h = t.optInt("height", 0)
                val area = w * h
                if (area > bestArea || best == null) {
                    best = url
                    bestArea = area
                }
            }
            if (best != null) return best
        }
        // Fallback to "thumbnail" string field
        return entry.optString("thumbnail", null)
    }

    /**
     * Extracts the best direct audio stream URL for a given video using yt-dlp --get-url.
     * This is the most reliable way to get a real playable URL.
     * Returns null if extraction fails.

     */
    suspend fun extractStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            initialize()
            ensureUpdated()

            val url = "https://music.youtube.com/watch?v=$videoId"
            val request = YoutubeDLRequest(url)
            request.addOption("-f", "bestaudio")
            request.addOption("--get-url")
            request.addOption("--no-playlist")

            val response = YoutubeDL.getInstance().execute(request)
            val streamUrl = response.out.trim().lines().firstOrNull { it.startsWith("http") }
            streamUrl
        } catch (e: Exception) {
            android.util.Log.e("MediaExtractor", "extractStreamUrl failed for $videoId", e)
            null
        }
    }

    /**
     * Extracts all available audio qualities with their real stream URLs.
     * Uses yt-dlp's -J output to parse format URLs.
     */
    suspend fun extractAudioQualities(videoId: String): List<AudioQuality> = withContext(Dispatchers.IO) {
        try {
            initialize()
            ensureUpdated()

            val url = "https://music.youtube.com/watch?v=$videoId"
            val request = YoutubeDLRequest(url)
            request.addOption("-J")
            request.addOption("--no-playlist")

            val response = YoutubeDL.getInstance().execute(request)
            val json = org.json.JSONObject(response.out)
            val formats = json.optJSONArray("formats") ?: return@withContext emptyList()

            val qualities = mutableListOf<AudioQuality>()
            for (i in 0 until formats.length()) {
                val fmt = formats.optJSONObject(i) ?: continue
                val mimeType = fmt.optString("ext", "")
                val vcodec = fmt.optString("vcodec", "none")
                val acodec = fmt.optString("acodec", "none")
                val isAudioOnly = vcodec == "none" && acodec != "none"
                if (!isAudioOnly) continue

                val streamUrl = fmt.optString("url", null) ?: continue
                val abr = fmt.optDouble("abr", 0.0).toInt()
                val contentLength = fmt.optLong("filesize", 0L).takeIf { it > 0 }
                    ?: fmt.optLong("filesize_approx", 0L)

                val label = when {
                    abr >= 256 -> "High ($abr kbps)"
                    abr >= 128 -> "Medium ($abr kbps)"
                    else       -> "Low ($abr kbps)"
                }
                qualities.add(
                    AudioQuality(
                        label         = label,
                        bitrate       = abr,
                        mimeType      = "audio/$mimeType",
                        url           = streamUrl,
                        contentLength = contentLength,
                    )
                )
            }
            qualities.sortedByDescending { it.bitrate }
        } catch (e: Exception) {
            android.util.Log.e("MediaExtractor", "extractAudioQualities failed for $videoId", e)
            emptyList()
        }
    }

    /**
     * Extracts items from a playlist URL without downloading them.
     */
    suspend fun extractPlaylistInfo(url: String): Result<List<PlaylistItem>> = withContext(Dispatchers.IO) {
        try {
            initialize()
            ensureUpdated()
            
            val request = YoutubeDLRequest(url)
            request.addOption("-J")
            request.addOption("--flat-playlist")
            
            val response = YoutubeDL.getInstance().execute(request)
            val json = org.json.JSONObject(response.out)
            val entries = json.optJSONArray("entries") ?: return@withContext Result.success(emptyList())
            
            val items = mutableListOf<PlaylistItem>()
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val itemTitle = entry.optString("title", "Unknown")
                val itemUploader = entry.optString("uploader", "Unknown Artist")
                val itemUrl = entry.optString("url", "")
                val itemDuration = entry.optLong("duration", 0L)
                
                // If url is not full (sometimes just an ID), fix it
                val fullUrl = if (itemUrl.startsWith("http")) itemUrl else "https://www.youtube.com/watch?v=$itemUrl"
                
                items.add(PlaylistItem(
                    title = itemTitle,
                    uploader = itemUploader,
                    url = fullUrl,
                    duration = itemDuration
                ))
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a specific format.
     * @param outputDir directory to save to
     * @param onProgress callback with progress percentage (0-100)
     */
    /**
     * Cancel an active download by killing the native yt-dlp process.
     */
    fun cancelDownload(processId: String) {
        YoutubeDL.getInstance().destroyProcessById(processId)
    }

    suspend fun download(
        url: String,
        format: MediaFormat,
        processId: String,
        onProgress: (Float, Long, String) -> Unit = { _, _, _ -> },
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        // Extract videoId from the URL so callers can tag it in Room DB after scan
        val videoId = extractVideoIdFromUrl(url)
        try {
            initialize()
            ensureUpdated()
            
            val cacheDirFile = java.io.File(context.cacheDir, "yt_dlp_downloads")
            cacheDirFile.mkdirs()
            val beforeFiles = cacheDirFile.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()
            
            val request = YoutubeDLRequest(url)
            request.addOption("-o", "${cacheDirFile.absolutePath}/%(title)s.%(ext)s")
            request.addOption("--restrict-filenames")

            if (format.formatId == "mp3_320") {
                // Transcode to MP3
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
            } else if (format.isAudioOnly) {
                request.addOption("-f", format.formatId)
            } else if (format.isVideoOnly) {
                // Merge best audio with video
                request.addOption("-f", "${format.formatId}+bestaudio")
                request.addOption("--merge-output-format", "mp4")
            } else {
                request.addOption("-f", format.formatId)
            }

            // Embed thumbnail into the audio/video file so MediaStore can serve album art
            if (!format.isVideoOnly) {
                request.addOption("--embed-thumbnail")
            }

            val response = YoutubeDL.getInstance().execute(
                request, processId
            ) { progress, etaInSeconds, line ->
                onProgress(progress, etaInSeconds, line ?: "")
            }

            val afterFiles = cacheDirFile.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()
            val newFiles = afterFiles - beforeFiles

            if (newFiles.isNotEmpty()) {
                // Signal UI that we're saving
                onProgress(100f, 0, "SAVING_TO_LIBRARY")
                
                val newFilePath = newFiles.first()
                val newFile = java.io.File(newFilePath)
                
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, newFile.name)
                    if (format.isVideoOnly) {
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/WaLEve")
                    } else {
                        val isMp3 = format.formatId == "mp3_320" || newFile.extension.lowercase() == "mp3"
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (isMp3) "audio/mpeg" else "audio/mp4")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_MUSIC + "/WaLEve")
                    }
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = if (format.isVideoOnly) {
                    android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                
                val itemUri = resolver.insert(collection, contentValues)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { outStream ->
                        java.io.BufferedInputStream(java.io.FileInputStream(newFile), 8 * 1024 * 1024).use { inStream ->
                            inStream.copyTo(outStream, bufferSize = 8 * 1024 * 1024)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                }
                
                // Cleanup temp file
                newFile.delete()
            }

            Result.success(DownloadResult(message = "Saved to Library!", videoId = videoId, thumbnailUrl = extractThumbnailUrl(url)))
        } catch (e: Exception) {
            android.util.Log.e("MediaExtractor", "Download failed", e)
            val msg = e.message ?: ""
            val cleanMsg = when {
                msg.contains("Forbidden") || msg.contains("403") -> "Download blocked by YouTube. Please try again later."
                msg.contains("UnknownHostException") || msg.contains("resolve host") -> "No internet connection."
                else -> "Failed to download."
            }
            Result.failure<DownloadResult>(Exception(cleanMsg))
        }
    }

    /**
     * Extract YouTube videoId from various URL formats.
     */
    private fun extractVideoIdFromUrl(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.getQueryParameter("v")
                ?: url.substringAfter("watch?v=", "").substringBefore("&").takeIf { it.isNotEmpty() }
                ?: url.substringAfter("youtu.be/", "").substringBefore("?").takeIf { it.isNotEmpty() }
                ?: url.substringAfter("ytm://stream/", "").takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null }
    }

    /**
     * Generate a YouTube thumbnail URL from a video URL.
     * Uses the high-quality `hqdefault` thumbnail which is always available.
     */
    private fun extractThumbnailUrl(url: String): String? {
        val videoId = extractVideoIdFromUrl(url) ?: return null
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }
}
