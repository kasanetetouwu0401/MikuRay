package com.v2ray.ang.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.WindowBlurUtils

class BlurBottomIntensityDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    override fun onClick() {
        val originalRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS)
        val originalRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS)
        val originalGlassEdge = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_GLASS_EDGE_AMOUNT,
            AppConfig.DEFAULT_BLUR_BOTTOM_GLASS_EDGE_AMOUNT
        )

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_blur_bottom_intensity, null)
        val sliderRadius = dialogView.findViewById<Slider>(R.id.slider_blur_radius)
        val sliderRounds = dialogView.findViewById<Slider>(R.id.slider_blur_rounds)
        val sliderGlassEdge = dialogView.findViewById<Slider>(R.id.slider_glass_edge)
        val groupGlassEdge = dialogView.findViewById<View>(R.id.group_glass_edge)
        val tvGlassEdgeUnsupported = dialogView.findViewById<View>(R.id.tv_glass_edge_unsupported)

        sliderRadius.value = originalRadius.toFloat().coerceIn(2f, 100f)
        sliderRounds.value = originalRounds.toFloat().coerceIn(1f, 15f)
        sliderGlassEdge.value = originalGlassEdge.toFloat().coerceIn(0f, 40f)

        // The lens refraction shader needs RuntimeShader (API 33+). On older devices we
        // still let the value be set (so it's ready to go the moment someone updates their
        // phone), but the control itself is hidden and replaced with a short explanation,
        // matching the tier-aware pattern used elsewhere for blur controls.
        val lensSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        groupGlassEdge.visibility = if (lensSupported) View.VISIBLE else View.GONE
        tvGlassEdgeUnsupported.visibility = if (lensSupported) View.GONE else View.VISIBLE

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_blur_bottom_intensity)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val radius = sliderRadius.value.toInt()
            val rounds = sliderRounds.value.toInt()
            val glassEdge = sliderGlassEdge.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, radius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, rounds)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_GLASS_EDGE_AMOUNT, glassEdge)
            updateSummary(radius, rounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, originalRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, originalRounds)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_GLASS_EDGE_AMOUNT, originalGlassEdge)
            updateSummary(originalRadius, originalRounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            sliderRadius.value = AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS.toFloat()
            sliderRounds.value = AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS.toFloat()
            sliderGlassEdge.value = AppConfig.DEFAULT_BLUR_BOTTOM_GLASS_EDGE_AMOUNT.toFloat()
        }
    }

    fun updateSummary(radius: Int, rounds: Int) {
        summary = context.getString(R.string.summary_blur_intensity_value, radius, rounds)
    }
}
