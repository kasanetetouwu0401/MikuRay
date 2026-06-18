package com.v2ray.ang.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.preference.Preference
import com.google.android.material.button.MaterialButton
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

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_blur_intensity, null)
        val sliderRadius = dialogView.findViewById<Slider>(R.id.slider_blur_radius)
        val sliderRounds = dialogView.findViewById<Slider>(R.id.slider_blur_rounds)
        val iconView = dialogView.findViewById<android.widget.ImageView>(R.id.dialog_icon)
        val titleView = dialogView.findViewById<android.widget.TextView>(R.id.dialog_title)
        val positiveButton = dialogView.findViewById<MaterialButton>(R.id.positive_button)
        val negativeButton = dialogView.findViewById<MaterialButton>(R.id.negative_button)
        val neutralButton = dialogView.findViewById<MaterialButton>(R.id.neutral_button)

        icon?.let { iconView.setImageDrawable(it) }
        titleView.text = title

        sliderRadius.value = originalRadius.toFloat().coerceIn(2f, 100f)
        sliderRounds.value = originalRounds.toFloat().coerceIn(1f, 15f)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        positiveButton.setOnClickListener {
            val radius = sliderRadius.value.toInt()
            val rounds = sliderRounds.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, radius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, rounds)
            updateSummary(radius, rounds)
            dialog.dismiss()
        }

        negativeButton.setOnClickListener {
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, originalRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ROUNDS, originalRounds)
            updateSummary(originalRadius, originalRounds)
            dialog.dismiss()
        }

        neutralButton.setOnClickListener {
            sliderRadius.value = AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS.toFloat()
            sliderRounds.value = AppConfig.DEFAULT_BLUR_BOTTOM_ROUNDS.toFloat()
        }

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()
    }

    fun updateSummary(radius: Int, rounds: Int) {
        summary = context.getString(R.string.summary_blur_intensity_value, radius, rounds)
    }
}
