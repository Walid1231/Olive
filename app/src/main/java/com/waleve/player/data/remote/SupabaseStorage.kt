package com.waleve.player.data.remote

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.waleve.player.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny Supabase Storage client (plain REST over OkHttp — no extra SDK).
 *
 * Used to share LOCAL songs / audiobooks with friends:
 *   sender uploads → public URL goes into the friend's Firebase inbox → friend streams or downloads it.
 *
 * Setup (once): create a PUBLIC bucket named `olive-shares` in Supabase and add an INSERT policy
 * for the `anon` role on that bucket. URL + anon key are injected at build time (BuildConfig).
 */
@Singleton
class SupabaseStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    baseClient: OkHttpClient,
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .apply { interceptors().clear() }      // never log / buffer big uploads
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    data class Uploaded(val publicUrl: String, val objectPath: String, val size: Long)

    /**
     * Upload a local media file. [localPath] is the absolute path stored in the library.
     * @param onProgress 0..100
     */
    suspend fun uploadLocalFile(
        localPath: String,
        ownerUid: String,
        mimeType: String,
        onProgress: (Float) -> Unit = {},
    ): Result<Uploaded> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured) return@withContext Result.failure(Exception("File sharing isn't set up (Supabase keys missing in this build)."))
            val size = fileSize(localPath)
            if (size <= 0) return@withContext Result.failure(Exception("Can't read this file."))
            if (size > MAX_UPLOAD_BYTES) {
                return@withContext Result.failure(
                    Exception("File is ${size / 1_048_576} MB — the free sharing limit is ${MAX_UPLOAD_BYTES / 1_048_576} MB per file.")
                )
            }

            val name = File(localPath).name
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(120)
            val objectPath = "shares/$ownerUid/${System.currentTimeMillis()}_$safeName"
            val bucket = BuildConfig.SUPABASE_BUCKET
            val base = BuildConfig.SUPABASE_URL.trimEnd('/')

            val body = object : RequestBody() {
                override fun contentType(): MediaType? = mimeType.toMediaTypeOrNull()
                override fun contentLength(): Long = size
                override fun writeTo(sink: BufferedSink) {
                    openInput(localPath).use { input ->
                        val src = input.source()
                        var written = 0L
                        var lastPct = -1
                        while (true) {
                            val read = src.read(sink.buffer, 64 * 1024)
                            if (read == -1L) break
                            sink.emit()
                            written += read
                            val pct = (written * 100 / size).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct.toFloat()) }
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url("$base/storage/v1/object/$bucket/$objectPath")
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                .header("cache-control", "max-age=31536000")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string()?.take(200) ?: ""
                    val friendly = when (resp.code) {
                        400, 413 -> if ("size" in err.lowercase()) "File too large for the sharing bucket." else "Upload rejected: $err"
                        401, 403 -> "Upload not allowed — check the Supabase bucket policy (anon INSERT)."
                        404 -> "Bucket '$bucket' not found in Supabase."
                        else -> "Upload failed (${resp.code})."
                    }
                    return@withContext Result.failure(Exception(friendly))
                }
            }
            Result.success(Uploaded("$base/storage/v1/object/public/$bucket/$objectPath", objectPath, size))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Upload failed"))
        }
    }

    /** Delete an uploaded share (public URL → object). True if gone (or already gone). */
    suspend fun deleteByPublicUrl(publicUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val marker = "/storage/v1/object/public/"
            val rest = publicUrl.substringAfter(marker, "")
            if (rest.isEmpty()) return@runCatching false
            val bucket = rest.substringBefore('/')
            val path = rest.substringAfter('/')
            val base = BuildConfig.SUPABASE_URL.trimEnd('/')
            val key = BuildConfig.SUPABASE_ANON_KEY
            val req = Request.Builder()
                .url("$base/storage/v1/object/$bucket/$path")
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .delete()
                .build()
            client.newCall(req).execute().use { it.isSuccessful || it.code == 404 }
        }.getOrDefault(false)
    }

    /**
     * Profile picture: shrink to max 512 px JPEG (~50–100 KB) and upload to the public `olive-avatars` bucket.
     */
    suspend fun uploadAvatar(imageUri: android.net.Uri, ownerUid: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured) return@withContext Result.failure(Exception("Photo upload isn't set up in this build."))
            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Couldn't read that image."))
            val src = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(Exception("That file isn't a supported image."))
            // centre-crop to a square, then scale to 512
            val side = minOf(src.width, src.height)
            val square = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val target = minOf(512, side)
            val scaled = android.graphics.Bitmap.createScaledBitmap(square, target, target, true)
            val jpeg = java.io.ByteArrayOutputStream().use { out ->
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray()
            }

            val base = BuildConfig.SUPABASE_URL.trimEnd('/')
            val objectPath = "avatars/$ownerUid/${System.currentTimeMillis()}.jpg"
            val request = Request.Builder()
                .url("$base/storage/v1/object/$AVATAR_BUCKET/$objectPath")
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                .post(jpeg.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Photo upload failed (${resp.code})."))
                }
            }
            Result.success("$base/storage/v1/object/public/$AVATAR_BUCKET/$objectPath")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Photo upload failed"))
        }
    }

    /**
     * Download a shared file into the app cache (then MediaExtractor.saveToSharedStorage moves it
     * into Music/ or Audiobooks/). @param onProgress 0..100
     */
    suspend fun downloadToCache(url: String, fileName: String, onProgress: (Float) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "shared_downloads").apply { mkdirs() }
                val safe = fileName.replace(Regex("[^A-Za-z0-9._ ()-]"), "_").ifBlank { "shared_audio" }
                val out = File(dir, safe)
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext Result.failure(Exception("Download failed (${resp.code}). The file may have been removed."))
                    val body = resp.body ?: return@withContext Result.failure(Exception("Empty response"))
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; onProgress(pct.toFloat()) }
                                }
                            }
                        }
                    }
                }
                Result.success(out)
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Download failed"))
            }
        }

    // ── File access (direct path first, MediaStore content URI as fallback for scoped storage) ──

    private fun fileSize(path: String): Long {
        val f = File(path)
        if (f.canRead()) return f.length()
        return mediaStoreUri(path)?.let { uri ->
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } ?: -1L
    }

    private fun openInput(path: String): InputStream {
        val f = File(path)
        if (f.canRead()) return f.inputStream()
        val uri = mediaStoreUri(path) ?: throw IllegalStateException("Can't open $path")
        return context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Can't open $path")
    }

    @Suppress("DEPRECATION")
    private fun mediaStoreUri(path: String): android.net.Uri? {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DATA} = ?", arrayOf(path), null,
        )?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(collection, c.getLong(0))
        }
        return null
    }

    companion object {
        /** Supabase free plan: 50 MB per file. */
        const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024
        const val AVATAR_BUCKET = "olive-avatars"

        fun mimeForPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "m4b", "aac", "mp4" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "webm" -> "audio/webm"
            else -> "application/octet-stream"
        }
    }
}
