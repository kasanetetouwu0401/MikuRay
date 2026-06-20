package com.v2ray.ang.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityAppTrafficBinding
import com.v2ray.ang.dto.AppTrafficInfo
import com.v2ray.ang.util.AppTrafficUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class AppTrafficActivity : BaseActivity() {

    companion object {
        // How far back "Total" mode looks for usage, to keep the query reasonably fast.
        private const val TOTAL_LOOKBACK_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val LIVE_POLL_INTERVAL_MILLIS = 2000L

        // Minimum bytes transferred within one poll interval to be considered "active now".
        private const val ACTIVE_THRESHOLD_BYTES = 2048L

        private const val MODE_TOTAL = 0
        private const val MODE_ACTIVE = 1
    }

    private val binding by lazy { ActivityAppTrafficBinding.inflate(layoutInflater) }
    private val adapter = AppTrafficAdapter()

    private var candidateApps: List<AppTrafficInfo> = emptyList()
    private var displayedApps: List<AppTrafficInfo> = emptyList()
    private var currentMode = MODE_TOTAL
    private var sortByUsage = true
    private var searchKeyword = ""

    private var livePollJob: Job? = null
    private var lastLiveSnapshot: Map<Int, Long> = emptyMap()
    private var wasPermissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top    = maxOf(systemBars.top,    displayCutout.top),
                bottom = maxOf(systemBars.bottom,    displayCutout.bottom),
                left   = maxOf(systemBars.left,   displayCutout.left),
                right  = maxOf(systemBars.right,  displayCutout.right)
            )
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_app_traffic))

        binding.recyclerView.adapter = adapter
        binding.btnGrantPermission.setOnClickListener {
            AppTrafficUtil.openUsageAccessSettings(this)
        }
        binding.swipeRefresh.setOnRefreshListener {
            when (currentMode) {
                MODE_TOTAL -> loadTotalTraffic()
                else -> binding.swipeRefresh.isRefreshing = false
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                switchMode(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        loadCandidateAppsThenTraffic()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionBanner()
        val nowGranted = AppTrafficUtil.hasUsageAccessPermission(this)
        if (currentMode == MODE_TOTAL && nowGranted && !wasPermissionGranted) {
            loadTotalTraffic()
        }
        wasPermissionGranted = nowGranted
        if (currentMode == MODE_ACTIVE) startLivePolling()
    }

    override fun onPause() {
        super.onPause()
        stopLivePolling()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_app_traffic, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchKeyword = newText.orEmpty()
                    applyFilterAndSort()
                    return false
                }
            })
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.sort_by_usage -> {
            sortByUsage = true
            applyFilterAndSort()
            true
        }

        R.id.sort_by_name -> {
            sortByUsage = false
            applyFilterAndSort()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun switchMode(mode: Int) {
        currentMode = mode
        adapter.isLiveMode = mode == MODE_ACTIVE
        when (mode) {
            MODE_TOTAL -> {
                stopLivePolling()
                binding.subtitle.text = getString(R.string.app_traffic_subtitle_total)
                refreshPermissionBanner()
                loadTotalTraffic()
            }

            MODE_ACTIVE -> {
                binding.permissionBanner.visibility = android.view.View.GONE
                binding.subtitle.text = getString(R.string.app_traffic_subtitle_active)
                startLivePolling()
            }
        }
    }

    private fun refreshPermissionBanner() {
        if (currentMode != MODE_TOTAL) return
        val granted = AppTrafficUtil.hasUsageAccessPermission(this)
        binding.permissionBanner.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun loadCandidateAppsThenTraffic() {
        showLoading()
        lifecycleScope.launch {
            try {
                candidateApps = withContext(Dispatchers.IO) {
                    AppTrafficUtil.loadCandidateApps(this@AppTrafficActivity)
                }
                loadTotalTraffic()
            } catch (e: Exception) {
                LogUtil.e("AppTrafficActivity", "Failed to load candidate apps", e)
                hideLoading()
            }
        }
    }

    private fun loadTotalTraffic() {
        if (candidateApps.isEmpty()) return
        showLoading()

        lifecycleScope.launch {
            try {
                val merged = withContext(Dispatchers.IO) {
                    val since = System.currentTimeMillis() - TOTAL_LOOKBACK_MILLIS
                    val traffic = AppTrafficUtil.queryTrafficByUid(this@AppTrafficActivity, since)
                    candidateApps.map { app ->
                        val (rx, tx) = traffic[app.uid] ?: (0L to 0L)
                        app.copy(rxBytes = rx, txBytes = tx, isActiveNow = false)
                    }
                }
                displayedApps = merged
                applyFilterAndSort()
            } catch (e: Exception) {
                LogUtil.e("AppTrafficActivity", "Failed to load total traffic", e)
            } finally {
                hideLoading()
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun startLivePolling() {
        if (livePollJob?.isActive == true) return
        lastLiveSnapshot = emptyMap()

        livePollJob = lifecycleScope.launch {
            while (isActive) {
                pollLiveTraffic()
                delay(LIVE_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopLivePolling() {
        livePollJob?.cancel()
        livePollJob = null
    }

    private suspend fun pollLiveTraffic() {
        if (candidateApps.isEmpty()) return
        if (!AppTrafficUtil.isNetworkAvailable(this)) {
            if (displayedApps.any { it.isActiveNow }) {
                displayedApps = displayedApps.map { it.copy(isActiveNow = false) }
                applyFilterAndSort()
            }
            return
        }

        val uids = candidateApps.map { it.uid }.toSet()
        val snapshot = withContext(Dispatchers.IO) { AppTrafficUtil.snapshotUidTraffic(uids) }

        val merged = candidateApps.map { app ->
            val now = snapshot[app.uid] ?: 0L
            val before = lastLiveSnapshot[app.uid] ?: now
            val delta = (now - before).coerceAtLeast(0L)
            val isActive = lastLiveSnapshot.isNotEmpty() && delta >= ACTIVE_THRESHOLD_BYTES
            app.copy(rxBytes = delta, txBytes = 0L, isActiveNow = isActive)
        }

        lastLiveSnapshot = snapshot
        displayedApps = merged
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        val key = searchKeyword.uppercase()
        var filtered = displayedApps.filter { app ->
            key.isEmpty() || app.appName.uppercase().contains(key) || app.packageName.uppercase().contains(key)
        }

        filtered = if (currentMode == MODE_ACTIVE) {
            // Active mode: show currently-active apps first, then by recent transfer amount.
            filtered.sortedWith(
                compareByDescending<AppTrafficInfo> { it.isActiveNow }
                    .thenByDescending { it.totalBytes }
            )
        } else if (sortByUsage) {
            filtered.sortedByDescending { it.totalBytes }
        } else {
            val collator = Collator.getInstance()
            filtered.sortedWith { a, b -> collator.compare(a.appName, b.appName) }
        }

        adapter.submitList(filtered)
    }
}
