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
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String,
    val body: String,
    val assets: List<GithubAsset>
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
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    return@withContext UpdateInfo(
                        version = remoteVersion,
                        releaseNotes = release.body,
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

    fun downloadApk(url: String): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))
        
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                emit(DownloadState.Error("Failed to download: HTTP ${response.code}"))
                return@flow
            }
            
            val body = response.body ?: run {
                emit(DownloadState.Error("Empty response body"))
                return@flow
            }
            
            val contentLength = body.contentLength()
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            
            val apkFile = File(updatesDir, "update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }
            
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)
            
            val buffer = ByteArray(8 * 1024)
            var bytesCopied = 0L
            var read: Int
            var lastProgress = 0f
            
            while (inputStream.read(buffer).also { read = it } >= 0) {
                outputStream.write(buffer, 0, read)
                bytesCopied += read
                
                if (contentLength > 0) {
                    val progress = bytesCopied.toFloat() / contentLength.toFloat()
                    if (progress - lastProgress >= 0.01f || progress == 1f) {
                        emit(DownloadState.Downloading(progress))
                        lastProgress = progress
                    }
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            emit(DownloadState.Success(apkFile))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            emit(DownloadState.Error(e.message ?: "Unknown error"))
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
