package com.waleve.player.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.waleve.player.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList()
)

@Serializable
data class GithubAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileSize: Long
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val REPO_OWNER = "Walid1231"
        private const val REPO_NAME = "Olive"
        private const val API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    }

    /** The newer release waiting to be installed (shared by every screen). */
    val available = kotlinx.coroutines.flow.MutableStateFlow<UpdateInfo?>(null)

    /** A downloaded APK waiting for the "install unknown apps" permission. */
    var pendingInstall: File? = null

    fun canInstall(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens Settings → "Install unknown apps" for Olive (one-time switch). */
    fun openInstallPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Already-downloaded APK for [info], if complete. */
    fun downloadedFor(info: UpdateInfo): File? {
        val f = File(File(context.cacheDir, "updates"), "olive-${info.version}.apk")
        return f.takeIf { it.exists() && (info.fileSize <= 0 || it.length() == info.fileSize) }
    }

    suspend fun checkForUpdate(): UpdateInfo? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to check for updates: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val release = json.decodeFromString<GithubRelease>(body)
            
            // Clean versions for comparison (e.g., "v1.0.1" -> "1.0.1")
            val remoteVersion = release.tagName.removePrefix("v")
            val localVersion = BuildConfig.VERSION_NAME.removePrefix("v")

            // Only show update if remote version is strictly newer
            if (isNewerVersion(remoteVersion, localVersion)) {
                // Find the APK asset
                // 64-bit phones get Olive.apk, old 32-bit phones Olive_32bit.apk
                val is64 = android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
                val apks = release.assets.filter { it.name.endsWith(".apk") }
                val apkAsset = (if (is64) apks.firstOrNull { it.name == "Olive.apk" } else apks.firstOrNull { it.name == "Olive_32bit.apk" })
                    ?: apks.firstOrNull()
                if (apkAsset != null) {
                    return@withContext UpdateInfo(
                        version = remoteVersion,
                        releaseNotes = release.body ?: "",
                        downloadUrl = apkAsset.browserDownloadUrl,
                        fileSize = apkAsset.size
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            null
        }
    }

    /**
     * Semantic version comparison: returns true if [remote] is strictly newer than [local].
     * Supports x.y.z format (e.g., "1.0.1" > "1.0.0").
     */
    fun isNewerVersion(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Version comparison failed: remote=$remote local=$local", e)
        }
        return false // equal or unparseable → no update
    }

    /**
     * Downloads the update with 4 parallel connections (each grabs one part of the file).
     * On slow links GitHub limits each connection, so 4 at once is usually 2–4× faster.
     * Falls back to a single connection if the server doesn't support parts.
     */
    fun downloadApk(url: String, version: String = "update"): Flow<DownloadState> = kotlinx.coroutines.flow.channelFlow {
        send(DownloadState.Downloading(0f))
        val client = okHttpClient.newBuilder()
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { it.delete() }   // keep only this version
            val apkFile = File(updatesDir, "olive-$version.apk")

            // Probe: follow the redirect once, learn the size and whether parts are allowed
            val probe = client.newCall(Request.Builder().url(url).header("Range", "bytes=0-0").build()).execute()
            val finalUrl = probe.request.url.toString()
            val total = probe.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
            val ranged = probe.code == 206 && total > 0
            probe.close()

            val done = java.util.concurrent.atomic.AtomicLong(0)
            var lastSent = 0f
            suspend fun report() {
                if (total <= 0) return
                val p = done.get().toFloat() / total
                if (p - lastSent >= 0.01f) { lastSent = p; send(DownloadState.Downloading(p.coerceAtMost(0.999f))) }
            }

            if (!ranged) {
                // Single connection
                client.newCall(Request.Builder().url(finalUrl).build()).execute().use { resp ->
                    if (!resp.isSuccessful) { send(DownloadState.Error("Failed to download: HTTP ${resp.code}")); return@channelFlow }
                    val body = resp.body ?: run { send(DownloadState.Error("Empty response")); return@channelFlow }
                    val len = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done.addAndGet(n.toLong())
                                if (len > 0) { val p = done.get().toFloat() / len; if (p - lastSent >= 0.01f) { lastSent = p; send(DownloadState.Downloading(p)) } }
                            }
                        }
                    }
                }
            } else {
                java.io.RandomAccessFile(apkFile, "rw").use { it.setLength(total) }
                val parts = 4
                val chunk = total / parts
                kotlinx.coroutines.coroutineScope {
                    (0 until parts).map { i ->
                        val from = i * chunk
                        val to = if (i == parts - 1) total - 1 else from + chunk - 1
                        launch(Dispatchers.IO) {
                            var pos = from
                            var attempts = 0
                            while (pos <= to) {
                                try {
                                    client.newCall(Request.Builder().url(finalUrl).header("Range", "bytes=$pos-$to").build()).execute().use { resp ->
                                        if (resp.code != 206) throw java.io.IOException("HTTP ${resp.code}")
                                        java.io.RandomAccessFile(apkFile, "rw").use { raf ->
                                            raf.seek(pos)
                                            resp.body!!.byteStream().use { input ->
                                                val buf = ByteArray(64 * 1024)
                                                while (true) {
                                                    val n = input.read(buf); if (n < 0) break
                                                    raf.write(buf, 0, n); pos += n; done.addAndGet(n.toLong())
                                                    report()
                                                }
                                            }
                                        }
                                    }
                                } catch (e: java.io.IOException) {
                                    // Resume this part where it stopped (up to 5 tries)
                                    if (++attempts > 5) throw e
                                    kotlinx.coroutines.delay(1_500L * attempts)
                                }
                            }
                        }
                    }.joinAll()
                }
                if (apkFile.length() != total || done.get() < total) {
                    send(DownloadState.Error("Download incomplete — tap Try again")); return@channelFlow
                }
            }
            send(DownloadState.Success(apkFile))
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            send(DownloadState.Error(e.message ?: "Download failed — check your internet"))
        }
    }.flowOn(Dispatchers.IO)
    
    fun installApk(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                apkFile
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
        }
    }
}
