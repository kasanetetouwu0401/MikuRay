package com.v2ray.ang.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.preference.Preference
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.DPIController
import com.v2ray.ang.util.WindowBlurUtils

class DpiSliderDialog @JvmOverloads constructor(
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
        val activity = context.findActivity() ?: return

        val systemDpi = Resources.getSystem().displayMetrics.densityDpi
        
        val savedDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val currentDpi = if (savedDpi > 0) savedDpi else systemDpi

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_dpi_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_dpi)
        val iconView = dialogView.findViewById<android.widget.ImageView>(R.id.dialog_icon)
        val titleView = dialogView.findViewById<android.widget.TextView>(R.id.dialog_title)
        val positiveButton = dialogView.findViewById<MaterialButton>(R.id.positive_button)
        val negativeButton = dialogView.findViewById<MaterialButton>(R.id.negative_button)
        val neutralButton = dialogView.findViewById<MaterialButton>(R.id.neutral_button)

        icon?.let { iconView.setImageDrawable(it) }
        titleView.text = title

        slider.value = currentDpi.toFloat().coerceIn(160f, 640f)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        positiveButton.setOnClickListener {
            val clamped = slider.value.toInt()
            val valueToSave = if (clamped == systemDpi) 0 else clamped

            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_DPI, valueToSave)
            summary = if (valueToSave == 0) systemDpi.toString() else clamped.toString()

            DPIController.applyDpi(activity.applicationContext, clamped)

            dialog.dismiss()
            activity.recreate()
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()
        }

        neutralButton.setOnClickListener {
            slider.value = systemDpi.toFloat().coerceIn(160f, 640f)
        }

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()
    }
}
