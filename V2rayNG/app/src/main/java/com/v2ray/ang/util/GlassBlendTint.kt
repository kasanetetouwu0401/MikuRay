package com.v2ray.ang.util

import android.content.res.Configuration
import android.content.Context
import android.graphics.BlendMode
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import androidx.annotation.ColorInt
import com.v2ray.ang.R

/**
 * One tint layer for a glass surface: a colour plus the blend mode it should be composited
 * with. InstallerX (via the miuix library's `BlurColors`/`BlendColorEntry`) tints its glass
 * with several such layers instead of one flat alpha overlay, which is what MikuRay's old
 * qmdeve `overlayColor` did. [GlassBlurView] draws a list of these, in order, on top of the
 * real-time blur.
 */
data class GlassTintEntry(
    @ColorInt val color: Int,
    val blendMode: GlassBlendMode = GlassBlendMode.SRC_OVER,
)

/**
 * Subset of Android's native [BlendMode] (API 29+) that covers the "Photoshop-style" modes
 * InstallerX's palette leans on most (Overlay/HardLight/SoftLight/ColorDodge/ColorBurn/
 * Luminosity), plus the simple ones. This intentionally does not try to replicate miuix's
 * own `BlurBlendMode` enum 1:1 (that's a compiled dependency we don't have source for) —
 * every entry here maps to a real platform BlendMode, so behaviour is verifiable.
 *
 * On API < 29, [BlendMode] does not exist yet; [toPorterDuff] gives the closest legacy
 * approximation so old devices still get a sensible (if flatter) tint rather than a crash.
 */
enum class GlassBlendMode {
    SRC_OVER,
    OVERLAY,
    HARD_LIGHT,
    SOFT_LIGHT,
    COLOR_DODGE,
    COLOR_BURN,
    LUMINOSITY,
    SCREEN,
    LIGHTEN,
    DARKEN;

    fun toPlatformBlendMode(): BlendMode = when (this) {
        SRC_OVER -> BlendMode.SRC_OVER
        OVERLAY -> BlendMode.OVERLAY
        HARD_LIGHT -> BlendMode.HARD_LIGHT
        SOFT_LIGHT -> BlendMode.SOFT_LIGHT
        COLOR_DODGE -> BlendMode.COLOR_DODGE
        COLOR_BURN -> BlendMode.COLOR_BURN
        LUMINOSITY -> BlendMode.LUMINOSITY
        SCREEN -> BlendMode.SCREEN
        LIGHTEN -> BlendMode.LIGHTEN
        DARKEN -> BlendMode.DARKEN
    }

    fun toPorterDuff(): PorterDuff.Mode = when (this) {
        SRC_OVER -> PorterDuff.Mode.SRC_OVER
        SCREEN -> PorterDuff.Mode.SCREEN
        LIGHTEN -> PorterDuff.Mode.LIGHTEN
        DARKEN -> PorterDuff.Mode.DARKEN
        // Overlay/HardLight/SoftLight/ColorDodge/ColorBurn/Luminosity have no PorterDuff
        // equivalent on API < 29; falling back to a plain alpha blend keeps the tint safe
        // and visible rather than silently dropping it.
        else -> PorterDuff.Mode.SRC_OVER
    }
}

internal object GlassPaints {

    fun tintPaint(entry: GlassTintEntry): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = entry.color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = entry.blendMode.toPlatformBlendMode()
        } else {
            @Suppress("DEPRECATION")
            xfermode = PorterDuffXfermode(entry.blendMode.toPorterDuff())
        }
    }
}

/**
 * Default tint recipes. Two flavours, matching the two ways MikuRay already uses blur:
 *  - [forCurrentTheme] — a light, theme-adaptive "material" tint for bounded surfaces like
 *    the bottom status pill, built from the app's own Material You colours rather than
 *    InstallerX's fixed iOS-style hex palette (so it still looks like *MikuRay*, just with
 *    InstallerX's layered-tint technique).
 *  - [forScrim] — keeps the existing ~47% black dim behind dialogs / the loading overlay
 *    exactly as before (so legibility doesn't regress), with one faint extra layer for the
 *    same glassy depth.
 */
object GlassTintDefaults {

    private fun withAlpha(@ColorInt color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    fun isNightMode(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun forCurrentTheme(context: Context): List<GlassTintEntry> {
        val isDark = isNightMode(context)
        val surface = context.getColorAttr(R.attr.colorSurface)
        val onSurface = context.getColorAttr(R.attr.colorOnSurface)

        val base = GlassTintEntry(
            color = withAlpha(surface, if (isDark) 140 else 150),
            blendMode = GlassBlendMode.SRC_OVER,
        )
        val depth = if (isDark) {
            GlassTintEntry(color = withAlpha(Color.WHITE, 15), blendMode = GlassBlendMode.SOFT_LIGHT)
        } else {
            GlassTintEntry(color = withAlpha(onSurface, 20), blendMode = GlassBlendMode.OVERLAY)
        }
        return listOf(base, depth)
    }

    fun forScrim(): List<GlassTintEntry> {
        val base = GlassTintEntry(color = Color.argb(120, 0, 0, 0), blendMode = GlassBlendMode.SRC_OVER)
        val sheen = GlassTintEntry(color = withAlpha(Color.WHITE, 10), blendMode = GlassBlendMode.SOFT_LIGHT)
        return listOf(base, sheen)
    }
}
