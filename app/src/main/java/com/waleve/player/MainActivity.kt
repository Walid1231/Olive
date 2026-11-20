package com.waleve.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.waleve.player.presentation.WaLeveAppContent
import androidx.lifecycle.lifecycleScope
import com.waleve.player.domain.usecase.ScanMusicUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var scanMusicUseCase: ScanMusicUseCase

    @Inject
    lateinit var together: com.waleve.player.data.social.TogetherRepository

    @Inject
    lateinit var playbackController: com.waleve.player.service.PlaybackController

    @Inject
    lateinit var shareAutoSaver: com.waleve.player.data.social.ShareAutoSaver

    @Inject
    lateinit var ytMusic: com.waleve.player.data.remote.YouTubeMusicBrowser

    @Inject
    lateinit var mediaExtractorWarm: com.waleve.player.data.extractor.MediaExtractor

    @Inject
    lateinit var headsetBattery: com.waleve.player.service.HeadsetBatteryMonitor

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Nothing to do here: the Home / Library screens re-check access on resume
        // and start a scan (with a visible progress row) as soon as it is granted.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        enableEdgeToEdge()
        requestRequiredPermissions()
        together.start()
        shareAutoSaver.start()
        // Start yt-dlp (+ its daily update) now, so the first song doesn't wait for it
        lifecycleScope.launch { mediaExtractorWarm.warmUp() }
        // Warm up Search → Discover so its shelves are already there when you open it
        lifecycleScope.launch {
            val cached = ytMusic.cachedHome()
            if (cached == null || System.currentTimeMillis() - cached.fetchedAt > 30 * 60_000) runCatching { ytMusic.home() }
        }
        com.waleve.player.widget.OliveWidget.watch(this, playbackController)
        headsetBattery.start()

        setContent {
            WaLeveAppContent()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A link shared to Olive → open the Download page with it. */
    private fun handleShare(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            com.waleve.player.data.download.SharedLinkInbox.offer(intent.getStringExtra(android.content.Intent.EXTRA_TEXT))
        }
    }

    override fun onStart() {
        super.onStart()
        headsetBattery.setForeground(true)
    }

    override fun onStop() {
        headsetBattery.setForeground(false)
        super.onStop()
    }

    /**
     * Asks for music + notification access on the very first start only. Asking on every start
     * made Android block the music permission after two "Don't allow"s; later the Library / Home
     * card asks again (or opens Settings when Android won't).
     */
    private fun requestRequiredPermissions() {
        val prefs = getSharedPreferences("olive_permissions", MODE_PRIVATE)
        if (prefs.getBoolean("startup_asked", false)) return
        prefs.edit().putBoolean("startup_asked", true).apply()
        val permissions = mutableListOf<String>()

        // Audio read permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                @Suppress("DEPRECATION")
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            if (com.waleve.player.presentation.components.audioPermission in permissions) {
                com.waleve.player.presentation.components.markAudioPermissionAsked(this)
            }
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
