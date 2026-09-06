package com.miku.ray.ui.dialog

import com.miku.ray.remixicon.R as RemixR
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.WindowBlurUtils
import kotlin.math.abs
import kotlin.math.roundToInt

class DpiSliderDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private enum class ScalePreset(val factor: Float) {
        COMPACT(1.20f),
        DEFAULT(1.00f),
        SPACIOUS(0.80f)
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun presetDpi(systemDpi: Int, preset: ScalePreset): Int {
        if (preset == ScalePreset.DEFAULT) return systemDpi
        return (systemDpi * preset.factor).roundToInt().coerceIn(160, 640)
    }

    private fun detectPreset(currentDpi: Int, systemDpi: Int): ScalePreset {
        // Pick the closest named preset for the toggle highlight (even for custom values)
        val candidates = ScalePreset.entries.map { it to presetDpi(systemDpi, it) }
        return candidates.minBy { abs(it.second - currentDpi) }.first
    }

    private fun isExactPreset(currentDpi: Int, systemDpi: Int, preset: ScalePreset): Boolean {
        return abs(presetDpi(systemDpi, preset) - currentDpi) <= 8
    }

    private fun formatSummary(savedDpi: Int, systemDpi: Int, ctx: Context): String {
        if (savedDpi <= 0) {
            return ctx.getString(R.string.pref_ui_scale_summary_default, systemDpi)
        }
        val preset = detectPreset(savedDpi, systemDpi)
        return if (isExactPreset(savedDpi, systemDpi, preset)) {
            when (preset) {
                ScalePreset.COMPACT -> ctx.getString(R.string.pref_ui_scale_summary_compact, savedDpi)
                ScalePreset.DEFAULT -> ctx.getString(R.string.pref_ui_scale_summary_default, savedDpi)
                ScalePreset.SPACIOUS -> ctx.getString(R.string.pref_ui_scale_summary_spacious, savedDpi)
            }
        } else {
            ctx.getString(R.string.pref_ui_scale_summary_custom, savedDpi)
        }
    }

    fun refreshSummary() {
        val systemDpi = Resources.getSystem().displayMetrics.densityDpi
        val savedDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        summary = formatSummary(savedDpi, systemDpi, context)
    }

    override fun onClick() {
        val activity = context.findActivity() ?: return

        val systemDpi = Resources.getSystem().displayMetrics.densityDpi
        val savedDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val currentDpi = if (savedDpi > 0) savedDpi else systemDpi

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_dpi_slider, null)
        val toggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_ui_scale)
        val btnCompact = dialogView.findViewById<MaterialButton>(R.id.btn_scale_compact)
        val btnDefault = dialogView.findViewById<MaterialButton>(R.id.btn_scale_default)
        val btnSpacious = dialogView.findViewById<MaterialButton>(R.id.btn_scale_spacious)
        val slider = dialogView.findViewById<Slider>(R.id.slider_dpi)
        val valueLabel = dialogView.findViewById<TextView>(R.id.text_dpi_value)

        fun updateValueLabel(dpi: Int) {
            valueLabel.text = context.getString(R.string.pref_ui_scale_dpi_value, dpi)
        }

        fun selectToggleForDpi(dpi: Int) {
            val preset = detectPreset(dpi, systemDpi)
            val buttonId = when (preset) {
                ScalePreset.COMPACT -> R.id.btn_scale_compact
                ScalePreset.DEFAULT -> R.id.btn_scale_default
                ScalePreset.SPACIOUS -> R.id.btn_scale_spacious
            }
            // Avoid recursive listener if already checked
            if (toggleGroup.checkedButtonId != buttonId) {
                toggleGroup.check(buttonId)
            }
        }

        slider.value = currentDpi.toFloat().coerceIn(160f, 640f)
        updateValueLabel(slider.value.toInt())
        selectToggleForDpi(currentDpi)

        var suppressToggle = false

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressToggle) return@addOnButtonCheckedListener
            val targetDpi = when (checkedId) {
                R.id.btn_scale_compact -> presetDpi(systemDpi, ScalePreset.COMPACT)
                R.id.btn_scale_spacious -> presetDpi(systemDpi, ScalePreset.SPACIOUS)
                else -> systemDpi
            }
            slider.value = targetDpi.toFloat().coerceIn(160f, 640f)
            updateValueLabel(targetDpi)
        }

        slider.addOnChangeListener { _, value, fromUser ->
            val dpi = value.toInt()
            updateValueLabel(dpi)
            if (fromUser) {
                suppressToggle = true
                selectToggleForDpi(dpi)
                suppressToggle = false
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_ui_scale_title)
            .setIcon(RemixR.drawable.rmx_smartphone_line)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val clamped = slider.value.toInt().coerceIn(160, 640)
                val valueToSave = if (clamped == systemDpi) 0 else clamped

                MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_DPI, valueToSave)
                summary = formatSummary(valueToSave, systemDpi, context)

                activity.recreate()
            }
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            suppressToggle = true
            toggleGroup.check(R.id.btn_scale_default)
            suppressToggle = false
            slider.value = systemDpi.toFloat().coerceIn(160f, 640f)
            updateValueLabel(systemDpi)

            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_DPI, 0)
            summary = formatSummary(0, systemDpi, context)

            dialog.dismiss()
            activity.recreate()
        }
    }
}
