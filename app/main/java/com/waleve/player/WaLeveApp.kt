package com.waleve.player

import android.app.Application
import com.waleve.player.data.extractor.MediaExtractor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WaLeveApp : Application() {

    @Inject lateinit var mediaExtractor: MediaExtractor

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Pre-initialize yt-dlp + FFmpeg and update in the background at app startup.
        // This removes the cold-start delay when the user first taps Search or Download,
        // making stream extraction feel much faster.
        appScope.launch {
            try {
                mediaExtractor.initialize()
                mediaExtractor.ensureUpdated()
            } catch (e: Exception) {
                android.util.Log.w("WaLeveApp", "Background yt-dlp init/update failed (will retry on demand)", e)
            }
        }
    }
}
