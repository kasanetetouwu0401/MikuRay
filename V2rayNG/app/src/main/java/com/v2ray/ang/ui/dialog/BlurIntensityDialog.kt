package com.v2ray.ang.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
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

    // Thickness only has any visible effect once the liquid-glass refraction shader
    // is actually running (API 33+ - see BlurredBackgroundDrawableRenderNode, which
    // only reads boundProps.liquidThickness inside its `liquidGlassEffect != null`
    // branch). Below that there's no shader and, as of removing the old qmdeve
    // fallback, no alternate blur engine either - so the rounds slider would just be
    // dead weight. Hide it instead of showing a control that does nothing.
    private val usesLiquidGlassEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun onClick() {
        val originalRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS)
        val originalRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_blur_intensity, null)
        val sliderRadius = dialogView.findViewById<Slider>(R.id.slider_blur_radius)
        val sliderRounds = dialogView.findViewById<Slider>(R.id.slider_blur_rounds)
        val labelRounds = dialogView.findViewById<TextView>(R.id.label_blur_rounds)
        val labelWarning = dialogView.findViewById<TextView>(R.id.label_blur_warning)

        if (usesLiquidGlassEffect) {
            labelRounds.setText(R.string.blur_intensity_thickness_label)
            labelWarning.setText(R.string.title_blur_warning_thickness)
        } else {
            labelRounds.visibility = View.GONE
            sliderRounds.visibility = View.GONE
            labelWarning.visibility = View.GONE
        }

        sliderRadius.value = originalRadius.toFloat().coerceIn(2f, 100f)
        sliderRounds.value = originalRounds.toFloat().coerceIn(1f, 15f)

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

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val radius = sliderRadius.value.toInt()
            val rounds = sliderRounds.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_RADIUS, radius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_ROUNDS, rounds)
            updateSummary(radius, rounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            WindowBlurUtils.updateWindowBlur(dialog.window, originalRadius.toFloat(), originalRounds)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_RADIUS, originalRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_ROUNDS, originalRounds)
            updateSummary(originalRadius, originalRounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            sliderRadius.value = AppConfig.DEFAULT_BLUR_RADIUS.toFloat()
            sliderRounds.value = AppConfig.DEFAULT_BLUR_ROUNDS.toFloat()
            WindowBlurUtils.updateWindowBlur(
                dialog.window,
                AppConfig.DEFAULT_BLUR_RADIUS.toFloat(),
                AppConfig.DEFAULT_BLUR_ROUNDS
            )
        }
    }

    fun updateSummary(radius: Int, rounds: Int) {
        summary = if (usesLiquidGlassEffect) {
            context.getString(R.string.summary_blur_intensity_value_thickness, radius, rounds)
        } else {
            context.getString(R.string.summary_blur_intensity_value_radius_only, radius)
        }
    }
}
