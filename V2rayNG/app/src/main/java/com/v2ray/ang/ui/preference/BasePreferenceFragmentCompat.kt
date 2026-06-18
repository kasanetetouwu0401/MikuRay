package com.v2ray.ang.ui.preference

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

/**
 * Base class for all settings screens in the app.
 *
 * Overrides [onDisplayPreferenceDialog] so that [EditTextPreference] and
 * [ListPreference] use the app's custom dialog style (centered icon, title,
 * and content card matching [com.v2ray.ang.util.showDeleteConfirmDialog]),
 * instead of the plain AndroidX default dialogs.
 */
abstract class BasePreferenceFragmentCompat : PreferenceFragmentCompat() {

    override fun onDisplayPreferenceDialog(preference: Preference) {
        // Preferences that already manage their own dialog (sliders, color
        // pickers, tab icon picker, etc.) are left untouched.
        if (parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return
        }

        val dialogFragment = when (preference) {
            is EditTextPreference -> EditTextPreferenceDialogFragment.newInstance(preference.key)
            is ListPreference -> ListPreferenceDialogFragment.newInstance(preference.key)
            else -> null
        }

        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    companion object {
        private const val DIALOG_FRAGMENT_TAG =
            "com.v2ray.ang.ui.preference.BasePreferenceFragmentCompat.DIALOG"
    }
}
