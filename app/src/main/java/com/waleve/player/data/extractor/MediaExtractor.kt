package com.waleve.player.data.extractor

import android.content.Context
import com.waleve.player.domain.repository.AudioQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
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
    val savedPath: String? = null,          // absolute path of the saved file in shared storage
    val category: DownloadCategory = DownloadCategory.SONG,
)

/** Where a download goes — songs and audiobooks live in separate libraries. */
enum class DownloadCategory {
    SONG, AUDIOBOOK, VIDEO;

    companion object {
        fun forFormat(format: MediaFormat): DownloadCategory =
            if (format.isAudioOnly) SONG else VIDEO
    }
}


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

            // --- Quick picks: every audio format + common video sizes ---
            val bestAudioKbps = allFormats.filter { it.isAudioOnly }.maxOfOrNull { it.bitrate } ?: 160
            val maxHeight = allFormats.filter { !it.isAudioOnly }.maxOfOrNull { it.height } ?: 0
            val quickFormats = audioConversionChoices(duration, bestAudioKbps) +
                videoChoices(duration, maxHeight)
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
        category: DownloadCategory = DownloadCategory.forFormat(format),
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
            request.addOption("-o", "${cacheDirFile.absolutePath}/%(title).120B [%(id)s].%(ext)s")
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--embed-metadata")
            // Print the final file path after all post-processing (conversion, merging, tagging)
            request.addOption("--print", "after_move:filepath")
            request.addOption("--no-simulate")
            request.addOption("--progress")   // --print implies --quiet; keep progress lines for the UI
            request.addOption("--newline")

            val fid = format.formatId
            when {
                fid.startsWith(CONVERT_PREFIX) -> {
                    // Audio conversion: mp3 / m4a / opus / flac / wav / vorbis(ogg) / alac
                    val codec = fid.removePrefix(CONVERT_PREFIX)
                    request.addOption("-f", "bestaudio/best")
                    request.addOption("-x")
                    request.addOption("--audio-format", codec)
                    request.addOption("--audio-quality", if (codec == "mp3") "320K" else "0")
                }
                fid.startsWith(VIDEO_PREFIX) -> {
                    val h = fid.removePrefix(VIDEO_PREFIX).toIntOrNull()
                    val sel = if (h == null) "bv*+ba/b" else "bv*[height<=$h]+ba/b[height<=$h]/b"
                    request.addOption("-f", sel)
                    request.addOption("--merge-output-format", "mp4")
                    request.addOption("--remux-video", "mp4")
                }
                fid == "mp3_320" -> {           // legacy id kept for old callers
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "320K")
                }
                format.isVideoOnly -> {
                    request.addOption("-f", "$fid+bestaudio")
                    request.addOption("--merge-output-format", "mp4")
                }
                else -> request.addOption("-f", fid)
            }

            if (category == DownloadCategory.AUDIOBOOK) {
                // Each book becomes its own album: playlist title (multi-part books) or the video title
                request.addOption("--parse-metadata", "%(playlist_title,title)s:%(album)s")
                request.addOption("--parse-metadata", "%(uploader,channel)s:%(album_artist)s")
                request.addOption("--embed-chapters")
            }

            // Cover art inside the file (MediaStore uses it as album art). WAV can't hold images.
            val targetExt = expectedExtension(format)
            if (targetExt !in setOf("wav", "webm")) {
                request.addOption("--embed-thumbnail")
                if (category != DownloadCategory.VIDEO) request.addOption("--convert-thumbnails", "jpg")
            }

            val response = YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                onProgress(progress, etaInSeconds, line ?: "")
            }

            val printed = response.out.lines().map { it.trim() }
                .lastOrNull { it.startsWith("/") && java.io.File(it).exists() }
            val newFile = printed?.let { java.io.File(it) } ?: run {
                val afterFiles = cacheDirFile.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()
                (afterFiles - beforeFiles).map { java.io.File(it) }
                    .filter { it.extension.lowercase() in MEDIA_EXTENSIONS }
                    .maxByOrNull { it.length() }
            } ?: return@withContext Result.failure(Exception("Download finished but no file was produced."))

            onProgress(100f, 0, "SAVING_TO_LIBRARY")
            val savedPath = saveToSharedStorage(newFile, category)

            // Clean up everything this download left in the cache (file, thumbnails, .part files)
            cacheDirFile.listFiles()?.filter { it.absolutePath !in beforeFiles }?.forEach { it.delete() }

            val where = when (category) {
                DownloadCategory.SONG -> "Songs"
                DownloadCategory.AUDIOBOOK -> "Audiobooks"
                DownloadCategory.VIDEO -> "Videos (Movies/Olive)"
            }
            Result.success(
                DownloadResult(
                    message = "Saved to $where!",
                    videoId = videoId,
                    thumbnailUrl = extractThumbnailUrl(url),
                    savedPath = savedPath,
                    category = category,
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("MediaExtractor", "Download failed", e)
            val msg = e.message ?: ""
            val cleanMsg = when {
                msg.contains("Forbidden") || msg.contains("403") -> "Download blocked by YouTube. Please try again later."
                msg.contains("UnknownHostException") || msg.contains("resolve host") -> "No internet connection."
                msg.contains("Requested format is not available") -> "That quality isn't available for this video."
                else -> "Failed to download."
            }
            Result.failure<DownloadResult>(Exception(cleanMsg))
        }
    }

    /**
     * Copy a finished file into public storage so it shows up in the library / gallery:
     *   Songs → Music/Olive, Audiobooks → Audiobooks/Olive, Videos → Movies/Olive.
     * Returns the absolute path of the saved file (used to tag the song in Room).
     */
    @Suppress("DEPRECATION")
    suspend fun saveToSharedStorage(file: java.io.File, category: DownloadCategory): String? = withContext(Dispatchers.IO) {
        val ext = file.extension.lowercase()
        val mime = mimeTypeFor(ext, category == DownloadCategory.VIDEO)
        val baseDir = when (category) {
            DownloadCategory.SONG -> android.os.Environment.DIRECTORY_MUSIC
            DownloadCategory.AUDIOBOOK ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                    android.os.Environment.DIRECTORY_AUDIOBOOKS else "Audiobooks"
            DownloadCategory.VIDEO -> android.os.Environment.DIRECTORY_MOVIES
        }
        val displayName = file.name

        val savedPath: String? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/$FOLDER_NAME")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                if (category == DownloadCategory.AUDIOBOOK) put(android.provider.MediaStore.Audio.Media.IS_AUDIOBOOK, 1)
                if (category == DownloadCategory.SONG) put(android.provider.MediaStore.Audio.Media.IS_MUSIC, 1)
            }
            val collection = if (category == DownloadCategory.VIDEO)
                android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val itemUri = resolver.insert(collection, values) ?: return@withContext null
            resolver.openOutputStream(itemUri)?.use { out ->
                java.io.BufferedInputStream(java.io.FileInputStream(file), 1 shl 20).use { it.copyTo(out, 1 shl 20) }
            }
            values.clear()
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            resolver.query(itemUri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } else {
            // Android 8–9: write directly (WRITE_EXTERNAL_STORAGE is granted up to API 28)
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(baseDir), FOLDER_NAME)
            dir.mkdirs()
            var dest = java.io.File(dir, displayName)
            var n = 1
            while (dest.exists()) { dest = java.io.File(dir, "${file.nameWithoutExtension} ($n).$ext"); n++ }
            file.copyTo(dest)
            dest.absolutePath
        }

        // Make sure MediaStore has read duration / tags before the library rescans
        savedPath?.let { awaitMediaScan(it, mime) }
        savedPath
    }

    private suspend fun awaitMediaScan(path: String, mime: String) {
        kotlinx.coroutines.withTimeoutOrNull(8_000) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                android.media.MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime)) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    companion object {
        const val FOLDER_NAME = "Olive"
        const val CONVERT_PREFIX = "convert_"
        const val VIDEO_PREFIX = "video_"
        val MEDIA_EXTENSIONS = setOf("mp3", "m4a", "opus", "ogg", "flac", "wav", "webm", "mp4", "mkv", "aac", "alac")

        fun mimeTypeFor(ext: String, isVideo: Boolean): String = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac", "alac" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/x-wav"
            "webm" -> if (isVideo) "video/webm" else "audio/webm"
            "mkv" -> "video/x-matroska"
            "mp4" -> if (isVideo) "video/mp4" else "audio/mp4"
            else -> if (isVideo) "video/mp4" else "audio/mpeg"
        }

        fun expectedExtension(format: MediaFormat): String = when {
            format.formatId.startsWith(CONVERT_PREFIX) -> format.extension
            format.formatId.startsWith(VIDEO_PREFIX) -> "mp4"
            else -> format.extension
        }

        /**
         * Audio formats offered everywhere (Download screen + Search).
         * YouTube streams are lossy (~128–160 kbps Opus/AAC); FLAC/WAV keep that exact quality
         * in a lossless container — useful for players/editors that need them, but files are much bigger.
         */
        fun audioConversionChoices(durationSec: Long = 0, sourceKbps: Int = 160): List<MediaFormat> {
            fun est(kbps: Int) = if (durationSec > 0) durationSec * kbps * 1000 / 8 else 0L
            fun fmt(codec: String, ext: String, label: String, tag: String, kbps: Int) = MediaFormat(
                formatId = CONVERT_PREFIX + codec, extension = ext, quality = if (kbps > 0) "${kbps}K" else "Lossless",
                fileSize = est(kbps), isAudioOnly = true, isVideoOnly = false,
                label = label, tag = tag, bitrate = kbps,
            )
            return listOf(
                fmt("mp3", "mp3", "MP3 · 320 kbps", "Works everywhere", 320),
                fmt("m4a", "m4a", "M4A / AAC", "Small · great on phones", maxOf(sourceKbps, 128)),
                fmt("opus", "opus", "Opus", "Smallest · original codec", maxOf(sourceKbps, 128)),
                fmt("flac", "flac", "FLAC", "Lossless container · big", 900),
                fmt("wav", "wav", "WAV", "Uncompressed · huge", 1411),
                fmt("vorbis", "ogg", "OGG Vorbis", "Open format", 192),
            )
        }

        fun videoChoices(durationSec: Long = 0, maxHeight: Int = 1080): List<MediaFormat> {
            val heights = listOf(360, 480, 720, 1080, 1440, 2160).filter { maxHeight == 0 || it <= maxOf(maxHeight, 360) }
            fun kbps(h: Int) = when (h) { 360 -> 700; 480 -> 1100; 720 -> 2500; 1080 -> 4500; 1440 -> 9000; else -> 18000 }
            return heights.map { h ->
                MediaFormat(
                    formatId = VIDEO_PREFIX + h, extension = "mp4", quality = "${h}p",
                    fileSize = if (durationSec > 0) durationSec * kbps(h) * 1000 / 8 else 0L,
                    isAudioOnly = false, isVideoOnly = false,
                    label = when (h) { 360 -> "Video 360p"; 480 -> "Video 480p"; 720 -> "Video HD 720p"; 1080 -> "Video Full HD 1080p"; 1440 -> "Video 2K"; else -> "Video 4K" },
                    tag = when (h) { 360, 480 -> "Data saver"; 720 -> "Best for mobile"; 1080 -> "High quality"; else -> "Heavy" },
                    height = h,
                )
            }
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
