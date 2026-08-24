package com.miku.ray.util

import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import com.miku.ray.R

data class ThemePreset(
    val key: String,
    val label: String,
    @ColorInt val sourceColor: Int,
    @StyleRes val styleRes: Int,
)

object ThemePresetCatalog {
    val presets: List<ThemePreset> = listOf(
        ThemePreset("red", "Red", 0xFFF44336.toInt(), R.style.AppTheme_SourceRed),
        ThemePreset("pink", "Pink", 0xFFE91E63.toInt(), R.style.AppTheme_SourcePink),
        ThemePreset("purple", "Purple", 0xFF9C27B0.toInt(), R.style.AppTheme_SourcePurple),
        ThemePreset("deep_purple", "Deep Purple", 0xFF673AB7.toInt(), R.style.AppTheme_SourceDeepPurple),
        ThemePreset("indigo", "Indigo", 0xFF3F51B5.toInt(), R.style.AppTheme_SourceIndigo),
        ThemePreset("blue", "Blue", 0xFF2196F3.toInt(), R.style.AppTheme_SourceBlue),
        ThemePreset("light_blue", "Light Blue", 0xFF03A9F4.toInt(), R.style.AppTheme_SourceLightBlue),
        ThemePreset("cyan", "Cyan", 0xFF00BCD4.toInt(), R.style.AppTheme_SourceCyan),
        ThemePreset("teal", "Teal", 0xFF009688.toInt(), R.style.AppTheme_SourceTeal),
        ThemePreset("green", "Green", 0xFF4CAF50.toInt(), R.style.AppTheme_SourceGreen),
        ThemePreset("light_green", "Light Green", 0xFF8BC34A.toInt(), R.style.AppTheme_SourceLightGreen),
        ThemePreset("lime", "Lime", 0xFFCDDC39.toInt(), R.style.AppTheme_SourceLime),
        ThemePreset("yellow", "Yellow", 0xFFFFEB3B.toInt(), R.style.AppTheme_SourceYellow),
        ThemePreset("amber", "Amber", 0xFFFFC107.toInt(), R.style.AppTheme_SourceAmber),
        ThemePreset("orange", "Orange", 0xFFFF9800.toInt(), R.style.AppTheme_SourceOrange),
        ThemePreset("deep_orange", "Deep Orange", 0xFFFF5722.toInt(), R.style.AppTheme_SourceDeepOrange),
        ThemePreset("brown", "Brown", 0xFF795548.toInt(), R.style.AppTheme_SourceBrown),
        ThemePreset("grey", "Grey", 0xFF9E9E9E.toInt(), R.style.AppTheme_SourceGrey),
        ThemePreset("blue_grey", "Blue Grey", 0xFF607D8B.toInt(), R.style.AppTheme_SourceBlueGrey),
    )

    fun find(key: String?): ThemePreset =
        presets.firstOrNull { it.key == key } ?: presets.first()

    fun styleForKey(key: String?): Int = find(key).styleRes
}
