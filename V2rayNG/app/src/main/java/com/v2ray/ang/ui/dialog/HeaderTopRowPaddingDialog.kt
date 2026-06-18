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

class HeaderTopRowPaddingDialog @JvmOverloads constructor(
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

        val saved = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_HEADER_TOP_ROW_PADDING,
            AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT
        )
        val current = saved.coerceIn(
            AppConfig.HEADER_TOP_ROW_PADDING_MIN,
            AppConfig.HEADER_TOP_ROW_PADDING_MAX
        )

        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_header_top_row_padding_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_header_top_row_padding)
        val iconView = dialogView.findViewById<android.widget.ImageView>(R.id.dialog_icon)
        val titleView = dialogView.findViewById<android.widget.TextView>(R.id.dialog_title)
        val positiveButton = dialogView.findViewById<MaterialButton>(R.id.positive_button)
        val negativeButton = dialogView.findViewById<MaterialButton>(R.id.negative_button)
        val neutralButton = dialogView.findViewById<MaterialButton>(R.id.neutral_button)

        icon?.let { iconView.setImageDrawable(it) }
        titleView.text = title

        slider.value = current.toFloat()

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        positiveButton.setOnClickListener {
            val newPadding = slider.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_HEADER_TOP_ROW_PADDING, newPadding)
            summary = context.getString(
                R.string.pref_header_top_row_padding_summary_value, newPadding
            )
            val intent = android.content.Intent(
                AppConfig.BROADCAST_ACTION_HEADER_TOP_ROW_PADDING_CHANGED
            )
            activity.sendBroadcast(intent)
            dialog.dismiss()
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()
        }

        neutralButton.setOnClickListener {
            slider.value = AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT.toFloat()
        }

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        updateSummary()
    }

    private fun updateSummary() {
        val p = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_HEADER_TOP_ROW_PADDING,
            AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT
        )
        summary = context.getString(R.string.pref_header_top_row_padding_summary_value, p)
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }
}
