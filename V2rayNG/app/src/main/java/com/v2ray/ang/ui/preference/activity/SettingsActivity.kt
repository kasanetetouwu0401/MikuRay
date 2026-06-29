package com.v2ray.ang.ui.preference.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.NonNull
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreferenceFragment
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MmkvPreferenceDataStore
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.util.showDeleteConfirmDialog

class SettingsActivity : BaseActivity(), SearchPreferenceResultListener {

    private lateinit var appBar: AppBarLayout
    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var searchBarCard: MaterialCardView
    private lateinit var searchEditText: EditText
    private lateinit var searchClear: ImageView
    private lateinit var searchBack: ImageView

    private var activeSearchFragment: SearchPreferenceFragment? = null

    private val searchConfiguration by lazy {
        SearchConfiguration(this).apply {
            setSearchBarEnabled(false) // search bar ada di toolbar kita sendiri
            setBreadcrumbsEnabled(true)
            setHistoryEnabled(true)
            index(R.xml.pref_ui_settings).addBreadcrumb(getString(R.string.title_ui_settings))
            index(R.xml.pref_vpn_settings).addBreadcrumb(getString(R.string.title_vpn_settings))
            index(R.xml.pref_core_settings).addBreadcrumb(getString(R.string.title_core_settings))
            index(R.xml.pref_mux_settings).addBreadcrumb(getString(R.string.title_mux_settings))
            index(R.xml.pref_fragment_settings).addBreadcrumb(getString(R.string.title_fragment_settings))
            index(R.xml.pref_advanced_settings).addBreadcrumb(getString(R.string.title_advanced))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_search)

        appBar = findViewById(R.id.app_bar)
        collapsingToolbar = findViewById(R.id.collapsing_toolbar)
        toolbar = findViewById(R.id.toolbar)
        searchBarCard = findViewById(R.id.search_bar_card)
        searchEditText = findViewById(R.id.search_edit_text)
        searchClear = findViewById(R.id.search_clear)
        searchBack = findViewById(R.id.search_back)

        val rootView = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top    = maxOf(systemBars.top,    displayCutout.top),
                bottom = maxOf(systemBars.bottom, displayCutout.bottom),
                left   = maxOf(systemBars.left,   displayCutout.left),
                right  = maxOf(systemBars.right,  displayCutout.right)
            )
            insets
        }

        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        setupSearchBar()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (searchBarCard.visibility == View.VISIBLE) {
                    closeSearch()
                    return
                }
                val searchFragment = supportFragmentManager.fragments.find {
                    it.javaClass.name.contains("SearchPreferenceFragment")
                }
                if (searchFragment != null && searchFragment.isVisible) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupSearchBar() {
        searchBack.setOnClickListener { closeSearch() }

        searchClear.setOnClickListener {
            searchEditText.setText("")
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                searchClear.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                activeSearchFragment?.setSearchTerm(text)
            }
        })

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else false
        }
    }

    private fun openSearch() {
        // Sembunyikan collapsing toolbar, munculkan search bar
        collapsingToolbar.visibility = View.GONE
        searchBarCard.visibility = View.VISIBLE

        // Show search fragment (tanpa search bar bawaannya)
        activeSearchFragment = searchConfiguration.showSearchFragment() as SearchPreferenceFragment
        activeSearchFragment?.setHistoryClickListener { entry ->
            searchEditText.setText(entry)
            searchEditText.setSelection(entry.length)
        }

        // Focus dan buka keyboard
        searchEditText.post {
            searchEditText.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearch() {
        hideKeyboard()

        // Kembalikan toolbar normal
        searchBarCard.visibility = View.GONE
        collapsingToolbar.visibility = View.VISIBLE
        searchEditText.setText("")

        // Tutup search fragment
        activeSearchFragment?.let { fragment ->
            if (fragment.isAdded) {
                supportFragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitAllowingStateLoss()
                supportFragmentManager.popBackStack(
                    SearchPreferenceFragment.TAG,
                    androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
            }
        }
        activeSearchFragment = null
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_search) {
            openSearch()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSearchResultClicked(@NonNull result: SearchPreferenceResult) {
        closeSearch()

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
            startActivity(Intent(this, targetActivity).apply {
                putExtra(AppConfig.EXTRA_HIGHLIGHT_KEY, result.key)
            })
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val navigateUiSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_UI_SETTINGS) }
        private val navigateVpnSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_VPN_SETTINGS) }
        private val navigateCoreSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_CORE_SETTINGS) }
        private val navigateMuxSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_MUX_SETTINGS) }
        private val navigateFragmentSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_FRAGMENT_SETTINGS) }
        private val navigateAdvancedSettings by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_ADVANCED_SETTINGS) }
        private val resetAllSettings by lazy { findPreference<Preference>(AppConfig.PREF_RESET_ALL_SETTINGS) }

        override fun onCreateRecyclerView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            savedInstanceState: Bundle?
        ): RecyclerView {
            val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
            recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

            val paddingHorizontalPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics
            ).toInt()

            val paddingVerticalPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics
            ).toInt()

            recyclerView.setPadding(paddingHorizontalPx, paddingVerticalPx, paddingHorizontalPx, paddingVerticalPx)
            recyclerView.clipToPadding = false

            return recyclerView
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_settings)

            navigateUiSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), UiSettingsActivity::class.java))
                true
            }
            navigateVpnSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), VpnSettingsActivity::class.java))
                true
            }
            navigateCoreSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), CoreSettingsActivity::class.java))
                true
            }
            navigateMuxSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), MuxSettingsActivity::class.java))
                true
            }
            navigateFragmentSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), FragmentSettingsActivity::class.java))
                true
            }
            navigateAdvancedSettings?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), AdvancedSettingsActivity::class.java))
                true
            }
            resetAllSettings?.setOnPreferenceClickListener {
                showDeleteConfirmDialog(
                    context = requireContext(),
                    titleRes = R.string.dialog_reset_settings_title,
                    messageRes = R.string.dialog_reset_settings_message,
                    iconRes = R.drawable.ic_restore_24dp,
                    positiveTextRes = R.string.dialog_reset_settings_confirm,
                ) {
                    SettingsManager.resetAllSettings(requireContext().applicationContext)
                    requireContext().toastSuccess(R.string.reset_settings_success)
                    activity?.recreate()
                }
                true
            }
        }
    }
}
