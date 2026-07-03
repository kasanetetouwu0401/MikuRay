package com.v2ray.ang.ui.preference.activity

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.NonNull
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bytehamster.lib.preferencesearch.SearchPreferenceActionView
import com.bytehamster.lib.preferencesearch.SearchPreferenceFragment
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivitySettingsSearchBinding
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.ui.HelperMikuFragment
import com.v2ray.ang.util.SearchChipGradientController
import com.v2ray.ang.util.WeatherHelper
import com.v2ray.ang.util.showDeleteConfirmDialog
import kotlinx.coroutines.launch

class SettingsHubFragment : HelperMikuFragment<ActivitySettingsSearchBinding>(), SearchPreferenceResultListener {

    private lateinit var searchActionView: SearchPreferenceActionView
    private lateinit var btnClearHistory: com.google.android.material.button.MaterialButton
    private lateinit var layoutWeatherChip: LinearLayout
    private lateinit var ivWeatherIcon: ImageView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var ivTotalTrafficIcon: ImageView
    private lateinit var tvTotalTraffic: TextView

    private var isColdStart = true

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        ActivitySettingsSearchBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar(binding.toolbar, title = getString(R.string.title_settings))

        setupSearchActionView()
        setupWeatherTrafficChip()
        setupMenu()

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsListFragment())
                .commit()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val searchFragment = childFragmentManager.fragments.find {
                    it.javaClass.name.contains("SearchPreferenceFragment")
                }

                if (searchFragment != null && searchFragment.isVisible) {
                    searchActionView.cancelSearch()
                } else {
                    isEnabled = false
                    closeThisFragment()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refreshSearchBarChip()
    }

    private fun setupSearchActionView() {
        searchActionView = binding.searchActionView
        btnClearHistory = binding.btnClearHistory
        searchActionView.setFragment(this)
        searchActionView.getSearchConfiguration().apply {
            setHistoryEnabled(true)
            setBreadcrumbsEnabled(true)
            setFragmentContainerViewId(R.id.settings_container)
            index(R.xml.pref_ui_settings).addBreadcrumb(R.string.title_ui_settings)
            index(R.xml.pref_vpn_settings).addBreadcrumb(R.string.title_vpn_settings)
            index(R.xml.pref_core_settings).addBreadcrumb(R.string.title_core_settings)
            index(R.xml.pref_mux_settings).addBreadcrumb(R.string.title_mux_settings)
            index(R.xml.pref_fragment_settings).addBreadcrumb(R.string.title_fragment_settings)
            index(R.xml.pref_advanced_settings).addBreadcrumb(R.string.title_advanced)
        }

        btnClearHistory.setOnClickListener {
            currentSearchFragment()?.clearHistory()
            btnClearHistory.isVisible = false
        }

        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    if (f is SearchPreferenceFragment) {
                        btnClearHistory.isVisible = f.hasHistory()
                    }
                }

                override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                    if (f is SearchPreferenceFragment) {
                        btnClearHistory.isVisible = false
                    }
                }
            },
            true
        )
    }

    private fun currentSearchFragment(): SearchPreferenceFragment? =
        childFragmentManager.fragments.filterIsInstance<SearchPreferenceFragment>().firstOrNull()

    private fun setupWeatherTrafficChip() {
        layoutWeatherChip = binding.layoutWeatherChip
        ivWeatherIcon = binding.ivWeatherIcon
        tvWeatherTemp = binding.tvWeatherTemp
        ivTotalTrafficIcon = binding.ivTotalTrafficIcon
        tvTotalTraffic = binding.tvTotalTraffic
    }

    private fun chipViews() = SearchChipGradientController.ChipViews(
        layoutWeatherChip = layoutWeatherChip,
        ivWeatherIcon = ivWeatherIcon,
        tvWeatherTemp = tvWeatherTemp,
        ivTotalTrafficIcon = ivTotalTrafficIcon,
        tvTotalTraffic = tvTotalTraffic
    )

    private fun weatherLocationReady(): Boolean =
        WeatherHelper.hasCustomLocation() || WeatherHelper.hasLocationPermission(requireContext())

    private fun refreshSearchBarChip() {
        val weatherEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)
        val totalTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_TOTAL_TRAFFIC_CHIP, false)

        SearchChipGradientController.applyState(requireActivity() as androidx.appcompat.app.AppCompatActivity, chipViews())

        when {
            weatherEnabled -> {
                hideTotalTrafficChip()
                refreshWeatherChip()
            }
            totalTrafficEnabled -> {
                hideWeatherChipViews()
                refreshTotalTrafficChip()
            }
            else -> {
                layoutWeatherChip.isVisible = false
            }
        }
    }

    private fun hideWeatherChipViews() {
        ivWeatherIcon.isVisible = false
        tvWeatherTemp.isVisible = false
    }

    private fun hideTotalTrafficChip() {
        ivTotalTrafficIcon.isVisible = false
        tvTotalTraffic.isVisible = false
    }

    private fun refreshTotalTrafficChip() {
        val totalTraffic = MmkvManager.getTotalTrafficString()
        if (totalTraffic == null) {
            layoutWeatherChip.isVisible = false
            return
        }
        tvTotalTraffic.text = totalTraffic
        ivTotalTrafficIcon.isVisible = true
        tvTotalTraffic.isVisible = true
        layoutWeatherChip.isVisible = true
    }

    private fun refreshWeatherChip() {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) {
            layoutWeatherChip.isVisible = false
            return
        }
        val coldStart = isColdStart.also { isColdStart = false }
        if (weatherLocationReady()) {
            if (coldStart) forceRefreshWeatherChip() else loadWeatherChip()
        } else {
            checkAndRequestPermission(PermissionType.LOCATION) {
                if (coldStart) forceRefreshWeatherChip() else loadWeatherChip()
            }
        }
    }

    private fun forceRefreshWeatherChip() {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) return

        if (!weatherLocationReady()) {
            checkAndRequestPermission(PermissionType.LOCATION) {
                forceRefreshWeatherChip()
            }
            return
        }

        val cached = WeatherHelper.getCachedWeatherStale()
        layoutWeatherChip.isVisible = true
        if (cached != null) {
            applyWeatherToChip(cached)
        } else {
            ivWeatherIcon.setImageResource(WeatherHelper.iconResForEmoji(null))
            ivWeatherIcon.isVisible = true
            tvWeatherTemp.text = getString(R.string.weather_loading)
            tvWeatherTemp.isVisible = true
        }

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(requireContext(), force = true)
            if (weather == null) {
                if (cached == null) layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun loadWeatherChip() {
        layoutWeatherChip.isVisible = true

        val fresh = WeatherHelper.getCachedWeather()
        val stale = fresh ?: WeatherHelper.getCachedWeatherStale()

        if (stale != null) {
            applyWeatherToChip(stale)
        } else {
            ivWeatherIcon.setImageResource(WeatherHelper.iconResForEmoji(null))
            ivWeatherIcon.isVisible = true
            tvWeatherTemp.text = getString(R.string.weather_loading)
            tvWeatherTemp.isVisible = true
        }

        if (fresh != null) return

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(requireContext())
            if (weather == null) {
                if (stale == null) layoutWeatherChip.isVisible = false
                return@launch
            }
            applyWeatherToChip(weather)
        }
    }

    private fun applyWeatherToChip(weather: WeatherHelper.WeatherResult) {
        ivWeatherIcon.setImageResource(WeatherHelper.iconResForEmoji(weather.emoji))
        tvWeatherTemp.text = weather.getTemperatureString(WeatherHelper.isCelsius())
        ivWeatherIcon.isVisible = true
        tvWeatherTemp.isVisible = true
        layoutWeatherChip.isVisible = true
    }

    private fun setupMenu() {
        binding.toolbar.inflateMenu(R.menu.menu_settings)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reset_settings -> {
                    showDeleteConfirmDialog(
                        context = requireContext(),
                        titleRes = R.string.dialog_reset_settings_title,
                        messageRes = R.string.dialog_reset_settings_message,
                        iconRes = R.drawable.ic_restore_24dp,
                        positiveTextRes = R.string.dialog_reset_settings_confirm,
                    ) {
                        SettingsManager.resetAllSettings(requireContext().applicationContext)
                        requireContext().toastSuccess(R.string.reset_settings_success)
                        requireActivity().recreate()
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onSearchResultClicked(@NonNull result: SearchPreferenceResult) {
        searchActionView.cancelSearch()

        val targetActivity: Class<*>? = when (result.resourceFile) {
            R.xml.pref_ui_settings       -> UiSettingsActivity::class.java
            R.xml.pref_vpn_settings      -> VpnSettingsActivity::class.java
            R.xml.pref_core_settings     -> CoreSettingsActivity::class.java
            R.xml.pref_mux_settings      -> MuxSettingsActivity::class.java
            R.xml.pref_fragment_settings -> FragmentSettingsActivity::class.java
            R.xml.pref_advanced_settings -> AdvancedSettingsActivity::class.java
            else                         -> null
        }

        if (targetActivity != null) {
            val intent = Intent(requireContext(), targetActivity).apply {
                putExtra(AppConfig.EXTRA_HIGHLIGHT_KEY, result.key)
            }

            val options = ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                R.anim.fade_in,
                R.anim.fade_out
            )

            startActivity(intent, options.toBundle())
        }
    }

    class SettingsListFragment : PreferenceFragmentCompat() {

        private val navigateUiSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_UI_SETTINGS) }
        private val navigateVpnSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_VPN_SETTINGS) }
        private val navigateCoreSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_CORE_SETTINGS) }
        private val navigateMuxSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_MUX_SETTINGS) }
        private val navigateFragmentSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_FRAGMENT_SETTINGS) }
        private val navigateAdvancedSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_ADVANCED_SETTINGS) }

        override fun onCreateRecyclerView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            savedInstanceState: Bundle?
        ): RecyclerView {
            val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
            recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

            val paddingHorizontalPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                resources.displayMetrics
            ).toInt()

            val paddingVerticalPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                resources.displayMetrics
            ).toInt()

            recyclerView.setPadding(
                paddingHorizontalPx,
                paddingVerticalPx,
                paddingHorizontalPx,
                paddingVerticalPx
            )

            recyclerView.clipToPadding = false

            return recyclerView
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_settings)

            navigateUiSettings?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), UiSettingsActivity::class.java))
                true
            }

            navigateVpnSettings?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), VpnSettingsActivity::class.java))
                true
            }

            navigateCoreSettings?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), CoreSettingsActivity::class.java))
                true
            }

            navigateMuxSettings?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), MuxSettingsActivity::class.java))
                true
            }

            navigateFragmentSettings?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), FragmentSettingsActivity::class.java))
                true
            }

            navigateAdvancedSettings?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), AdvancedSettingsActivity::class.java))
                true
            }
        }
    }
}
