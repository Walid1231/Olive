package com.waleve.player.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast

object WidgetPin {
    fun isAdded(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, OliveWidgetProvider::class.java)).isNotEmpty()

    /** Asks the launcher to place the Olive widget (Android 8+). Falls back to instructions. */
    fun request(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ok = runCatching {
            mgr.isRequestPinAppWidgetSupported &&
                mgr.requestPinAppWidget(ComponentName(context, OliveWidgetProvider::class.java), null, null)
        }.getOrDefault(false)
        if (!ok) Toast.makeText(context, "Long-press your home screen → Widgets → Olive", Toast.LENGTH_LONG).show()
    }
}
