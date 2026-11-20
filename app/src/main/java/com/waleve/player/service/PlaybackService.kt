package com.waleve.player.service

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.app.PendingIntent
import android.content.Intent
import com.waleve.player.MainActivity

class PlaybackService : MediaSessionService() {

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface Deps {
        fun streamRepository(): com.waleve.player.domain.repository.StreamRepository
    }

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Online songs: links fetched on demand, refreshed silently, cached (see StreamEngine)
        val repo = dagger.hilt.android.EntryPointAccessors
            .fromApplication(applicationContext, Deps::class.java).streamRepository()
        val engine = StreamEngine(this, repo)

        // Live Listen Together: a copy of the decoded audio goes to LiveAudioTap (idle unless live)
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(com.waleve.player.service.live.LiveAudioTap.processor))
                    .build()
        }

        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(engine.dataSourceFactory)
                    .setLoadErrorHandlingPolicy(engine.loadErrorPolicy)
            )
            .setLoadControl(engine.loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

/** The User-Agent each YouTube client uses; googlevideo checks it against the link's "c=". */
object YouTubeHeaders {
    const val VR_UA = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private const val ANDROID_UA = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
    private const val MUSIC_UA = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14) gzip"
    private const val IOS_UA = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    private const val TV_UA = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
    private const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    fun forClient(c: String): Map<String, String> = when {
        c.equals("ANDROID_VR", true) -> mapOf("User-Agent" to VR_UA)
        c.equals("ANDROID_MUSIC", true) -> mapOf("User-Agent" to MUSIC_UA)
        c.startsWith("ANDROID", true) -> mapOf("User-Agent" to ANDROID_UA)
        c.startsWith("IOS", true) -> mapOf("User-Agent" to IOS_UA)
        c.startsWith("TV", true) -> mapOf("User-Agent" to TV_UA, "Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/")
        else -> mapOf("User-Agent" to WEB_UA, "Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/")
    }
}
