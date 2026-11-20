package com.waleve.player.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.waleve.player.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.security.MessageDigest
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
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    /** "sha256:…", which GitHub lists for every release file. */
    val digest: String? = null,
)

data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileSize: Long,
    /** Expected SHA-256 of the APK (hex), when GitHub gave one. */
    val sha256: String? = null,
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

    private fun apkFileFor(version: String) = File(File(context.cacheDir, "updates"), "olive-$version.apk")

    /**
     * A finished download for [info] that really is this update, or null. Half-finished or
     * damaged files are deleted here — installing one is what made Android say "App not installed".
     */
    fun downloadedFor(info: UpdateInfo): File? {
        val f = apkFileFor(info.version)
        if (!f.exists()) return null
        val ok = (info.fileSize <= 0 || f.length() == info.fileSize) &&
            archiveInfo(f, 0)?.let { it.packageName == context.packageName && it.versionName == info.version } == true
        if (!ok) f.delete()
        return f.takeIf { ok }
    }

    // ─── Download that keeps going if the dialog closes or the screen rotates ───

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private val _download = MutableStateFlow<Pair<String, DownloadState>?>(null)
    /** The running / last download as (version, state). */
    val download: StateFlow<Pair<String, DownloadState>?> = _download.asStateFlow()

    fun startDownload(info: UpdateInfo) {
        if (downloadJob?.isActive == true && _download.value?.first == info.version) return
        downloadJob?.cancel()
        _download.value = info.version to DownloadState.Downloading(0f)
        downloadJob = scope.launch {
            downloadApk(info.downloadUrl, info.version, info.fileSize, info.sha256).collect { _download.value = info.version to it }
        }
    }

    /** Forget a finished/failed download (e.g. its file turned out to be damaged). */
    fun clearDownload() {
        if (downloadJob?.isActive != true) _download.value = null
    }

    // ─── Checks before handing the file to Android's installer ───

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File, flags: Int): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun installedInfo(flags: Int): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else context.packageManager.getPackageInfo(context.packageName, flags)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else info.signatures
        return sigs.orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return hex(md.digest())
    }

    /** Space Android can give us right now (counts caches it would clear). */
    private fun allocatableBytes(): Long = runCatching {
        val sm = context.getSystemService(android.os.storage.StorageManager::class.java)
        sm.getAllocatableBytes(android.os.storage.StorageManager.UUID_DEFAULT)
    }.getOrElse { context.filesDir.usableSpace }

    /** Drop Olive's own throw-away files: copies of played songs, leftovers of old downloads. */
    private fun freeOwnCache() {
        File(context.cacheDir, "play_cache").deleteRecursively()
        val old = System.currentTimeMillis() - 10 * 60_000L
        File(context.cacheDir, "yt_dlp_downloads").listFiles()?.filter { it.lastModified() < old }?.forEach { it.deleteRecursively() }
    }

    /** Problem with a freshly downloaded file, or null when it is complete and really an Olive APK. */
    private fun verifyDownloaded(file: File, size: Long, sha256: String?): String? {
        if (size > 0 && file.length() != size) return "The download didn't finish. Tap Try again."
        if (!sha256.isNullOrBlank() && !sha256Of(file).equals(sha256, ignoreCase = true)) return "The download got damaged on the way. Tap Try again."
        val info = archiveInfo(file, 0) ?: return "The download got damaged on the way. Tap Try again."
        if (info.packageName != context.packageName) return "This file isn't an Olive update."
        return null
    }

    /**
     * Makes sure Android will accept [apkFile] before opening its installer, so problems get a
     * clear message instead of "App not installed". Null = go ahead.
     */
    suspend fun prepareInstall(apkFile: File): String? = kotlinx.coroutines.withContext(Dispatchers.IO) { checkInstallable(apkFile) }

    private fun checkInstallable(apkFile: File): String? {
        if (!apkFile.exists()) return "The update file is gone. Tap Download again."
        @Suppress("DEPRECATION")
        val certs = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val update = archiveInfo(apkFile, certs)
        if (update == null || update.packageName != context.packageName) {
            apkFile.delete()
            return "The update file was damaged, so it was deleted. Tap Download again."
        }
        val installed = installedInfo(certs)
        if (installed != null) {
            if (PackageInfoCompat.getLongVersionCode(update) < PackageInfoCompat.getLongVersionCode(installed)) {
                return "This file is an older Olive than the one you have."
            }
            val a = signers(update)
            val b = signers(installed)
            if (a.isNotEmpty() && b.isNotEmpty() && a.intersect(b).isEmpty()) {
                return "The Olive on this phone came from a different build, so Android won't update it. Uninstall Olive once, then install this file."
            }
        }
        // Installing unpacks the app: needs about 2.5× the file size free
        val need = (apkFile.length() * 2.5).toLong()
        if (allocatableBytes() < need) freeOwnCache()
        val free = allocatableBytes()
        if (free < need) {
            val mb = (need - free) / (1024 * 1024) + 1
            return "Not enough free storage to install. Free up about $mb MB, then tap Install now."
        }
        return null
    }

    /** True when the last check couldn't reach GitHub at all (so "no update" isn't known). */
    @Volatile var lastCheckFailed = false
        private set

    private val is64 get() = android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

    /**
     * Backup check that doesn't use GitHub's API (which allows only 60 checks an hour per
     * internet address — mobile networks share addresses, so it's often used up):
     * github.com/…/releases/latest redirects to the newest tag; notes come from the repo file.
     */
    private fun checkViaWebsite(): UpdateInfo? {
        val repo = API_URL.substringAfter("repos/").substringBefore("/releases")
        val tag = okHttpClient.newCall(Request.Builder().url("https://github.com/$repo/releases/latest").build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            r.request.url.toString().substringAfter("/releases/tag/", "").ifBlank { throw java.io.IOException("no tag") }
        }
        val remote = tag.removePrefix("v")
        if (!isNewerVersion(remote, BuildConfig.VERSION_NAME.removePrefix("v"))) return null
        val apkUrl = "https://github.com/$repo/releases/download/$tag/${if (is64) "Olive.apk" else "Olive_32bit.apk"}"
        val size = runCatching {
            okHttpClient.newCall(Request.Builder().url(apkUrl).header("Range", "bytes=0-0").build()).execute().use {
                it.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
        val notes = runCatching {
            // main always holds the latest release's short notes
            okHttpClient.newCall(Request.Builder().url("https://raw.githubusercontent.com/$repo/main/RELEASE_NOTES.md").build()).execute().use {
                if (it.isSuccessful) it.body?.string().orEmpty() else ""
            }
        }.getOrDefault("")
        return UpdateInfo(version = remote, releaseNotes = notes, downloadUrl = apkUrl, fileSize = size)
    }

    suspend fun checkForUpdate(): UpdateInfo? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        lastCheckFailed = false
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Update API refused (${response.code}) — using the website check")
                response.close()
                return@withContext runCatching { checkViaWebsite() }.getOrElse { lastCheckFailed = true; null }
            }

            val body = response.body?.string() ?: return@withContext runCatching { checkViaWebsite() }.getOrElse { lastCheckFailed = true; null }
            val release = json.decodeFromString<GithubRelease>(body)
            
            // Clean versions for comparison (e.g., "v1.0.1" -> "1.0.1")
            val remoteVersion = release.tagName.removePrefix("v")
            val localVersion = BuildConfig.VERSION_NAME.removePrefix("v")

            // Only show update if remote version is strictly newer
            if (isNewerVersion(remoteVersion, localVersion)) {
                // Find the APK asset
                // 64-bit phones get Olive.apk, old 32-bit phones Olive_32bit.apk
                val apks = release.assets.filter { it.name.endsWith(".apk") }
                val apkAsset = (if (is64) apks.firstOrNull { it.name == "Olive.apk" } else apks.firstOrNull { it.name == "Olive_32bit.apk" })
                    ?: apks.firstOrNull()
                if (apkAsset != null) {
                    return@withContext UpdateInfo(
                        version = remoteVersion,
                        releaseNotes = release.body ?: "",
                        downloadUrl = apkAsset.browserDownloadUrl,
                        fileSize = apkAsset.size,
                        sha256 = apkAsset.digest?.substringAfter("sha256:", "")?.takeIf { it.length == 64 },
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates — using the website check", e)
            runCatching { checkViaWebsite() }.getOrElse { lastCheckFailed = true; null }
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
    fun downloadApk(
        url: String,
        version: String = "update",
        expectedSize: Long = 0,
        sha256: String? = null,
    ): Flow<DownloadState> = kotlinx.coroutines.flow.channelFlow {
        send(DownloadState.Downloading(0f))
        val client = okHttpClient.newBuilder()
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "olive-$version.apk")
        // Written here first and renamed only after it checks out, so a cut-off download
        // can never be offered for installing later.
        val partFile = File(updatesDir, "olive-$version.apk.part")
        try {
            updatesDir.listFiles()?.forEach { it.delete() }   // old versions and half-finished files

            // Probe: learn the size and whether parts are allowed. Every request below goes to the
            // GitHub link again (not the redirect it gives), because that redirect expires in minutes.
            val probe = client.newCall(Request.Builder().url(url).header("Range", "bytes=0-0").build()).execute()
            val total = probe.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
            val ranged = probe.code == 206 && total > 0
            probe.close()
            val finalUrl = url

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
                        FileOutputStream(partFile).use { out ->
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
                java.io.RandomAccessFile(partFile, "rw").use { it.setLength(total) }
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
                                        java.io.RandomAccessFile(partFile, "rw").use { raf ->
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
                if (done.get() < total) {
                    partFile.delete()
                    send(DownloadState.Error("The download didn't finish. Tap Try again.")); return@channelFlow
                }
            }
            // Check it before it can be installed
            val problem = verifyDownloaded(partFile, if (total > 0) total else expectedSize, sha256)
            if (problem != null) {
                partFile.delete()
                send(DownloadState.Error(problem)); return@channelFlow
            }
            if (!partFile.renameTo(apkFile)) { partFile.copyTo(apkFile, overwrite = true); partFile.delete() }
            send(DownloadState.Success(apkFile))
        } catch (e: kotlinx.coroutines.CancellationException) {
            partFile.delete()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            partFile.delete()
            send(DownloadState.Error("Download failed: ${e.message ?: "check your internet"}. Tap Try again."))
        }
    }.flowOn(Dispatchers.IO)
    
    fun installApk(apkFile: File): Boolean {
        return try {
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            false
        }
    }
}
