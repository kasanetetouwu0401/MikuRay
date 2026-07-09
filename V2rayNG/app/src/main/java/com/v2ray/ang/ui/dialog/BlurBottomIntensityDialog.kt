package com.v2ray.ang.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.BlurBottomStatusController
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
        val originalOverlay = BlurBottomStatusController.currentOverlayStrength()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_blur_intensity, null)
        val sliderRadius = dialogView.findViewById<Slider>(R.id.slider_blur_radius)
        val sliderRounds = dialogView.findViewById<Slider>(R.id.slider_blur_rounds)
        val sliderOverlay = dialogView.findViewById<Slider>(R.id.slider_blur_overlay)

        sliderRadius.value = originalRadius.toFloat().coerceIn(2f, 100f)
        sliderRounds.value = originalRounds.toFloat().coerceIn(1f, 15f)
        sliderOverlay.value = originalOverlay.toFloat().coerceIn(
            AppConfig.BLUR_BOTTOM_OVERLAY_STRENGTH_MIN.toFloat(),
            AppConfig.BLUR_BOTTOM_OVERLAY_STRENGTH_MAX.toFloat()
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_blur_bottom_intensity)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        val mainActivity = context.findActivity() as? MainActivity

        fun previewOverlay(strength: Int) {
            mainActivity?.previewBottomBlurOverlayStrength(strength)
        }

        sliderOverlay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) previewOverlay(value.toInt())
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val radius = sliderRadius.value.toInt()
            val rounds = sliderRounds.value.toInt()
            val overlay = sliderOverlay.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, radius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, rounds)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_OVERLAY_STRENGTH, overlay)
            updateSummary(radius, rounds, overlay)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            previewOverlay(originalOverlay)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, originalRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, originalRounds)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_OVERLAY_STRENGTH, originalOverlay)
            updateSummary(originalRadius, originalRounds, originalOverlay)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            sliderRadius.value = AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS.toFloat()
            sliderRounds.value = AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS.toFloat()
            sliderOverlay.value = AppConfig.BLUR_BOTTOM_OVERLAY_STRENGTH_DEFAULT.toFloat()
            previewOverlay(AppConfig.BLUR_BOTTOM_OVERLAY_STRENGTH_DEFAULT)
        }
    }

    fun updateSummary(radius: Int, rounds: Int, overlay: Int = BlurBottomStatusController.currentOverlayStrength()) {
        summary = context.getString(R.string.summary_blur_intensity_value, radius, rounds, overlay)
    }
}
