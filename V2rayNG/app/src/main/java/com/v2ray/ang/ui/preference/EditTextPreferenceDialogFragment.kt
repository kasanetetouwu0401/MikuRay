package com.v2ray.ang.ui.preference

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogPreferenceEdittextBinding
import com.v2ray.ang.util.WindowBlurUtils

/**
 * Custom EditTextPreference dialog with a centered icon header, matching the
 * app's delete-confirmation dialog style (icon -> title -> input card -> buttons).
 */
class EditTextPreferenceDialogFragment : PreferenceDialogFragmentCompat() {

    private var binding: DialogPreferenceEdittextBinding? = null
    private var editTextValue: CharSequence? = null

    private val editTextPreference: EditTextPreference?
        get() = preference as? EditTextPreference

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        binding = DialogPreferenceEdittextBinding.inflate(layoutInflater)
        val b = binding!!

        val pref = editTextPreference
        editTextValue = savedInstanceState?.getCharSequence(SAVE_STATE_TEXT)
            ?: pref?.text

        b.dialogTitle.text = pref?.dialogTitle ?: pref?.title
        val message = pref?.dialogMessage
        if (!message.isNullOrEmpty()) {
            b.dialogMessage.text = message
            b.dialogMessage.visibility = View.VISIBLE
        } else {
            b.dialogMessage.visibility = View.GONE
        }

        val dialogIconDrawable = pref?.icon ?: pref?.dialogIcon
        if (dialogIconDrawable != null) {
            b.dialogIcon.setImageDrawable(dialogIconDrawable)
        } else {
            b.dialogIcon.setImageResource(R.drawable.ic_edit_24dp)
        }

        b.editText.setText(editTextValue)
        b.editText.setSelection(b.editText.text?.length ?: 0)
        
        if (pref != null) {
            try {
                val method = EditTextPreference::class.java.getDeclaredMethod("getOnBindEditTextListener")
                method.isAccessible = true
                val listener = method.invoke(pref) as? EditTextPreference.OnBindEditTextListener
                listener?.onBindEditText(b.editText)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (b.editText.inputType == InputType.TYPE_CLASS_TEXT) {
            b.editText.setSingleLine()
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(b.root)
            .setCancelable(true)
            .create()

        b.positiveButton.setOnClickListener {
            editTextValue = b.editText.text
            onDialogClosed(true)
            dialog.dismiss()
        }
        b.negativeButton.setOnClickListener {
            onDialogClosed(false)
            dialog.dismiss()
        }

        WindowBlurUtils.applyWindowBlur(dialog.window)
        b.editText.requestFocus()
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        return dialog
    }

    override fun needInputMethod(): Boolean = true

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val newValue = editTextValue?.toString() ?: ""
            editTextPreference?.let {
                if (it.callChangeListener(newValue)) {
                    it.text = newValue
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(SAVE_STATE_TEXT, editTextValue)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val SAVE_STATE_TEXT = "EditTextPreferenceDialogFragment.text"

        fun newInstance(key: String): EditTextPreferenceDialogFragment {
            val fragment = EditTextPreferenceDialogFragment()
            val b = Bundle(1)
            b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}
