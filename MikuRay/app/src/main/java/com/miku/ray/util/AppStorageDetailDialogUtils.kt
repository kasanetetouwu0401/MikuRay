package com.miku.ray.util

import android.content.Context
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.databinding.DialogAppStorageDetailBinding
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val APP_STORAGE_DETAIL_REFRESH_INTERVAL_MS = 1000L

fun showAppStorageDetailDialog(
    context: Context,
    onStorageChanged: (() -> Unit)? = null
) {
    val binding = DialogAppStorageDetailBinding.inflate(LayoutInflater.from(context))
    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok, null)
        .create()

    val dialogScope = (context as? LifecycleOwner)?.lifecycleScope
        ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val ownsScope = context !is LifecycleOwner
    var isClearingCache = false
    var clearJob: Job? = null
    var refreshJob: Job? = null

    fun renderStorageInfo(info: AppStorageInfo) {
        binding.tvStorageTotalValue.text = formatStorageBytes(info.totalBytes)
        binding.tvStorageAppValue.text = formatStorageBytes(info.appBytes)
        binding.tvStorageDataValue.text = formatStorageBytes(info.dataBytes)
        binding.tvStorageCacheValue.text = formatStorageBytes(info.cacheBytes)
        binding.btnClearCache.isEnabled = !isClearingCache && info.cacheBytes > 0L
    }

    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()

    // Keep both the chip and this dialog accurate while files are created or
    // removed by the app or by another system component.
    refreshJob = dialogScope.launch {
        while (isActive && dialog.isShowing) {
            val info = withContext(Dispatchers.IO) { context.getAppStorageInfo() }
            if (!isActive || !dialog.isShowing) break
            renderStorageInfo(info)
            delay(APP_STORAGE_DETAIL_REFRESH_INTERVAL_MS)
        }
    }

    binding.btnClearCache.setOnClickListener {
        if (isClearingCache) return@setOnClickListener
        isClearingCache = true
        binding.btnClearCache.isEnabled = false

        clearJob = dialogScope.launch {
            val cleared = runCatching {
                withContext(Dispatchers.IO) { context.clearAppCache() }
            }.getOrDefault(false)
            val updatedInfo = withContext(Dispatchers.IO) { context.getAppStorageInfo() }

            isClearingCache = false
            renderStorageInfo(updatedInfo)
            onStorageChanged?.invoke()

            if (cleared) {
                context.snackbarSuccess(
                    R.string.toast_app_cache_cleared,
                    title = context.getString(R.string.title_alerter_success)
                )
            } else {
                context.snackbarError(
                    R.string.toast_app_cache_clear_failed,
                    title = context.getString(R.string.title_alerter_error)
                )
            }
        }
    }

    dialog.setOnDismissListener {
        refreshJob?.cancel()
        clearJob?.cancel()
        if (ownsScope) dialogScope.cancel()
    }
}
