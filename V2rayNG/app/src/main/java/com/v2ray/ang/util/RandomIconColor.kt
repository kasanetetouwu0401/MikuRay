package com.v2ray.ang.util

import androidx.annotation.ColorInt
import com.google.android.material.color.utilities.Hct

/**
 * Generates a vivid, theme-aware background color for an icon based on a stable identity
 * (e.g. an icon drawable's constant state hash). Same identity always yields the same color,
 * so colors stay consistent across app restarts (a la Telegram's settings icon colors).
 */
object RandomIconColor {

    // Evenly spaced hues around the color wheel, offset to avoid muddy greens/yellows up front.
    private val HUES = intArrayOf(
        4, 24, 48, 84, 120, 152, 176, 200, 224, 256, 284, 312, 340
    )

    @ColorInt
    fun forIdentity(identity: Int, isDarkMode: Boolean): Int {
        val index = Math.floorMod(identity, HUES.size)
        val hue = HUES[index].toDouble()
        val tone = if (isDarkMode) 60.0 else 52.0
        return Hct.from(hue, 48.0, tone).toInt()
    }
}
