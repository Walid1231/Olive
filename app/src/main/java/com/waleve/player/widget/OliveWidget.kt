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
    interface Deps {
        fun playbackController(): PlaybackController
        fun localMusicRepository(): com.waleve.player.domain.repository.LocalMusicRepository
        fun headsetBattery(): com.waleve.player.service.HeadsetBatteryMonitor
    }

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
        if (action !in setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_FAV)) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val player = deps.playbackController()
        if (action == ACTION_FAV) {
            val song = player.playerState.value.currentSong ?: return
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    deps.localMusicRepository().toggleFavorite(song.id)
                    kotlinx.coroutines.withContext(Dispatchers.Main) { player.updateSongFavoriteStatus(song.id, !song.isFavorite) }
                } finally { pending.finish() }
            }
            return
        }
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
        const val ACTION_FAV = "com.waleve.player.widget.FAV"
    }
}

object OliveWidget {
    private data class State(
        val songId: Long?, val title: String?, val artist: String?, val art: String?,
        val playing: Boolean, val audiobook: Boolean, val favorite: Boolean, val canFavorite: Boolean,
        val videoId: String?,
    )

    @Volatile private var last = State(null, null, null, null, false, false, false, false, null)
    @Volatile private var lastPos = 0L
    @Volatile private var lastDur = 0L
    @Volatile private var lastArtKey: String? = null
    @Volatile private var lastArt: Bitmap? = null
    @Volatile private var lastThumb: Bitmap? = null
    @Volatile private var buds: com.waleve.player.service.HeadsetBattery? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watching = false

