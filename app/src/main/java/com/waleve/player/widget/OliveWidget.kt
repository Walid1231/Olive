package com.waleve.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.waleve.player.MainActivity
import com.waleve.player.R
import com.waleve.player.service.PlaybackController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Home-screen widget: cover, title, artist and previous / play-pause / next. */
class OliveWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun playbackController(): PlaybackController }

    private fun start(context: Context) {
        val player = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).playbackController()
        OliveWidget.watch(context, player)
        OliveWidget.refresh(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = start(context)

    override fun onEnabled(context: Context) = start(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV)) return
        val player = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).playbackController()
        if (player.playerState.value.currentSong == null) {
            // Nothing loaded (e.g. app was closed) → open Olive instead
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        when (action) {
            ACTION_PLAY_PAUSE -> player.playPause()
            ACTION_NEXT -> player.next()
            ACTION_PREV -> player.previous()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.waleve.player.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.waleve.player.widget.NEXT"
        const val ACTION_PREV = "com.waleve.player.widget.PREV"
    }
}

object OliveWidget {
    private data class State(val title: String?, val artist: String?, val art: String?, val playing: Boolean, val audiobook: Boolean)

    @Volatile private var last = State(null, null, null, false, false)
    @Volatile private var lastArtKey: String? = null
    @Volatile private var lastArt: Bitmap? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watching = false

    /** Keep every widget in sync with the player. Call once at app start. */
    @OptIn(FlowPreview::class)
    fun watch(context: Context, player: PlaybackController) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        scope.launch {
            player.playerState
                .map { State(it.currentSong?.title, it.currentSong?.artist, it.currentSong?.albumArtUri, it.isPlaying, it.currentSong?.isAudiobook == true) }
                .distinctUntilChanged()
                .debounce(250)
                .collect { s -> last = s; push(app, s) }
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { push(app, last) }
    }

    private fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, OliveWidgetProvider::class.java))

    private suspend fun push(context: Context, s: State) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_player)

        if (s.title == null) {
            views.setTextViewText(R.id.widget_label, "Olive")
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_idle))
            views.setTextViewText(R.id.widget_artist, context.getString(R.string.widget_artist_idle))
        } else {
            views.setTextViewText(R.id.widget_label, when { s.audiobook -> "Audiobook"; s.playing -> "Now playing"; else -> "Paused" })
            views.setTextViewText(R.id.widget_title, s.title)
            views.setTextViewText(R.id.widget_artist, s.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist")
        }
        views.setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)

        val art = loadArt(context, s.art)
        if (art != null) views.setImageViewBitmap(R.id.widget_art, art)
        else views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)

        fun broadcast(action: String, code: Int) = PendingIntent.getBroadcast(
            context, code, Intent(context, OliveWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_play, broadcast(OliveWidgetProvider.ACTION_PLAY_PAUSE, 21))
        views.setOnClickPendingIntent(R.id.widget_next, broadcast(OliveWidgetProvider.ACTION_NEXT, 22))
        views.setOnClickPendingIntent(R.id.widget_prev, broadcast(OliveWidgetProvider.ACTION_PREV, 23))
        val open = PendingIntent.getActivity(
            context, 24, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        views.setOnClickPendingIntent(R.id.widget_art, open)

        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) }
    }

    private suspend fun loadArt(context: Context, uri: String?): Bitmap? {
        if (uri.isNullOrBlank()) return null
        if (uri == lastArtKey) return lastArt
        val bmp = runCatching {
            val req = ImageRequest.Builder(context).data(uri).size(256).allowHardware(false).build()
            (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap(256, 256)?.let { rounded(it, 40f) }
        }.getOrNull()
        lastArtKey = uri
        lastArt = bmp
        return bmp
    }

    private fun rounded(src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
        return out
    }
}
