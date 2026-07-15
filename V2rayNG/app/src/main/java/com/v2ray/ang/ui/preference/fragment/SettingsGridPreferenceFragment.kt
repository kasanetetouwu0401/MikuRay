package com.v2ray.ang.ui.preference.fragment

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.ui.MainActivity

/**
 * Tile-grid preference fragment (dipromote dari inner-class
 * `SettingsActivity.SettingsFragment` lama). Click handler navigasi via
 * `MainActivity.displayPreferenceFragment(...)` — back stack otomatis by activity.
 */
class SettingsGridPreferenceFragment : androidx.preference.PreferenceFragmentCompat() {

    private val navigateUiSettings by lazy { findPreference<androidx.preference.Preference>(AppConfig.PREF_NAVIGATE_UI_SETTINGS) }
    private val navigateVpnSettings by lazy { findPreference<androidx.preference.Preference>(AppConfig.PREF_NAVIGATE_VPN_SETTINGS) }
    private val navigateCoreSettings by lazy { findPreference<androidx.preference.Preference>(AppConfig.PREF_NAVIGATE_CORE_SETTINGS) }
    private val navigateMuxSettings by lazy { findPreference<androidx.preference.Preference>(AppConfig.PREF_NAVIGATE_MUX_SETTINGS) }
    private val navigateFragmentSettings by lazy { findPreference<androidx.preference.Preference>(AppConfig.PREF_NAVIGATE_FRAGMENT_SETTINGS) }
    private val navigateAdvancedSettings by lazy { findPreference<androidx.preference.Preference>(AppConfig.PREF_NAVIGATE_ADVANCED_SETTINGS) }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView {
        val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        val px12 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
        val px4  = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f,  resources.displayMetrics).toInt()
        recyclerView.setPadding(px12, px4, px12, px4)
        recyclerView.clipToPadding = false
        return recyclerView
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
        addPreferencesFromResource(R.xml.pref_settings)

        val mainActivity = activity as? MainActivity ?: return

        navigateUiSettings?.setOnPreferenceClickListener {
            mainActivity.displayPreferenceFragment(UiSettingsFragment())
            true
        }
        navigateVpnSettings?.setOnPreferenceClickListener {
            mainActivity.displayPreferenceFragment(VpnSettingsFragment())
            true
        }
        navigateCoreSettings?.setOnPreferenceClickListener {
            mainActivity.displayPreferenceFragment(CoreSettingsFragment())
            true
        }
        navigateMuxSettings?.setOnPreferenceClickListener {
            mainActivity.displayPreferenceFragment(MuxSettingsFragment())
            true
        }
        navigateFragmentSettings?.setOnPreferenceClickListener {
            mainActivity.displayPreferenceFragment(FragmentSettingsFragment())
            true
        }
        navigateAdvancedSettings?.setOnPreferenceClickListener {
            mainActivity.displayPreferenceFragment(AdvancedSettingsFragment())
            true
        }
    }
}
