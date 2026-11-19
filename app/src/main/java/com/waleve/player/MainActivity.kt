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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Nothing to do here: the Home / Library screens re-check access on resume
        // and start a scan (with a visible progress row) as soon as it is granted.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRequiredPermissions()
        together.start()
        shareAutoSaver.start()
        // Warm up Search → Discover so its shelves are already there when you open it
        lifecycleScope.launch {
            val cached = ytMusic.cachedHome()
            if (cached == null || System.currentTimeMillis() - cached.fetchedAt > 30 * 60_000) runCatching { ytMusic.home() }
        }
        com.waleve.player.widget.OliveWidget.watch(this, playbackController)

        setContent {
            WaLeveAppContent()
        }
    }

    private fun requestRequiredPermissions() {
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
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
