package com.v2ray.ang.ui.preference.fragment

import androidx.preference.PreferenceFragmentCompat
import com.v2ray.ang.R
import com.v2ray.ang.ui.PreferenceToolbarHostFragment

class UiSettingsFragment : PreferenceToolbarHostFragment() {
    override fun getTitle(): CharSequence = getString(R.string.title_ui_settings)
    override fun createPreferenceFragment(): PreferenceFragmentCompat = UiPreferenceFragment()
}
