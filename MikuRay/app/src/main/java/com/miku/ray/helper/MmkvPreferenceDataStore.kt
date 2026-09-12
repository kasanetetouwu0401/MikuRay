package com.miku.ray.helper

import androidx.preference.PreferenceDataStore
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil

class MmkvPreferenceDataStore(private val triggersServiceRestart: Boolean = true) : PreferenceDataStore() {

    override fun putString(key: String, value: String?) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getString(key: String, defaultValue: String?): String? {
        return MmkvManager.decodeSettingsString(key, defaultValue)
    }

    override fun putInt(key: String, value: Int) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return MmkvManager.decodeSettingsInt(key, defaultValue)
    }

    override fun putLong(key: String, value: Long) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return MmkvManager.decodeSettingsLong(key, defaultValue)
    }

    override fun putFloat(key: String, value: Float) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return MmkvManager.decodeSettingsFloat(key, defaultValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return MmkvManager.decodeSettingsBool(key, defaultValue)
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        if (values == null) {
            MmkvManager.encodeSettings(key, null as String?)
        } else {
            MmkvManager.encodeSettings(key, values)
        }
        notifySettingChanged(key)
    }

    override fun getStringSet(key: String, defaultValues: MutableSet<String>?): MutableSet<String>? {
        return MmkvManager.decodeSettingsStringSet(key) ?: defaultValues
    }

    private fun isLightUiKey(key: String): Boolean =
        key in LIGHT_UI_KEYS ||
            key.startsWith("pref_particles_") ||
            key.startsWith("pref_banner_character_") ||
            key.startsWith("pref_snowflakes_") ||
            key.startsWith("pref_weather_")

    private fun isDisplayRefreshKey(key: String): Boolean = key in DISPLAY_REFRESH_KEYS

    private fun notifySettingChanged(key: String) {
        if (key == AppConfig.PREF_LOGLEVEL) {
            LogUtil.refreshLogLevel()
        }

        if (key == AppConfig.PREF_UI_MODE_NIGHT) {
            SettingsManager.setNightMode()
        }

        // List / chip display — refresh lists, no VPN restart
        if (isDisplayRefreshKey(key)) {
            SettingsChangeManager.makeRefreshDisplayPrefs()
            return
        }

        // Pure UI chrome — SharedFlow light path
        if (isLightUiKey(key)) {
            SettingsChangeManager.makeLightUiRefresh()
            return
        }

        // Theme / font / DPI — activities recreate via heavyThemeVersion
        if (key in HEAVY_THEME_KEYS) {
            SettingsChangeManager.makeHeavyThemeRecreate()
            return
        }

        // Service-related
        if (triggersServiceRestart) {
            SettingsChangeManager.makeRestartService()
        }
        SettingsChangeManager.makeSetupGroupTab()
    }

    companion object {
        /** Keys that only need server-list / chip rebind. */
        private val DISPLAY_REFRESH_KEYS = setOf(
            AppConfig.PREF_TRAFFIC_ENABLED,
            AppConfig.PREF_DISABLE_SENSOR_TEXT,
            AppConfig.PREF_NETWORK_SECURITY_ENABLED,
            AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
            AppConfig.PREF_SHOW_ISP_INFO,
            AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP,
            AppConfig.PREF_COMPACT_LIST_ACTIONS,
            AppConfig.PREF_HIDE_SCROLL_BUTTONS,
            AppConfig.PREF_INDICATOR_STYLE,
            AppConfig.PREF_SEARCH_BAR_CHIP,
            AppConfig.PREF_SEARCH_CHIP_GRADIENT,
            AppConfig.PREF_SEARCH_BAR_CHIP_DUAL_SELECTION,
            AppConfig.PREF_TAB_BADGE_LIMIT,
            AppConfig.PREF_GROUP_ALL_DISPLAY,
            AppConfig.PREF_GROUP_ALL_TAB_ICON,
            AppConfig.PREF_SPEED_ENABLED,
        )

        /**
         * UI Settings keys that must NOT restart VPN / rebuild groups.
         * Heavy theme/font/DPI keys are intentionally excluded.
         */
        private val LIGHT_UI_KEYS = setOf(
            // Blur
            AppConfig.PREF_BLUR_BOTTOM_STATUS,
            AppConfig.PREF_BLUR_BOTTOM_RADIUS,
            AppConfig.PREF_BLUR_BOTTOM_ALPHA,
            AppConfig.PREF_BLUR_BOTTOM_BLOB_ANIM,
            AppConfig.PREF_BLUR_BOTTOM_INTENSITY,
            AppConfig.PREF_BLUR_RADIUS,
            AppConfig.PREF_BLUR_ROUNDS,
            AppConfig.PREF_BLUR_INTENSITY,
            AppConfig.PREF_ENABLE_BLUR,
            AppConfig.PREF_USE_SYSTEM_BLUR,
            // Home / header / FAB / toolbar
            AppConfig.PREF_HOME_BANNER_HEIGHT,
            AppConfig.PREF_HEADER_TOP_ROW_PADDING,
            AppConfig.PREF_FAB_EXTENDED,
            AppConfig.PREF_SHOW_QUICK_ACTIONS,
            AppConfig.PREF_DISABLE_HOME_BANNER,
            AppConfig.PREF_TOOLBAR_CENTER_SUBTITLE_MODE,
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            // Selected / sheet / profile banner
            AppConfig.PREF_SELECTED_BANNER_DIM,
            AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED,
            AppConfig.PREF_SELECTED_BANNER_URI,
            AppConfig.PREF_SHEET_BANNER_DIM,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_SHAPE,
            AppConfig.PREF_CUSTOM_THEME_BANNER_URI,
            // Shapes (list chrome)
            AppConfig.PREF_ICON_SHAPE,
            AppConfig.PREF_ARROW_SHAPE,
            // Indicator / category
            AppConfig.PREF_INDICATOR_STYLE,
            AppConfig.PREF_CATEGORY_STYLE,
            // Search chip
            AppConfig.PREF_SEARCH_BAR_CHIP,
            AppConfig.PREF_SEARCH_CHIP_GRADIENT,
            AppConfig.PREF_SEARCH_BAR_CHIP_DUAL_SELECTION,
            // Group tab chrome
            AppConfig.PREF_TAB_BADGE_LIMIT,
            AppConfig.PREF_GROUP_ALL_DISPLAY,
            AppConfig.PREF_GROUP_ALL_TAB_ICON,
            // List chrome
            AppConfig.PREF_HIDE_SCROLL_BUTTONS,
            AppConfig.PREF_COMPACT_LIST_ACTIONS,
            AppConfig.PREF_CONFIRM_REMOVE,
            AppConfig.PREF_START_SCAN_IMMEDIATE,
            // Notification labels (UI only)
            AppConfig.PREF_HIDE_DIRECT_TRAFFIC_NOTIFICATION,
            AppConfig.PREF_SHOW_GROUP_NAME_NOTIFICATION,
            // Snowflakes
            AppConfig.PREF_ENABLE_SNOWFLAKES,
            AppConfig.PREF_SNOWFLAKES_SPEED,
            AppConfig.PREF_SNOWFLAKES_COUNT,
            AppConfig.PREF_SNOWFLAKES_SIZE,
            AppConfig.PREF_SNOWFLAKES_OPACITY,
            AppConfig.PREF_SNOWFLAKES_WIND,
            AppConfig.PREF_SNOWFLAKES_LIFE,
            // Particles
            AppConfig.PREF_ENABLE_PARTICLES_SHEET,
            AppConfig.PREF_PARTICLES_SETTINGS,
            AppConfig.PREF_PARTICLES_FRAME_DELAY,
            AppConfig.PREF_PARTICLES_LINE_LENGTH,
            AppConfig.PREF_PARTICLES_LINE_THICKNESS,
            AppConfig.PREF_PARTICLES_RADIUS_MAX,
            AppConfig.PREF_PARTICLES_RADIUS_MIN,
            AppConfig.PREF_PARTICLES_DENSITY,
            AppConfig.PREF_PARTICLES_SPEED_FACTOR,
            // Banner character
            AppConfig.PREF_BANNER_CHARACTER_WIDTH,
            AppConfig.PREF_BANNER_CHARACTER_HEIGHT,
            AppConfig.PREF_BANNER_CHARACTER_MARGIN_TOP,
            AppConfig.PREF_BANNER_CHARACTER_MARGIN_BOTTOM,
            AppConfig.PREF_BANNER_CHARACTER_MARGIN_END,
            AppConfig.PREF_BANNER_SETTINGS_CHARACTER,
            // Weather chip
            AppConfig.PREF_WEATHER_USE_CELSIUS,
            AppConfig.PREF_WEATHER_CUSTOM_LOCATION,
            // Splash next launch
            AppConfig.PREF_SHOW_SPLASH,
        )
    }
}
