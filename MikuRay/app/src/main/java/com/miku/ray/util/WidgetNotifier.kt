package com.miku.ray.util

import android.content.Context
import android.content.Intent
import com.miku.ray.AppConfig

/**
 * AppWidgetProvider instances can't keep a live AIDL binding (they're only woken up briefly per
 * broadcast), so instead of the old generic BROADCAST_ACTION_ACTIVITY bus, we send one small,
 * explicit, typed broadcast straight at the widget receivers whenever something they care about
 * changes (connection state or traffic). Both WidgetProvider and TrafficDetailWidgetProvider
 * listen for AppConfig.ACTION_WIDGET_STATE_CHANGED.
 */
object WidgetNotifier {

    fun refresh(context: Context, running: Boolean) {
        val app = context.applicationContext
        val intent = Intent(AppConfig.ACTION_WIDGET_STATE_CHANGED)
        .setPackage(app.packageName)
        .putExtra(AppConfig.EXTRA_RUNNING, running)
        app.sendBroadcast(intent)
    }
}
