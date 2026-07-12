package com.v2ray.ang.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import java.util.Collections
import java.util.WeakHashMap

object ThemeRecreateManager {

    private val activities: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())

    private val WATCHED_KEYS: Set<String> = setOf(
        AppConfig.PREF_APP_THEME,
        AppConfig.PREF_DYNAMIC_COLOR,
        AppConfig.PREF_DYNAMIC_COLOR_BANNER,
        AppConfig.PREF_TRUE_BLACK,
        AppConfig.PREF_USE_CUSTOM_COLOR,
        AppConfig.PREF_CUSTOM_COLOR,
        AppConfig.PREF_BANNER_COLOR,
        AppConfig.PREF_CUSTOM_DPI,
        AppConfig.PREF_SHOW_HOME_BANNER,
        AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
        AppConfig.PREF_HOME_BANNER_HEIGHT,
        AppConfig.PREF_BLUR_BOTTOM_STATUS,
        AppConfig.PREF_BLUR_BOTTOM_RADIUS,
        AppConfig.PREF_BLUR_BOTTOM_ROUNDS,
        AppConfig.PREF_APP_FONT,
        AppConfig.PREF_APP_FONT_USE_CUSTOM,
        AppConfig.PREF_APP_FONT_CUSTOM_NAME,
        AppConfig.PREF_HEADER_TOP_ROW_PADDING,
    )

    private val lastKnownValues = HashMap<String, Any?>()
    private var initialized = false

    fun track(activity: Activity) {
        activities.add(activity)
    }

    fun untrack(activity: Activity) {
        activities.remove(activity)
    }

    @Synchronized
    fun init() {
        if (initialized) return
        initialized = true
        WATCHED_KEYS.forEach { key -> lastKnownValues[key] = currentValueOf(key) }
    }

    private fun currentValueOf(key: String): Any? = when (key) {
        AppConfig.PREF_APP_THEME,
        AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
        AppConfig.PREF_APP_FONT,
        AppConfig.PREF_APP_FONT_CUSTOM_NAME ->
            MmkvManager.decodeSettingsString(key) ?: ""

        AppConfig.PREF_CUSTOM_COLOR,
        AppConfig.PREF_BANNER_COLOR,
        AppConfig.PREF_CUSTOM_DPI,
        AppConfig.PREF_HOME_BANNER_HEIGHT,
        AppConfig.PREF_BLUR_BOTTOM_RADIUS,
        AppConfig.PREF_BLUR_BOTTOM_ROUNDS,
        AppConfig.PREF_HEADER_TOP_ROW_PADDING ->
            MmkvManager.decodeSettingsInt(key, 0)

        else -> MmkvManager.decodeSettingsBool(key, false)
    }

    @Synchronized
    fun notifyKeyWritten(key: String, newValue: Any?) {
        if (!initialized || key !in WATCHED_KEYS) return
        val old = lastKnownValues[key]
        lastKnownValues[key] = newValue
        if (old != newValue) {
            recreateAll()
        }
    }

    fun recreateAll() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { recreateAll() }
            return
        }
        val snapshot = activities.toList()
        snapshot.forEach { activity ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.recreate()
            }
        }
    }
}