    /** Keep every widget in sync with the player. Safe to call many times. */
    @OptIn(FlowPreview::class)
    fun watch(context: Context, player: PlaybackController) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        // Song / play / favourite changes → full redraw
        scope.launch {
            player.playerState
                .map {
                    val s = it.currentSong
                    State(
                        s?.id, s?.title, s?.artist, s?.albumArtUri, it.isPlaying, s?.isAudiobook == true,
                        s?.isFavorite == true, s != null && (s.source == null || s.source == "local"), s?.videoId,
                    )
                }
                .distinctUntilChanged()
                .debounce(200)
                .collect { st ->
                    last = st
                    val ps = player.playerState.value
                    lastPos = ps.currentPosition; lastDur = ps.duration
                    push(app, st)
                }
        }
        // Earbuds battery → small pill above the time
        scope.launch {
            val monitor = EntryPointAccessors.fromApplication(app, OliveWidgetProvider.Deps::class.java).headsetBattery()
            monitor.start()
            monitor.state.debounce(500).collect { b ->
                buds = b
                pushBuds(app)
            }
        }
        // Progress bar + seek jumps (time text runs by itself)
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(4_000)
                if (ids(app).isEmpty()) continue
                val ps = player.playerState.value
                val expected = lastPos + if (last.playing) 4_000 else 0
                val jumped = kotlin.math.abs(ps.currentPosition - expected) > 2_500 || ps.duration != lastDur
                lastPos = ps.currentPosition; lastDur = ps.duration
                if (jumped) push(app, last) else if (last.playing) pushProgress(app)
            }
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { push(app, last) }
    }

    private fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, OliveWidgetProvider::class.java))

    private fun progressValue(): Int = if (lastDur > 0) ((lastPos.toFloat() / lastDur) * 1000).toInt().coerceIn(0, 1000) else 0

    private fun pushProgress(context: Context) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val v = RemoteViews(context.packageName, R.layout.widget_player)
        v.setProgressBar(R.id.widget_progress, 1000, progressValue(), false)
        runCatching { AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(ids, v) }
    }

    private fun budsText(): String? = buds?.takeIf { it.hasAny }?.shortText()

    private fun applyBuds(v: RemoteViews) {
        val text = budsText()
        v.setViewVisibility(R.id.widget_buds, if (text != null) android.view.View.VISIBLE else android.view.View.GONE)
        if (text != null) v.setTextViewText(R.id.widget_buds_text, text)
    }

    private fun pushBuds(context: Context) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val v = RemoteViews(context.packageName, R.layout.widget_player)
        applyBuds(v)
        runCatching { AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(ids, v) }
    }

    private suspend fun push(context: Context, s: State) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_player)

        // Text
        if (s.title == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_idle))
            views.setTextViewText(R.id.widget_artist, context.getString(R.string.widget_artist_idle))
        } else {
            views.setTextViewText(R.id.widget_title, s.title)
            val who = s.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
            views.setTextViewText(R.id.widget_artist, if (s.audiobook) "Audiobook · $who" else who)
        }
        views.setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        views.setImageViewResource(R.id.widget_fav, if (s.favorite) R.drawable.ic_w_heart_fill else R.drawable.ic_w_heart)
        views.setViewVisibility(R.id.widget_fav, if (s.canFavorite) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widget_share, if (s.title != null) android.view.View.VISIBLE else android.view.View.GONE)

        // Time: chronometers tick on their own; we only set where they start
        val now = android.os.SystemClock.elapsedRealtime()
        val hasTime = s.title != null && lastDur > 0
        views.setViewVisibility(R.id.widget_elapsed, if (hasTime) android.view.View.VISIBLE else android.view.View.INVISIBLE)
        views.setViewVisibility(R.id.widget_remaining, if (hasTime) android.view.View.VISIBLE else android.view.View.INVISIBLE)
        if (hasTime) {
            views.setChronometer(R.id.widget_elapsed, now - lastPos, null, s.playing)
            views.setChronometerCountDown(R.id.widget_remaining, true)
            views.setChronometer(R.id.widget_remaining, now + (lastDur - lastPos).coerceAtLeast(0), "-%s", s.playing)
        }
        views.setProgressBar(R.id.widget_progress, 1000, progressValue(), false)
        applyBuds(views)

        // Cover (big, rounded) + small round thumb in the pill
        val (art, thumb) = loadArt(context, s.art)
        if (art != null) views.setImageViewBitmap(R.id.widget_art, art) else views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
        if (thumb != null) views.setImageViewBitmap(R.id.widget_thumb, thumb) else views.setImageViewResource(R.id.widget_thumb, R.drawable.widget_art_placeholder)

        // Taps
        fun broadcast(action: String, code: Int) = PendingIntent.getBroadcast(
            context, code, Intent(context, OliveWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_play, broadcast(OliveWidgetProvider.ACTION_PLAY_PAUSE, 21))
        views.setOnClickPendingIntent(R.id.widget_next, broadcast(OliveWidgetProvider.ACTION_NEXT, 22))
        views.setOnClickPendingIntent(R.id.widget_prev, broadcast(OliveWidgetProvider.ACTION_PREV, 23))
        views.setOnClickPendingIntent(R.id.widget_fav, broadcast(OliveWidgetProvider.ACTION_FAV, 25))
        val open = PendingIntent.getActivity(
            context, 24, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_open, open)
        views.setOnClickPendingIntent(R.id.widget_pill, open)
        if (s.title != null) {
            val text = buildString {
                append("🎵 ${s.title}")
                s.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.let { append(" — $it") }
                s.videoId?.let { append("\nhttps://youtu.be/$it") }
                append("\nListening on Olive")
            }
            val share = Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share song",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.widget_share,
                PendingIntent.getActivity(context, 26, share, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) }
    }

    private suspend fun loadArt(context: Context, uri: String?): Pair<Bitmap?, Bitmap?> {
        if (uri.isNullOrBlank()) return null to null
        if (uri == lastArtKey) return lastArt to lastThumb
        val src = runCatching {
            val req = ImageRequest.Builder(context).data(uri).size(600).allowHardware(false).build()
            (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap(600, 600)
        }.getOrNull()
        val art = src?.let { rounded(it, 72f) }
        val thumb = src?.let { rounded(Bitmap.createScaledBitmap(it, 96, 96, true), 48f) }
        lastArtKey = uri; lastArt = art; lastThumb = thumb
        return art to thumb
    }

    private fun rounded(src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
        return out
    }
}
