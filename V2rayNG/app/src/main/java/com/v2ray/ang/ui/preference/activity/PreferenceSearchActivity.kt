package com.v2ray.ang.ui.preference.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.R
import com.v2ray.ang.dto.PreferenceSearchEntry
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.util.PreferenceSearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated search screen for finding any preference key across every settings
 * screen in the app (Settings, UI, VPN, Core, Mux, Fragment, Advanced).
 *
 * The index is built lazily by [PreferenceSearchIndex] from the actual preference
 * XML resources, so it always reflects whatever preferences currently exist.
 */
class PreferenceSearchActivity : BaseActivity() {

    companion object {
        /** Extra carried to the destination settings activity to scroll to + highlight a preference. */
        const val EXTRA_HIGHLIGHT_KEY = "extra_highlight_preference_key"
    }

    private var allEntries: List<PreferenceSearchEntry> = emptyList()
    private val adapter = PreferenceSearchAdapter(onResultClicked = { entry -> openResult(entry) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preference_search)

        val rootView = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top = maxOf(systemBars.top, displayCutout.top),
                bottom = maxOf(systemBars.bottom, displayCutout.bottom),
                left = maxOf(systemBars.left, displayCutout.left),
                right = maxOf(systemBars.right, displayCutout.right)
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.menu_item_search_preferences))

        findViewById<RecyclerView>(R.id.recycler_view).adapter = adapter

        setupSearchView()
        loadIndex()
    }

    private fun setupSearchView() {
        val searchView = findViewById<SearchView>(R.id.search_view)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filter(newText.orEmpty())
                return true
            }
        })
        searchView.requestFocus()
    }

    private fun loadIndex() {
        lifecycleScope.launch {
            allEntries = withContext(Dispatchers.Default) {
                PreferenceSearchIndex.getEntries(this@PreferenceSearchActivity)
            }
            showEmptyQueryState()
        }
    }

    private fun filter(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            showEmptyQueryState()
            return
        }

        val key = query.lowercase()
        val filtered = allEntries.filter { entry -> matches(entry, key) }
        showResults(filtered)
    }

    private fun matches(entry: PreferenceSearchEntry, key: String): Boolean {
        return entry.title.lowercase().contains(key) ||
            entry.summary.lowercase().contains(key) ||
            entry.key.lowercase().contains(key) ||
            entry.categoryTitle.lowercase().contains(key) ||
            entry.screenTitle.lowercase().contains(key)
    }

    private fun showEmptyQueryState() {
        adapter.submitList(emptyList())
        setEmptyVisible(true, getString(R.string.preference_search_empty_query))
    }

    private fun showResults(results: List<PreferenceSearchEntry>) {
        adapter.submitList(results)
        setEmptyVisible(results.isEmpty(), getString(R.string.preference_search_empty))
    }

    private fun setEmptyVisible(visible: Boolean, message: String) {
        findViewById<RecyclerView>(R.id.recycler_view).visibility = if (visible) View.GONE else View.VISIBLE
        findViewById<View>(R.id.empty_state).visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) setEmptyStateText(message)
    }

    private fun setEmptyStateText(text: String) {
        findViewById<android.widget.TextView>(R.id.empty_state_text)?.text = text
    }

    private fun openResult(entry: PreferenceSearchEntry) {
        val intent = Intent(this, entry.targetActivity).apply {
            putExtra(EXTRA_HIGHLIGHT_KEY, entry.key)
        }
        startActivity(intent)
    }
}
