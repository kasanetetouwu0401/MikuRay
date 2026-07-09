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
import com.v2ray.ang.util.WindowBlurUtils

class BlurIntensityDialog @JvmOverloads constructor(
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
        val originalRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS)
        val originalRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)
        val originalOverlay = WindowBlurUtils.currentOverlayStrength()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_blur_intensity, null)
        val sliderRadius = dialogView.findViewById<Slider>(R.id.slider_blur_radius)
        val sliderRounds = dialogView.findViewById<Slider>(R.id.slider_blur_rounds)
        val sliderOverlay = dialogView.findViewById<Slider>(R.id.slider_blur_overlay)

        sliderRadius.value = originalRadius.toFloat().coerceIn(2f, 100f)
        sliderRounds.value = originalRounds.toFloat().coerceIn(1f, 15f)
        sliderOverlay.value = originalOverlay.toFloat().coerceIn(
            AppConfig.BLUR_OVERLAY_STRENGTH_MIN.toFloat(),
            AppConfig.BLUR_OVERLAY_STRENGTH_MAX.toFloat()
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_blur_intensity)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        sliderRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                WindowBlurUtils.updateWindowBlur(dialog.window, value, sliderRounds.value.toInt())
            }
        }
        sliderRounds.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                WindowBlurUtils.updateWindowBlur(dialog.window, sliderRadius.value, value.toInt())
            }
        }
        sliderOverlay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                WindowBlurUtils.updateWindowBlurOverlay(dialog.window, value.toInt())
            }
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val radius = sliderRadius.value.toInt()
            val rounds = sliderRounds.value.toInt()
            val overlay = sliderOverlay.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_RADIUS, radius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_ROUNDS, rounds)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_OVERLAY_STRENGTH, overlay)
            updateSummary(radius, rounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            WindowBlurUtils.updateWindowBlur(dialog.window, originalRadius.toFloat(), originalRounds)
            WindowBlurUtils.updateWindowBlurOverlay(dialog.window, originalOverlay)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_RADIUS, originalRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_ROUNDS, originalRounds)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_OVERLAY_STRENGTH, originalOverlay)
            updateSummary(originalRadius, originalRounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            sliderRadius.value = AppConfig.DEFAULT_BLUR_RADIUS.toFloat()
            sliderRounds.value = AppConfig.DEFAULT_BLUR_ROUNDS.toFloat()
            sliderOverlay.value = AppConfig.BLUR_OVERLAY_STRENGTH_DEFAULT.toFloat()
            WindowBlurUtils.updateWindowBlur(
                dialog.window,
                AppConfig.DEFAULT_BLUR_RADIUS.toFloat(),
                AppConfig.DEFAULT_BLUR_ROUNDS
            )
            WindowBlurUtils.updateWindowBlurOverlay(dialog.window, AppConfig.BLUR_OVERLAY_STRENGTH_DEFAULT)
        }
    }

    fun updateSummary(radius: Int, rounds: Int) {
        summary = context.getString(R.string.summary_blur_intensity_value, radius, rounds)
    }
}
