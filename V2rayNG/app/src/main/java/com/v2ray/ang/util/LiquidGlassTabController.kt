package com.v2ray.ang.util

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

/**
 * Controller for the Liquid Glass effect on:
 *  - The inner tab layout card (cornerRadius = 23dp)
 *  - The search bar card (cornerRadius = 28dp)
 *
 * Uses the original shader uniforms (identical to Telegram's LiquidGlassEffect):
 *   resolution, center, size, radius, thickness,
 *   refract_index, refract_intensity, foreground_color_premultiplied
 *
 * Requires API 33 (Android 13). Safe to call on any API level — silently no-ops below 33.
 */
object LiquidGlassTabController {

    private const val TAG = "LiquidGlassTabCtrl"

    // Corner radii matching the XML layouts
    private const val TAB_CORNER_RADIUS_DP    = 23f
    private const val SEARCH_CORNER_RADIUS_DP = 28f

    // Liquid glass tuning — matching Telegram's typical values
    private const val DEFAULT_THICKNESS_DP      = 18f
    private const val DEFAULT_REFRACT_INDEX      = 1.45f
    private const val DEFAULT_REFRACT_INTENSITY  = 0.5f

    // Foreground tint overlay (premultiplied RGBA)
    private const val TINT_R = 0.12f
    private const val TINT_G = 0.12f
    private const val TINT_B = 0.16f
    private const val TINT_A = 0.18f

    private val LISTENER_TAG_KEY = "liquid_glass_listener".hashCode()

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_LIQUID_GLASS_TAB, false)

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Apply or remove the liquid-glass effect on both [tabCardView] and [searchCardView].
     * Pass null for either to skip it. No-op below API 33.
     */
    fun applyState(context: Context, tabCardView: View?, searchCardView: View?) {
        if (!isSupported()) return
        if (isEnabled()) {
            applyEffect(context, tabCardView,    TAB_CORNER_RADIUS_DP)
            applyEffect(context, searchCardView, SEARCH_CORNER_RADIUS_DP)
        } else {
            clearEffect(tabCardView)
            clearEffect(searchCardView)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyEffect(context: Context, view: View?, cornerRadiusDp: Float) {
        view ?: return
        try {
            val src = context.resources.openRawResource(
                context.resources.getIdentifier("liquid_glass_tab", "raw", context.packageName)
            ).bufferedReader().use { it.readText() }

            val shader = RuntimeShader(src)
            val density = context.resources.displayMetrics.density

            attachLayoutListener(shader, view, density, cornerRadiusDp)

            val effect = RenderEffect.createRuntimeShaderEffect(shader, "img")
            view.setRenderEffect(effect)
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply LiquidGlass on ${view.javaClass.simpleName}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun attachLayoutListener(
        shader: RuntimeShader,
        view: View,
        density: Float,
        cornerRadiusDp: Float
    ) {
        val old = view.getTag(LISTENER_TAG_KEY)
        if (old is View.OnLayoutChangeListener) view.removeOnLayoutChangeListener(old)

        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateUniforms(shader, view, density, cornerRadiusDp)
        }
        view.addOnLayoutChangeListener(listener)
        view.setTag(LISTENER_TAG_KEY, listener)

        if (view.isLaidOut && view.width > 0) updateUniforms(shader, view, density, cornerRadiusDp)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun updateUniforms(
        shader: RuntimeShader,
        view: View,
        density: Float,
        cornerRadiusDp: Float
    ) {
        if (view.width == 0 || view.height == 0) return

        val w         = view.width.toFloat()
        val h         = view.height.toFloat()
        val cx        = w / 2f
        val cy        = h / 2f
        val r         = cornerRadiusDp * density
        val thickness = DEFAULT_THICKNESS_DP * density

        shader.setFloatUniform("resolution", w, h)
        shader.setFloatUniform("center", cx, cy)
        shader.setFloatUniform("size", cx, cy)
        // radius: (topRight, bottomRight, topLeft, bottomLeft) — all equal for uniform rounding
        shader.setFloatUniform("radius", r, r, r, r)
        shader.setFloatUniform("thickness", thickness)
        shader.setFloatUniform("refract_index", DEFAULT_REFRACT_INDEX)
        shader.setFloatUniform("refract_intensity", DEFAULT_REFRACT_INTENSITY)
        // Pre-multiplied RGBA
        shader.setFloatUniform(
            "foreground_color_premultiplied",
            TINT_R * TINT_A, TINT_G * TINT_A, TINT_B * TINT_A, TINT_A
        )

        view.invalidate()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun clearEffect(view: View?) {
        view ?: return
        try {
            view.setRenderEffect(null)
            view.setLayerType(View.LAYER_TYPE_NONE, null)
            val old = view.getTag(LISTENER_TAG_KEY)
            if (old is View.OnLayoutChangeListener) {
                view.removeOnLayoutChangeListener(old)
                view.setTag(LISTENER_TAG_KEY, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear LiquidGlass on ${view.javaClass.simpleName}", e)
        }
    }
}
