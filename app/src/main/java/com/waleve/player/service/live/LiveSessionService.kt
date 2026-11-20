package com.waleve.player.service.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.waleve.player.MainActivity
import com.waleve.player.R

/**
 * Keeps a live Listen Together stream going with the screen off.
 * Host: mediaPlayback + microphone (Android needs the mic type to feed audio to WebRTC; the mic
 * sound itself is replaced by the music). Guest: mediaPlayback only.
 */
class LiveSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getBooleanExtra(EXTRA_HOST, false) == true
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Listen Together live", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_w_buds)
            .setContentTitle(if (host) "You're live" else "Listening live")
            .setContentText(if (host) "Friends hear what you play in Olive" else "Playing your friend's music")
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (host) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        runCatching { ServiceCompat.startForeground(this, NOTIF_ID, n, type) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "olive_live"
        private const val NOTIF_ID = 4711
        private const val EXTRA_HOST = "host"

        fun start(context: Context, host: Boolean) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, LiveSessionService::class.java).putExtra(EXTRA_HOST, host))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, LiveSessionService::class.java)) }
        }
    }
}
