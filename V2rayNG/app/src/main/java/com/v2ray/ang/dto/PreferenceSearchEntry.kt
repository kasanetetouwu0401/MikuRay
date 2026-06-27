package com.v2ray.ang.dto

/**
 * A single searchable preference entry, indexed from one of the app's
 * preference XML resources (pref_settings, pref_ui_settings, pref_vpn_settings, etc).
 *
 * @param key the preference key (android:key), used to scroll/highlight the target preference.
 * @param title resolved display title of the preference.
 * @param summary resolved display summary, if any (empty string if none).
 * @param iconRes drawable resource id for the preference icon, or 0 if none.
 * @param categoryTitle resolved title of the parent PreferenceCategory/UwuPreferenceCategory, if any.
 * @param screenTitle resolved title of the settings screen (activity) this preference lives on.
 * @param targetActivity the Activity class that hosts this preference.
 */
data class PreferenceSearchEntry(
    val key: String,
    val title: String,
    val summary: String,
    val iconRes: Int,
    val categoryTitle: String,
    val screenTitle: String,
    val targetActivity: Class<out android.app.Activity>
)
