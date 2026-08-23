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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How often the dialog re-measures storage while it stays open. */
private const val APP_STORAGE_DIALOG_AUTO_REFRESH_INTERVAL_MS = 2_000L

fun showAppStorageDetailDialog(
    context: Context,
    onStorageChanged: (() -> Unit)? = null
) {
    val binding = DialogAppStorageDetailBinding.inflate(LayoutInflater.from(context))
    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok, null)
        .create()

    // A dedicated SupervisorJob so the periodic auto-refresh loop and the
    // one-off clear-cache job don't cancel each other if either one fails.
    val scope = (context as? LifecycleOwner)?.lifecycleScope
        ?: CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun renderStorageInfo(info: AppStorageInfo) {
        binding.tvStorageTotalValue.text = formatStorageBytes(info.totalBytes)
        binding.tvStorageAppValue.text = formatStorageBytes(info.appBytes)
        binding.tvStorageDataValue.text = formatStorageBytes(info.dataBytes)
        binding.tvStorageCacheValue.text = formatStorageBytes(info.cacheBytes)
        binding.btnClearCache.isEnabled = info.cacheBytes > 0L
    }

    // Measuring storage walks the app's data/cache directories on disk, so it
    // must never run on the main thread - doing so previously caused the
    // dialog to jank/freeze briefly on open.
    suspend fun loadStorageInfo() {
        val info = withContext(Dispatchers.IO) { context.getAppStorageInfo() }
        renderStorageInfo(info)
    }

    var clearJob: Job? = null

    scope.launch { loadStorageInfo() }
    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()

    // Keep the figures live while the dialog is open: the VPN core keeps
    // writing logs/cache in the background, so a one-shot measurement at
    // open time quickly goes stale.
    val autoRefreshJob = scope.launch {
        while (isActive) {
            delay(APP_STORAGE_DIALOG_AUTO_REFRESH_INTERVAL_MS)
            // Skip a tick while a manual clear is in flight so the two
            // refreshes don't race and clobber each other's render.
            if (clearJob?.isActive != true) {
                loadStorageInfo()
            }
        }
    }

    binding.btnClearCache.setOnClickListener {
        if (clearJob?.isActive == true) return@setOnClickListener
        binding.btnClearCache.isEnabled = false

        clearJob = scope.launch {
            val cleared = withContext(Dispatchers.IO) { context.clearAppCache() }
            loadStorageInfo()
            onStorageChanged?.invoke()
            if (cleared) {
                context.snackbarSuccess(
                    R.string.toast_app_cache_cleared,
                    title = context.getString(R.string.title_alerter_success)
                )
                dialog.dismiss()
            } else {
                context.snackbarError(
                    R.string.toast_app_cache_clear_failed,
                    title = context.getString(R.string.title_alerter_error)
                )
            }
        }
    }

    dialog.setOnDismissListener {
        autoRefreshJob.cancel()
        clearJob?.cancel()
    }
}
