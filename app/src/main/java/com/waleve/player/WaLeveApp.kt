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
        // Before Hilt injects anything that uses Firebase
        ensureFirebase()
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

    /**
     * Firebase normally starts itself before the app; some Xiaomi / MIUI phones skip that and the
     * app crashed with "Default FirebaseApp is not initialized". Start it ourselves if needed —
     * from resources, or from the settings built into the app.
     */
    private fun ensureFirebase() {
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) return
            if (com.google.firebase.FirebaseApp.initializeApp(this) != null) return
            if (BuildConfig.FIREBASE_APP_ID.isBlank()) return
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .apply { if (BuildConfig.FIREBASE_BUCKET.isNotBlank()) setStorageBucket(BuildConfig.FIREBASE_BUCKET) }
                .build()
            com.google.firebase.FirebaseApp.initializeApp(this, options)
            android.util.Log.w("WaLeveApp", "Firebase started manually (automatic start-up was skipped)")
        } catch (e: Exception) {
            android.util.Log.e("WaLeveApp", "Firebase start-up failed", e)
        }
    }
}
