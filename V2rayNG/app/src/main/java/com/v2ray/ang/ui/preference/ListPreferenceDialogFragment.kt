package com.v2ray.ang.ui.preference

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.preference.ListPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogPreferenceListBinding
import com.v2ray.ang.util.WindowBlurUtils

/**
 * Custom ListPreference dialog with a centered icon header, matching the
 * app's delete-confirmation dialog style (icon -> title -> options card -> buttons).
 */
class ListPreferenceDialogFragment : PreferenceDialogFragmentCompat() {

    private var binding: DialogPreferenceListBinding? = null
    private var clickedEntryIndex = -1
    private var entries: Array<CharSequence> = arrayOf()
    private var entryValues: Array<CharSequence> = arrayOf()

    private val listPreference: ListPreference?
        get() = preference as? ListPreference

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        binding = DialogPreferenceListBinding.inflate(LayoutInflater.from(context))
        val b = binding!!

        val pref = listPreference
        entries = pref?.entries?.map { it as CharSequence }?.toTypedArray() ?: arrayOf()
        entryValues = pref?.entryValues?.map { it as CharSequence }?.toTypedArray() ?: arrayOf()
        val restoredIndex = savedInstanceState?.getInt(SAVE_STATE_INDEX, -1) ?: -1
        clickedEntryIndex = if (restoredIndex >= 0) {
            restoredIndex
        } else {
            pref?.value?.let { value -> entryValues.indexOfFirst { it == value } } ?: -1
        }

        b.dialogTitle.text = pref?.dialogTitle ?: pref?.title
        val dialogIconDrawable = pref?.icon ?: pref?.dialogIcon
        if (dialogIconDrawable != null) {
            b.dialogIcon.setImageDrawable(dialogIconDrawable)
        } else {
            b.dialogIcon.setImageResource(R.drawable.ic_list_24dp)
        }

        b.listOptions.removeAllViews()
        entries.forEachIndexed { index, entry ->
            val radioButton = LayoutInflater.from(context)
                .inflate(R.layout.item_dialog_list_radio, b.listOptions, false) as MaterialRadioButton
            radioButton.text = entry
            radioButton.id = android.view.View.generateViewId()
            radioButton.isChecked = index == clickedEntryIndex
            radioButton.setOnClickListener {
                clickedEntryIndex = index
            }
            b.listOptions.addView(radioButton)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(b.root)
            .setCancelable(true)
            .create()

        b.positiveButton.setOnClickListener {
            onDialogClosed(true)
            dialog.dismiss()
        }
        b.negativeButton.setOnClickListener {
            onDialogClosed(false)
            dialog.dismiss()
        }

        WindowBlurUtils.applyWindowBlur(dialog.window)

        return dialog
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult && clickedEntryIndex >= 0 && clickedEntryIndex < entryValues.size) {
            val newValue = entryValues[clickedEntryIndex].toString()
            listPreference?.let {
                if (it.callChangeListener(newValue)) {
                    it.value = newValue
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SAVE_STATE_INDEX, clickedEntryIndex)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val SAVE_STATE_INDEX = "ListPreferenceDialogFragment.index"

        fun newInstance(key: String): ListPreferenceDialogFragment {
            val fragment = ListPreferenceDialogFragment()
            val b = Bundle(1)
            b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}
