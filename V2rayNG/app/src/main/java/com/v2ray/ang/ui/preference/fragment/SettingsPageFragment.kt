package com.v2ray.ang.ui.preference.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bytehamster.lib.preferencesearch.SearchPreferenceActionView
import com.bytehamster.lib.preferencesearch.SearchPreferenceFragment
import com.google.android.material.button.MaterialButton
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.ToolbarFragment
import com.v2ray.ang.ui.weather.WeatherForecastActivity
import com.v2ray.ang.ui.weather.WeatherHelper
import com.v2ray.ang.util.SearchChipGradientController
import kotlinx.coroutines.launch

/**
 * Halaman utama Settings (homepage of all preferences) yang di-host MainActivity.
 * Layout = `fragment_settings_search.xml` (collapsing toolbar + search bar + weather chip).
 *
 * Click pada tile navigasi akan panggil MainActivity.displayPreferenceFragment(...).
 */
class SettingsPageFragment : ToolbarFragment(R.layout.fragment_settings_search) {

    private lateinit var searchActionView: SearchPreferenceActionView
    private lateinit var btnClearHistory: MaterialButton
    private lateinit var layoutWeatherChip: LinearLayout
    private lateinit var ivWeatherIcon: ImageView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var ivTotalTrafficIcon: ImageView
    private lateinit var tvTotalTraffic: TextView

    private var isColdStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupToolbar(title = getString(R.string.title_settings))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSearchActionView()
        setupWeatherTrafficChip()
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.search_container, SettingsGridPreferenceFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSearchBarChip()
    }

    private fun setupSearchActionView() {
        val root = requireView()
        searchActionView = root.findViewById(R.id.search_action_view)
        btnClearHistory = root.findViewById(R.id.btn_clear_history)
        searchActionView.setActivity(requireActivity())
        searchActionView.getSearchConfiguration().apply {
            setHistoryEnabled(true)
            setBreadcrumbsEnabled(true)
            setFragmentContainerViewId(R.id.search_container)
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
            object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: androidx.fragment.app.FragmentManager, f: androidx.fragment.app.Fragment) {
                    if (f is SearchPreferenceFragment) {
                        btnClearHistory.isVisible = f.hasHistory()
                    }
                }
                override fun onFragmentViewDestroyed(fm: androidx.fragment.app.FragmentManager, f: androidx.fragment.app.Fragment) {
                    if (f is SearchPreferenceFragment) {
                        btnClearHistory.isVisible = false
                    }
                }
            }, true
        )
    }

    private fun currentSearchFragment(): SearchPreferenceFragment? =
        childFragmentManager.fragments.filterIsInstance<SearchPreferenceFragment>().firstOrNull()

    private fun setupWeatherTrafficChip() {
        val v = requireView()
        layoutWeatherChip = v.findViewById(R.id.layout_weather_chip)
        ivWeatherIcon = v.findViewById(R.id.iv_weather_icon)
        tvWeatherTemp = v.findViewById(R.id.tv_weather_temp)
        ivTotalTrafficIcon = v.findViewById(R.id.iv_total_traffic_icon)
        tvTotalTraffic = v.findViewById(R.id.tv_total_traffic)
        layoutWeatherChip.setOnClickListener {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false)) {
                startActivity(Intent(requireContext(), WeatherForecastActivity::class.java))
            }
        }
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
        SearchChipGradientController.applyState(requireContext(), chipViews())
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
        if (totalTraffic == null) { layoutWeatherChip.isVisible = false; return }
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
        viewLifecycleOwner.lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(requireContext(), force = coldStart)
            applyWeatherToChip(weather ?: WeatherHelper.getCachedWeatherStale() ?: return@launch)
        }
        layoutWeatherChip.isVisible = true
        val cached = WeatherHelper.getCachedWeatherStale()
        if (cached != null) applyWeatherToChip(cached)
    }

    private fun applyWeatherToChip(weather: WeatherHelper.WeatherResult) {
        ivWeatherIcon.setImageResource(weather.iconRes)
        tvWeatherTemp.text = weather.getTemperatureString(WeatherHelper.isCelsius())
        ivWeatherIcon.isVisible = true
        tvWeatherTemp.isVisible = true
        layoutWeatherChip.isVisible = true
    }
}
