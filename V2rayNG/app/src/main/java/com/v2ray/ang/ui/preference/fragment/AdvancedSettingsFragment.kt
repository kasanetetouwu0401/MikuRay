package com.v2ray.ang.ui.preference.fragment

import androidx.preference.PreferenceFragmentCompat
import com.v2ray.ang.R
import com.v2ray.ang.ui.PreferenceToolbarHostFragment

class AdvancedSettingsFragment : PreferenceToolbarHostFragment() {
    override fun getTitle(): CharSequence = getString(R.string.title_advanced)
    override fun createPreferenceFragment(): PreferenceFragmentCompat = AdvancedPreferenceFragment()
}
