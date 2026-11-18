package com.waleve.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.waleve.player.MainActivity
import com.waleve.player.R
import com.waleve.player.domain.model.InboxNotification
import com.waleve.player.domain.model.InboxNotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows system-level notification bar alerts when a friend shares a song or playlist.
 *
 * This is triggered from the existing Firestore inbox real-time listener, so
 * notifications fire as soon as new items appear in the user's inbox collection.
 */
@Singleton
class ShareNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val CHANNEL_ID = "waleve_share_notifications"
        private const val CHANNEL_NAME = "Shared Music"
        private const val CHANNEL_DESCRIPTION = "Notifications when friends share songs or playlists"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Show a notification for a newly received shared song or playlist.
     */
    fun showShareNotification(notification: InboxNotification) {
        val (title, body, notifId) = when (notification.type) {
            InboxNotificationType.SONG_SHARED -> Triple(
                "🎵 ${notification.fromName} shared a song",
                notification.songTitle ?: "Tap to listen",
                "song_${notification.notifId}".hashCode(),
            )
            InboxNotificationType.PLAYLIST_SHARED -> Triple(
                "📋 ${notification.fromName} shared a playlist",
                notification.playlistName ?: "Tap to view",
                "playlist_${notification.notifId}".hashCode(),
            )
            else -> return // Don't show notifications for friend requests (they have their own tab)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "friends_inbox")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // User denied POST_NOTIFICATIONS — silent fallback
        }
    }
}
