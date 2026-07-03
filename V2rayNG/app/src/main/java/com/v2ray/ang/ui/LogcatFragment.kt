package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLogcatBinding
import com.v2ray.ang.extension.snackbarDefault
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.LogcatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogcatFragment : MikuFragment<ActivityLogcatBinding>(), SwipeRefreshLayout.OnRefreshListener {

    private val viewModel: LogcatViewModel by viewModels()
    private lateinit var adapter: LogcatRecyclerAdapter

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        ActivityLogcatBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar(binding.toolbar, title = getString(R.string.title_logcat))
        setupMenu()

        adapter = LogcatRecyclerAdapter(viewModel, ::onLogLongClick)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.refreshLayout.setOnRefreshListener(this)

        requireContext().snackbarDefault(getString(R.string.pull_down_to_refresh), title = getString(R.string.title_alerter_info))
    }

    private fun setupMenu() {
        binding.toolbar.inflateMenu(R.menu.menu_logcat)

        val searchItem = binding.toolbar.menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.filter(newText)
                    refreshData()
                    return false
                }
            })
            searchView.setOnCloseListener {
                viewModel.filter("")
                refreshData()
                false
            }
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.copy_all -> {
                    val all = viewModel.getAll().joinToString("\n")
                    Utils.setClipboard(requireContext(), all)
                    requireContext().snackbarSuccess(
                        getString(R.string.logcat_copy),
                        title = getString(R.string.title_alerter_success)
                    )
                    true
                }

                R.id.share_all -> {
                    shareLogcat()
                    true
                }

                R.id.clear_all -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        viewModel.clearLogcat()
                        withContext(Dispatchers.Main) {
                            refreshData()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun onLogLongClick(log: String): Boolean {
        Utils.setClipboard(requireContext(), log)
        return true
    }

    private fun shareLogcat() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logText = viewModel.getAll().joinToString("\n")

            val result = try {
                val shareDir = File(requireContext().cacheDir, "shared_logs").apply {
                    mkdirs()
                }

                shareDir.listFiles()?.forEach { it.delete() }

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val logFile = File(shareDir, "MikuRay_logcat_$timestamp.txt")
                logFile.writeText(logText, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.cache",
                    logFile
                )

                uri to logFile.name
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    requireContext().snackbarDefault(e.localizedMessage ?: e.toString(), title = getString(R.string.title_alerter_info))
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, result.first)
                    putExtra(Intent.EXTRA_SUBJECT, result.second)
                    putExtra(Intent.EXTRA_TITLE, result.second)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(requireContext().contentResolver, result.second, result.first)
                }

                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(R.string.logcat_share)
                    )
                )
            }
        }
    }

    override fun onRefresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadLogcat()
            withContext(Dispatchers.Main) {
                binding.refreshLayout.isRefreshing = false
                refreshData()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        adapter.notifyDataSetChanged()
    }
}
