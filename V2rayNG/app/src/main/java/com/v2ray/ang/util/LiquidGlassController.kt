package com.v2ray.ang.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

/**
 * "Liquid Glass" look applied to all pill/card UI elements in MainActivity:
 *  - Inner tab layout card        (cornerRadius = 23dp)
 *  - Search bar card              (cornerRadius = 28dp)
 *  - btn_home card                (cornerRadius = 28dp)
 *  - btn_more_menu card           (cornerRadius = 28dp)
 *  - btn_add_config card          (cornerRadius = 28dp)
 *  - btn_add_sub card             (cornerRadius = 28dp, colorPrimary bg)
 *
 * Ported directly from Telegram's blur3 StrokeDrawable (see LiquidGlassStrokeDrawable.kt).
 * NOT a blur/refraction shader — a static fill + two-tone stroke (bright top, dim bottom),
 * the "glass edge catching light" look seen in NagramX/Telegram screenshots.
 * No RenderEffect/AGSL, no API-level restriction.
 */
object LiquidGlassController {

    private const val TAG = "LiquidGlassCtrl"

    private const val TAB_CORNER_RADIUS_DP  = 23f
    private const val PILL_CORNER_RADIUS_DP = 28f // search bar + all button cards

    // Subtle fill so the card reads as "glass" over whatever sits behind it
    private const val FILL_ALPHA_DARK  = 0x14 // ~8% white, dark theme
    private const val FILL_ALPHA_LIGHT = 0x0A // ~4% white, light theme

    private val ORIGINAL_BG_TAG_KEY = "liquid_glass_original_bg".hashCode()
    private val ORIGINAL_CARD_BG_TAG_KEY = "liquid_glass_original_card_bg".hashCode()
    private val ORIGINAL_STROKE_W_TAG_KEY = "liquid_glass_original_stroke_w".hashCode()
    private val NULL_BG_MARKER = Any()

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_LIQUID_GLASS, false)

    /** Kept for call-site compatibility; this look has no API-level restriction. */
    fun isSupported(): Boolean = true

    /**
     * Apply or clear the effect on all provided views.
     * [tabCardView] uses 23dp radius; all others use 28dp.
     * Pass null for any view to skip it safely.
     */
    fun applyState(
        context: Context,
        tabCardView: View?,
        searchCardView: View?,
        btnHome: View?,
        btnMoreMenu: View?,
        btnAddConfig: View?,
        btnAddSub: View?
    ) {
        val enable = isEnabled()

        fun handle(view: View?, cornerDp: Float) {
            if (enable) applyEffect(context, view, cornerDp)
            else clearEffect(view)
        }

        handle(tabCardView,    TAB_CORNER_RADIUS_DP)
        handle(searchCardView, PILL_CORNER_RADIUS_DP)
        handle(btnHome,        PILL_CORNER_RADIUS_DP)
        handle(btnMoreMenu,    PILL_CORNER_RADIUS_DP)
        handle(btnAddConfig,   PILL_CORNER_RADIUS_DP)
        handle(btnAddSub,      PILL_CORNER_RADIUS_DP)
    }

    private fun isDarkTheme(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyEffect(context: Context, view: View?, cornerRadiusDp: Float) {
        view ?: return
        try {
            if (view.getTag(ORIGINAL_BG_TAG_KEY) == null) {
                view.setTag(ORIGINAL_BG_TAG_KEY, view.background ?: NULL_BG_MARKER)
            }

            // MaterialCardView paints its own background/stroke separately from
            // View.background, so it would otherwise sit on top and hide our drawable.
            // (btn_add_sub keeps its own colorPrimary look untouched if you don't want
            // it ghosted — see note below.)
            if (view is MaterialCardView) {
                if (view.getTag(ORIGINAL_CARD_BG_TAG_KEY) == null) {
                    view.setTag(ORIGINAL_CARD_BG_TAG_KEY, view.cardBackgroundColor)
                    view.setTag(ORIGINAL_STROKE_W_TAG_KEY, view.strokeWidth)
                }
                view.setCardBackgroundColor(Color.TRANSPARENT)
                view.strokeWidth = 0
                view.cardElevation = 0f
            }

            val density = context.resources.displayMetrics.density
            val isDark = isDarkTheme(context)

            val glass = LiquidGlassStrokeDrawable(density).apply {
                cornerRadius = cornerRadiusDp * density
                setFillColor(colorWithAlpha(0xFFFFFF, if (isDark) FILL_ALPHA_DARK else FILL_ALPHA_LIGHT))
                applyThemeDefaults(isDark)
            }

            view.background = glass
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to apply glass look on " + view.javaClass.simpleName, e)
        }
    }

    private fun clearEffect(view: View?) {
        view ?: return
        try {
            val original = view.getTag(ORIGINAL_BG_TAG_KEY)
            if (original != null) {
                view.background = if (original === NULL_BG_MARKER) null else original as? Drawable
                view.setTag(ORIGINAL_BG_TAG_KEY, null)
            }
            if (view is MaterialCardView) {
                val origCardBg = view.getTag(ORIGINAL_CARD_BG_TAG_KEY) as? android.content.res.ColorStateList
                val origStrokeW = view.getTag(ORIGINAL_STROKE_W_TAG_KEY) as? Int
                origCardBg?.let { view.setCardBackgroundColor(it) }
                origStrokeW?.let { view.strokeWidth = it }
                view.setTag(ORIGINAL_CARD_BG_TAG_KEY, null)
                view.setTag(ORIGINAL_STROKE_W_TAG_KEY, null)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to clear glass look on " + view.javaClass.simpleName, e)
        }
    }

    private fun colorWithAlpha(rgb: Int, alpha: Int): Int =
        (alpha shl 24) or (rgb and 0x00FFFFFF)
}
