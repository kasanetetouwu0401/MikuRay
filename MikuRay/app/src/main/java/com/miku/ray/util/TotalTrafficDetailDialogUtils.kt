package com.miku.ray.util

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.databinding.DialogTotalTrafficDetailBinding
import com.miku.ray.handler.MmkvManager
import com.miku.ray.widget.TrafficBarChartView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HISTORY_DAYS = 7
private const val TOTAL_TRAFFIC_REFRESH_INTERVAL_MS = 1000L

private data class TotalTrafficSnapshot(
    val uploadBytes: Long,
    val downloadBytes: Long,
    val todayBytes: Long,
    val monthBytes: Long,
    val history: List<Triple<String, Long, Long>>
)

private fun readTotalTrafficSnapshot(): TotalTrafficSnapshot {
    val (uploadBytes, downloadBytes) = MmkvManager.getTotalTrafficDetail() ?: (0L to 0L)
    val (todayUp, todayDown) = MmkvManager.getTodayTrafficDetail()
    val (monthUp, monthDown) = MmkvManager.getCurrentMonthTrafficDetail()
    val history = MmkvManager.getDailyTrafficHistory(HISTORY_DAYS)
        .map { (dateKey, up, down) -> Triple(dateKey, up, down) }

    return TotalTrafficSnapshot(
        uploadBytes = uploadBytes,
        downloadBytes = downloadBytes,
        todayBytes = todayUp + todayDown,
        monthBytes = monthUp + monthDown,
        history = history
    )
}

fun showTotalTrafficDetailDialog(context: Context) {
    val binding = DialogTotalTrafficDetailBinding.inflate(LayoutInflater.from(context))
    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok, null)
        .create()
    val dialogScope = (context as? LifecycleOwner)?.lifecycleScope
        ?: CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)
    val ownsScope = context !is LifecycleOwner
    var refreshJob: kotlinx.coroutines.Job? = null

    fun render(snapshot: TotalTrafficSnapshot) {
        binding.tvUploadValue.text = MmkvManager.formatTrafficBytesPublic(snapshot.uploadBytes)
        binding.tvDownloadValue.text = MmkvManager.formatTrafficBytesPublic(snapshot.downloadBytes)
        binding.tvCombinedValue.text = MmkvManager.formatTrafficBytesPublic(
            snapshot.uploadBytes + snapshot.downloadBytes
        )
        binding.tvTodayValue.text = MmkvManager.formatTrafficBytesPublic(snapshot.todayBytes)
        binding.tvMonthValue.text = MmkvManager.formatTrafficBytesPublic(snapshot.monthBytes)

        val hasHistory = snapshot.history.any { (_, up, down) -> up + down > 0L }
        binding.chartDailyTraffic.isVisible = hasHistory
        binding.tvHistoryEmpty.isVisible = !hasHistory
        binding.chartDailyTraffic.setEntries(
            snapshot.history.map { (dateKey, up, down) ->
                TrafficBarChartView.DayEntry(dateKey, up, down)
            }
        )
    }

    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()

    refreshJob = dialogScope.launch {
        while (isActive && dialog.isShowing) {
            val snapshot = withContext(Dispatchers.IO) { readTotalTrafficSnapshot() }
            if (!isActive || !dialog.isShowing) break
            render(snapshot)
            delay(TOTAL_TRAFFIC_REFRESH_INTERVAL_MS)
        }
    }

    dialog.setOnDismissListener {
        refreshJob?.cancel()
        if (ownsScope) dialogScope.cancel()
    }
}
