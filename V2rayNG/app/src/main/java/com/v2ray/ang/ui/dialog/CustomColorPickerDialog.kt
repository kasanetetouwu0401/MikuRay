package com.v2ray.ang.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.v2ray.ang.R
import com.v2ray.ang.util.ThemeManager
import com.v2ray.ang.util.WindowBlurUtils

class CustomColorPickerDialog : DialogFragment() {

    companion object {
        const val TAG = "CustomColorPickerDialog"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            currentColor: Int = Color.parseColor("#6750A4"),
            onApplied: () -> Unit = {},
        ) {
            CustomColorPickerDialog().apply {
                arguments = Bundle().apply { putInt("current_color", currentColor) }
                onAppliedCallback = onApplied
            }.show(fragmentManager, TAG)
        }
    }

    var onAppliedCallback: () -> Unit = {}
    private var selectedColor: Int = Color.parseColor("#6750A4")

    override fun onStart() {
        super.onStart()
        WindowBlurUtils.applyWindowBlur(dialog?.window)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val initialColor = arguments?.getInt("current_color") ?: selectedColor
        selectedColor = initialColor

        val view = layoutInflater.inflate(R.layout.dialog_custom_color_picker, null)

        val colorPickerView = view.findViewById<ColorPickerView>(R.id.color_picker_view)
        val positiveButton = view.findViewById<MaterialButton>(R.id.positive_button)
        val negativeButton = view.findViewById<MaterialButton>(R.id.negative_button)
        val neutralButton = view.findViewById<MaterialButton>(R.id.neutral_button)

        colorPickerView.post {
            colorPickerView.selectByHsvColor(initialColor)
        }

        colorPickerView.setColorListener(ColorEnvelopeListener { envelope, _ ->
            selectedColor = envelope.color
        })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()

        positiveButton.setOnClickListener {
            activity?.let { ThemeManager.saveCustomColor(it, selectedColor) }
            onAppliedCallback()
            dismiss()
        }

        negativeButton.setOnClickListener {
            dismiss()
        }

        neutralButton.setOnClickListener {
            activity?.let { ThemeManager.clearCustomColor(it) }
            onAppliedCallback()
            dismiss()
        }

        return dialog
    }
}
