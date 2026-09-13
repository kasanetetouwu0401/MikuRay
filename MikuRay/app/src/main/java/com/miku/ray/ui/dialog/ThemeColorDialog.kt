package com.miku.ray.ui.dialog

import com.miku.ray.remixicon.R as RemixR
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.handler.SettingsChangeManager
import kotlinx.coroutines.flow.collect
import com.miku.ray.util.ThemeManager
import com.miku.ray.util.WindowBlurUtils
import com.miku.ray.util.getColorAttr

class ThemeColorDialog : DialogFragment() {

    private var stateJob: kotlinx.coroutines.Job? = null

    companion object {
        const val TAG = "ThemeColorDialog"

        val THEME_KEYS = (1..16).map { it.toString() }

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            onApplied: () -> Unit = {},
        ) {
            val dialog = ThemeColorDialog()
            dialog.onAppliedCallback = onApplied
            dialog.show(fragmentManager, TAG)
        }
    }

    var onAppliedCallback: () -> Unit = {}

    override fun onStart() {
        super.onStart()
        WindowBlurUtils.applyWindowBlur(dialog?.window)
        stateJob?.cancel()
        stateJob = lifecycleScope.launchWhenStarted {
            SettingsChangeManager.uiCustomizationState.collect { state ->
                dialog?.findViewById<android.widget.GridLayout>(R.id.grid_theme_colors)?.let { grid ->
                    grid.children.forEach { item ->
                        val selected = when (item.tag) {
                            "custom" -> state.useCustomColor
                            else -> item.tag == state.appTheme
                        }
                        item.findViewById<ImageView>(R.id.iv_check).visibility =
                            if (selected) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onStop() {
        stateJob?.cancel()
        stateJob = null
        super.onStop()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
        .inflate(R.layout.dialog_theme_color, null)

        val grid = view.findViewById<android.widget.GridLayout>(R.id.grid_theme_colors)

        val state = SettingsChangeManager.uiCustomizationState.value
        val useCustom   = state.useCustomColor
        val savedColor  = state.customColor
        val currentKey  = state.appTheme

        THEME_KEYS.forEach { key ->
            val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_theme_color, grid, false)
            itemView.tag = key

            val circle = itemView.findViewById<ImageView>(R.id.iv_color_circle)
            val check  = itemView.findViewById<ImageView>(R.id.iv_check)

            val isSelected = !useCustom && key == currentKey

            val styleRes = ThemeManager.getThemeStyleRes(key)
            val wrappedContext = ContextThemeWrapper(requireContext(), styleRes)
            val m3PrimaryColor = wrappedContext.getColorAttr("colorPrimary")

            applyCircleDrawable(circle, m3PrimaryColor, isSelected)

            check.visibility = if (isSelected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                activity?.let { act -> ThemeManager.setAndSaveTheme(act, key) }
                onAppliedCallback()
                dismiss()
            }

            grid.addView(itemView)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val customItemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_theme_color, grid, false)
            customItemView.tag = "custom"

            val customCircle = customItemView.findViewById<ImageView>(R.id.iv_color_circle)
            val customIcon   = customItemView.findViewById<ImageView>(R.id.iv_check)

            val isCustomSelected = useCustom && savedColor != 0
            val rawCustomColor   = if (savedColor != 0) savedColor else ContextCompat.getColor(requireContext(), R.color.teal_primary)

            val customOptions = DynamicColorsOptions.Builder()
            .setContentBasedSource(rawCustomColor)
            .build()
            val wrappedCustomContext = DynamicColors.wrapContextIfAvailable(requireContext(), customOptions)
            val m3CustomPrimary = wrappedCustomContext.getColorAttr("colorPrimary")

            applyCircleDrawable(customCircle, m3CustomPrimary, isCustomSelected)

            customIcon.setImageResource(RemixR.drawable.rmx_pencil_line)
            customIcon.visibility = View.VISIBLE

            val lum = calculateLuminance(m3CustomPrimary)
            customIcon.setColorFilter(if (lum > 0.4f) Color.BLACK else Color.WHITE)

            customItemView.setOnClickListener {
                dismiss()
                CustomColorPickerDialog.show(
                    parentFragmentManager,
                    currentColor = rawCustomColor,
                    onApplied   = onAppliedCallback,
                )
            }

            grid.addView(customItemView)
        }

        return MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.pref_theme_color_title)
        .setIcon(RemixR.drawable.rmx_palette_line)
        .setView(view)
        .setNegativeButton(android.R.string.cancel) { _, _ -> dismiss() }
        .create()
    }

    private fun applyCircleDrawable(view: ImageView, @ColorInt color: Int, selected: Boolean) {
        val drawable = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = if (selected) dpToPx(16).toFloat() else dpToPx(100).toFloat()
            setColor(color)
        }
        view.background = drawable
    }

    private fun calculateLuminance(@ColorInt color: Int): Float {
        val r = Color.red(color)   / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color)  / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()
}
