package com.miku.ray.ui.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.miku.ray.R
import com.miku.ray.util.ThemeManager
import com.miku.ray.util.WindowBlurUtils
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.miku.ray.remixicon.R as RemixR
import com.google.android.material.color.utilities.Hct
import java.util.Locale

class CustomColorPickerDialog : DialogFragment() {

    companion object {
        const val TAG = "CustomColorPickerDialog"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            currentColor: Int = Color.parseColor("#AE3A13"),
            onApplied: () -> Unit = {},
        ) {
            CustomColorPickerDialog().apply {
                arguments = Bundle().apply { putInt("current_color", currentColor) }
                onAppliedCallback = onApplied
            }.show(fragmentManager, TAG)
        }
    }

    private enum class InputMode { HEX, HCT }

    var onAppliedCallback: () -> Unit = {}
    private var selectedColor: Int = Color.parseColor("#AE3A13")
    private var inputMode = InputMode.HEX
    private var updatingFields = false

    override fun onStart() {
        super.onStart()
        WindowBlurUtils.applyWindowBlur(dialog?.window)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val initialColor = arguments?.getInt("current_color") ?: selectedColor
        selectedColor = initialColor

        val view = layoutInflater.inflate(R.layout.dialog_custom_color_picker, null)
        val colorPickerView = view.findViewById<ColorPickerView>(R.id.color_picker_view)
        val hexLayout = view.findViewById<TextInputLayout>(R.id.source_hex_layout)
        val hueLayout = view.findViewById<TextInputLayout>(R.id.source_hue_layout)
        val chromaLayout = view.findViewById<TextInputLayout>(R.id.source_chroma_layout)
        val toneLayout = view.findViewById<TextInputLayout>(R.id.source_tone_layout)
        val hexInput = view.findViewById<TextInputEditText>(R.id.source_hex_input)
        val hueInput = view.findViewById<TextInputEditText>(R.id.source_hue_input)
        val chromaInput = view.findViewById<TextInputEditText>(R.id.source_chroma_input)
        val toneInput = view.findViewById<TextInputEditText>(R.id.source_tone_input)

        fun clearErrors() {
            hexLayout.error = null
            hueLayout.error = null
            chromaLayout.error = null
            toneLayout.error = null
        }

        fun setInputText(input: TextInputEditText, value: String) {
            input.setText(value)
            input.setSelection(input.text?.length ?: 0)
        }

        fun updateFieldsFromColor(color: Int) {
            val hct = Hct.fromInt(color)
            updatingFields = true
            setInputText(hexInput, String.format(Locale.US, "#%06X", color and 0xFFFFFF))
            setInputText(hueInput, String.format(Locale.US, "%.1f", hct.hue))
            setInputText(chromaInput, String.format(Locale.US, "%.1f", hct.chroma))
            setInputText(toneInput, String.format(Locale.US, "%.1f", hct.tone))
            updatingFields = false
        }

        fun parseNumber(input: TextInputEditText, layout: TextInputLayout, min: Double, max: Double): Double? {
            val value = input.text?.toString()?.trim()?.toDoubleOrNull()
            if (value == null || value !in min..max) {
                layout.error = getString(R.string.pref_custom_source_invalid_range, min, max)
                return null
            }
            return value
        }

        fun applyInputFields(): Boolean {
            clearErrors()
            val nextColor = if (inputMode == InputMode.HEX) {
                try {
                    Color.parseColor(hexInput.text?.toString()?.trim().orEmpty())
                } catch (_: IllegalArgumentException) {
                    hexLayout.error = getString(R.string.pref_custom_source_invalid_hex)
                    return false
                }
            } else {
                val hue = parseNumber(hueInput, hueLayout, 0.0, 360.0) ?: return false
                val chroma = parseNumber(chromaInput, chromaLayout, 0.0, 150.0) ?: return false
                val tone = parseNumber(toneInput, toneLayout, 0.0, 100.0) ?: return false
                Hct.from(hue, chroma, tone).toInt()
            }

            selectedColor = Color.rgb(Color.red(nextColor), Color.green(nextColor), Color.blue(nextColor))
            updateFieldsFromColor(selectedColor)
            colorPickerView.post { colorPickerView.selectByHsvColor(selectedColor) }
            return true
        }

        val inputWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!updatingFields) inputMode = InputMode.HCT
            }
        }
        val hexWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!updatingFields) inputMode = InputMode.HEX
            }
        }
        hueInput.addTextChangedListener(inputWatcher)
        chromaInput.addTextChangedListener(inputWatcher)
        toneInput.addTextChangedListener(inputWatcher)
        hexInput.addTextChangedListener(hexWatcher)

        updateFieldsFromColor(initialColor)
        colorPickerView.post { colorPickerView.selectByHsvColor(initialColor) }
        colorPickerView.setColorListener(ColorEnvelopeListener { envelope, _ ->
            selectedColor = Color.rgb(
                Color.red(envelope.color),
                Color.green(envelope.color),
                Color.blue(envelope.color)
            )
            inputMode = InputMode.HEX
            updateFieldsFromColor(selectedColor)
        })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pref_custom_color_title)
            .setIcon(RemixR.drawable.rmx_palette_line)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.pref_custom_color_reset) { _, _ ->
                activity?.let { ThemeManager.clearCustomColor(it) }
                onAppliedCallback()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (applyInputFields()) {
                    activity?.let { ThemeManager.saveCustomColor(it, selectedColor) }
                    onAppliedCallback()
                    dialog.dismiss()
                }
            }
        }
        return dialog
    }
}
