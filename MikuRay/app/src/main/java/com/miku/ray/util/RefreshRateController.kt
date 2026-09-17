package com.miku.ray.util

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.Window
import android.view.WindowManager
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import kotlin.math.abs
import kotlin.math.roundToInt

object RefreshRateController {

    const val VALUE_DEFAULT = "0"

    private const val EXACT_MATCH_TOLERANCE = 0.5f

    fun currentValue(): String =
    MmkvManager.decodeSettingsString(AppConfig.PREF_REFRESH_RATE) ?: VALUE_DEFAULT

    fun applyToWindow(window: Window?) {
        if (window == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val desiredHz = currentValue().toFloatOrNull() ?: 0f
        val modeId = if (desiredHz <= 0f) 0 else findBestModeId(getDisplay(window), desiredHz)

        val attributes = window.attributes
        if (attributes.preferredDisplayModeId != modeId) {
            attributes.preferredDisplayModeId = modeId
            window.attributes = attributes
        }
    }

    fun supportedRefreshRates(context: Context): Set<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptySet()
        val display = getDisplay(context) ?: return emptySet()
        return display.supportedModes?.map { it.refreshRate.roundToInt() }?.toSet() ?: emptySet()
    }

    private fun getDisplay(window: Window): Display? = getDisplay(window.context)

    private fun getDisplay(context: Context): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
    }

    private fun findBestModeId(display: Display?, desiredHz: Float): Int {
        if (display == null) return 0
        val modes = display.supportedModes ?: return 0
        if (modes.isEmpty()) return 0

        val currentMode = display.mode
        val sameResModes = modes.filter {
            it.physicalWidth == currentMode?.physicalWidth && it.physicalHeight == currentMode?.physicalHeight
        }
        val candidates = sameResModes.ifEmpty { modes.toList() }

        candidates.minByOrNull { abs(it.refreshRate - desiredHz) }
        ?.takeIf { abs(it.refreshRate - desiredHz) <= EXACT_MATCH_TOLERANCE }
        ?.let { return it.modeId }

        candidates.filter { it.refreshRate >= desiredHz }
        .minByOrNull { it.refreshRate }
        ?.let { return it.modeId }

        return candidates.maxByOrNull { it.refreshRate }?.modeId ?: 0
    }
}
