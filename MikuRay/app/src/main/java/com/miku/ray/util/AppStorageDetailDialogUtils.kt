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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun showAppStorageDetailDialog(
    context: Context,
    onStorageChanged: (() -> Unit)? = null
) {
    val binding = DialogAppStorageDetailBinding.inflate(LayoutInflater.from(context))
    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok, null)
        .create()

    fun renderStorageInfo(info: AppStorageInfo) {
        binding.tvStorageTotalValue.text = formatStorageBytes(info.totalBytes)
        binding.tvStorageAppValue.text = formatStorageBytes(info.appBytes)
        binding.tvStorageDataValue.text = formatStorageBytes(info.dataBytes)
        binding.tvStorageCacheValue.text = formatStorageBytes(info.cacheBytes)
        binding.btnClearCache.isEnabled = info.cacheBytes > 0L
    }

    renderStorageInfo(context.getAppStorageInfo())
    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()

    var clearJob: Job? = null
    binding.btnClearCache.setOnClickListener {
        if (clearJob?.isActive == true) return@setOnClickListener
        binding.btnClearCache.isEnabled = false

        val scope = (context as? LifecycleOwner)?.lifecycleScope
            ?: kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
        clearJob = scope.launch {
            val cleared = withContext(Dispatchers.IO) { context.clearAppCache() }
            val updatedInfo = withContext(Dispatchers.IO) { context.getAppStorageInfo() }
            renderStorageInfo(updatedInfo)
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

    dialog.setOnDismissListener { clearJob?.cancel() }
}
