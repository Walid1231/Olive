package com.waleve.player.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Handles the "Cancel" and "+5 min" buttons on the sleep-timer notification. */
class SleepTimerReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun playbackController(): PlaybackController }

    override fun onReceive(context: Context, intent: Intent) {
        val controller = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).playbackController()
        when (intent.action) {
            ACTION_CANCEL -> controller.cancelSleepTimer()
            ACTION_EXTEND -> controller.extendSleepTimer(5)
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.waleve.player.SLEEP_CANCEL"
        const val ACTION_EXTEND = "com.waleve.player.SLEEP_EXTEND"
    }
}
