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

/**
 * Applies the user's preferred app refresh rate (default / 60 / 90 / 120 Hz) to an
 * Activity's window via WindowManager.LayoutParams.preferredDisplayModeId, matching
 * against the display's actual supported modes rather than just hinting a raw Hz value.
 *
 * Stored pref value is a Hz string ("0" = system default, i.e. no preference expressed).
 */
object RefreshRateController {

    const val VALUE_DEFAULT = "0"

    /** Hz values are never exact ("90" is often reported as 89.99...), tolerate a small drift. */
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

    /**
     * Rounded refresh rates (Hz) the device's default display actually supports, e.g.
     * {60, 90, 120}. Empty if this couldn't be determined (API < 23, or no display), in
     * which case callers should show every option rather than hide everything.
     */
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

    /**
     * Picks the supported Display.Mode to use for [desiredHz], preferring modes that keep
     * the display's current resolution (some devices expose distinct modes per-resolution,
     * and we don't want a refresh-rate pick to silently change the rendering resolution too).
     *
     * Resolution rules, in order:
     * 1. Exact match (within [EXACT_MATCH_TOLERANCE] Hz) - e.g. asking for 90 on a device
     *    that actually reports 89.97.
     * 2. The lowest available rate that's still >= desired - so "90Hz" on a 60/120-only
     *    device lands on 120, guaranteeing at least the smoothness the user asked for
     *    rather than silently downgrading them to 60.
     * 3. If nothing meets or exceeds desired, the highest rate below it (best available).
     */
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
