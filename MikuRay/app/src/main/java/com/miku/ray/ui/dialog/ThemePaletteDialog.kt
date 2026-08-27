package com.miku.ray.ui.dialog

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.util.ThemeManager
import com.miku.ray.util.ThemePaletteStore
import com.miku.ray.util.WindowBlurUtils

/**
 * A View-based adaptation of ImageToolbox's reusable color tuple library.
 * MikuRay stores only seed colors, then Material Dynamic Colors derives the full scheme.
 */
class ThemePaletteDialog : DialogFragment() {

    companion object {
        const val TAG = "ThemePaletteDialog"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            onApplied: () -> Unit = {},
        ) {
            ThemePaletteDialog().apply {
                onAppliedCallback = onApplied
            }.show(fragmentManager, TAG)
        }
    }

    var onAppliedCallback: () -> Unit = {}

    override fun onStart() {
        super.onStart()
        WindowBlurUtils.applyWindowBlur(dialog?.window)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_theme_palette, null)

        val createButton = view.findViewById<MaterialButton>(R.id.button_theme_palette_create)
        createButton.setOnClickListener {
            val currentColor = currentThemeSeed()
            dismiss()
            CustomColorPickerDialog.show(
                fragmentManager = parentFragmentManager,
                currentColor = currentColor,
                saveToPalette = true,
                onApplied = onAppliedCallback,
            )
        }

        renderPalette(view)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_theme_palette)
            .setIcon(RemixR.drawable.rmx_palette_line)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun renderPalette(view: View) {
        val selectedColor = currentThemeSeed()
        addColorButtons(
            grid = view.findViewById(R.id.grid_theme_palette_suggested),
            colors = ThemePaletteStore.suggestedColors,
            selectedColor = selectedColor,
            removable = false,
        )

        val savedColors = ThemePaletteStore.savedColors()
        view.findViewById<TextView>(R.id.tv_theme_palette_saved_empty).visibility =
            if (savedColors.isEmpty()) View.VISIBLE else View.GONE
        addColorButtons(
            grid = view.findViewById(R.id.grid_theme_palette_saved),
            colors = savedColors,
            selectedColor = selectedColor,
            removable = true,
            onPaletteChanged = { renderPalette(view) },
        )

        val recentColors = ThemePaletteStore.recentColors()
        view.findViewById<TextView>(R.id.tv_theme_palette_recent_empty).visibility =
            if (recentColors.isEmpty()) View.VISIBLE else View.GONE
        addColorButtons(
            grid = view.findViewById(R.id.grid_theme_palette_recent),
            colors = recentColors,
            selectedColor = selectedColor,
            removable = false,
        )
    }

    private fun addColorButtons(
        grid: GridLayout,
        colors: List<Int>,
        selectedColor: Int,
        removable: Boolean,
        onPaletteChanged: (() -> Unit)? = null,
    ) {
        grid.removeAllViews()
        colors.forEach { color ->
            val button = MaterialButton(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = dp(56)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                insetTop = 0
                insetBottom = 0
                insetLeft = 0
                insetRight = 0
                cornerRadius = dp(18)
                backgroundTintList = ColorStateList.valueOf(color)
                contentDescription = getString(
                    R.string.theme_palette_color_description,
                    ThemePaletteStore.colorToHex(color)
                )
                isAllCaps = false
                if (color == selectedColor) {
                    strokeWidth = dp(3)
                    strokeColor = ColorStateList.valueOf(
                        if (isLight(color)) Color.BLACK else Color.WHITE
                    )
                }
                setOnClickListener {
                    activity?.let { activity ->
                        ThemeManager.saveCustomColor(activity, color)
                        onAppliedCallback()
                        dismiss()
                    }
                }
                if (removable) {
                    setOnLongClickListener {
                        confirmRemoveColor(color, onPaletteChanged)
                        true
                    }
                }
            }
            grid.addView(button)
        }
    }

    private fun confirmRemoveColor(color: Int, onPaletteChanged: (() -> Unit)?) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.theme_palette_remove_title)
            .setMessage(
                getString(
                    R.string.theme_palette_remove_message,
                    ThemePaletteStore.colorToHex(color)
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.theme_palette_remove_action) { _, _ ->
                ThemePaletteStore.removeSavedColor(color)
                onPaletteChanged?.invoke()
            }
            .show()
    }

    private fun currentThemeSeed(): Int {
        val savedColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
        return if (savedColor != 0) {
            savedColor
        } else {
            ContextCompat.getColor(requireContext(), R.color.teal_primary)
        }
    }

    private fun isLight(color: Int): Boolean {
        val red = Color.red(color) / 255f
        val green = Color.green(color) / 255f
        val blue = Color.blue(color) / 255f
        return (0.2126f * red) + (0.7152f * green) + (0.0722f * blue) > 0.5f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
