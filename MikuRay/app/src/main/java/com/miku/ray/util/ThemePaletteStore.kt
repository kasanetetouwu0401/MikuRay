package com.miku.ray.util

import android.graphics.Color
import androidx.annotation.ColorInt
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import java.util.Locale

/**
 * Stores reusable theme seed colors for the Palette Library. A seed is enough because
 * Material Dynamic Colors derives the complete light/dark scheme from it at runtime.
 */
object ThemePaletteStore {
    private const val MAX_SAVED_COLORS = 12
    private const val MAX_RECENT_COLORS = 8

    val suggestedColors: List<Int> = listOf(
        Color.parseColor("#006A6A"), // Miku teal
        Color.parseColor("#6750A4"), // Material purple
        Color.parseColor("#00639B"), // Ocean blue
        Color.parseColor("#A23B72"), // Berry
        Color.parseColor("#8C5000"), // Amber
        Color.parseColor("#466800"), // Moss
        Color.parseColor("#8E4E00"), // Copper
        Color.parseColor("#825500")  // Gold
    )

    fun savedColors(): List<Int> = readColors(AppConfig.PREF_THEME_PALETTE_SAVED)

    fun recentColors(): List<Int> = readColors(AppConfig.PREF_THEME_PALETTE_RECENT)

    fun addSavedColor(@ColorInt color: Int) {
        writeColors(
            AppConfig.PREF_THEME_PALETTE_SAVED,
            (listOf(color) + savedColors()).distinct().take(MAX_SAVED_COLORS)
        )
    }

    fun removeSavedColor(@ColorInt color: Int) {
        writeColors(
            AppConfig.PREF_THEME_PALETTE_SAVED,
            savedColors().filterNot { it == color }
        )
    }

    fun recordRecentColor(@ColorInt color: Int) {
        writeColors(
            AppConfig.PREF_THEME_PALETTE_RECENT,
            (listOf(color) + recentColors()).distinct().take(MAX_RECENT_COLORS)
        )
    }

    private fun readColors(key: String): List<Int> {
        return MmkvManager.decodeSettingsString(key)
            .orEmpty()
            .split(',')
            .mapNotNull { encoded ->
                encoded.trim().takeIf { it.isNotEmpty() }?.let {
                    runCatching { Color.parseColor(it) }.getOrNull()
                }
            }
            .distinct()
    }

    private fun writeColors(key: String, colors: List<Int>) {
        MmkvManager.encodeSettings(
            key,
            colors.joinToString(",") { colorToHex(it) }
        )
    }

    fun colorToHex(@ColorInt color: Int): String =
        String.format(Locale.US, "#%06X", 0xFFFFFF and color)
}
