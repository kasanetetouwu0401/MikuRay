package com.miku.ray.util

import android.content.Context
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.databinding.DialogCacheDetailBinding
import com.miku.ray.handler.MmkvManager
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

fun showCacheDetailDialog(context: Context) {
    val binding = DialogCacheDetailBinding.inflate(LayoutInflater.from(context))

    // Falls back to a standalone scope if the context isn't a LifecycleOwner (dialog still works,
    // it just won't be auto-cancelled on destroy).
    val scope = (context as? LifecycleOwner)?.lifecycleScope
        ?: CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun refreshSizes() {
        binding.tvDataSizeValue.text = context.getString(R.string.label_storage_calculating)
        binding.tvCacheSizeValue.text = context.getString(R.string.label_storage_calculating)
        scope.launch {
            binding.tvDataSizeValue.text =
                MmkvManager.formatTrafficBytesPublic(AppStorageUtils.getAppDataSize(context))
            binding.tvCacheSizeValue.text =
                MmkvManager.formatTrafficBytesPublic(AppStorageUtils.getAppCacheSize(context))
        }
    }
    refreshSizes()

    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok, null)
        .create()
    WindowBlurUtils.applyWindowBlur(dialog.window)

    binding.btnClearCache.setOnClickListener {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.clear_cache_confirm_title)
            .setMessage(R.string.clear_cache_confirm_summary)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_clear_cache) { _, _ ->
                scope.launch {
                    val success = AppStorageUtils.clearAppCache(context)
                    if (success) {
                        context.snackbarSuccess(R.string.clear_cache_success, title = context.getString(R.string.title_alerter_success))
                    } else {
                        context.snackbarError(R.string.clear_cache_failed, title = context.getString(R.string.title_alerter_error))
                    }
                    refreshSizes()
                }
            }
            .show()
    }

    dialog.show()
}
