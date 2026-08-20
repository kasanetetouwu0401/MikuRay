package com.v2ray.ang.util

import android.content.Context
import android.os.Build
import androidx.preference.SwitchPreferenceCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

object SystemWindowBlurPreferenceController {

    fun bind(preference: SwitchPreferenceCompat?, context: Context) {
        preference ?: return

        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        preference.isEnabled = supported
        preference.summary = context.getString(
            if (supported) {
                R.string.summary_pref_use_system_window_blur
            } else {
                R.string.summary_pref_use_system_window_blur_unavailable
            }
        )

        preference.setOnPreferenceChangeListener { _, newValue ->
            MmkvManager.encodeSettings(
                AppConfig.PREF_USE_SYSTEM_WINDOW_BLUR,
                newValue as Boolean
            )
            true
        }
    }
}
