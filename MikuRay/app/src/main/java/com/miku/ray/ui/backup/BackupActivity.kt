package com.miku.ray.ui.backup

import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.ui.base.HelperBaseActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.util.showBlur
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.WEBDAV_BACKUP_FILE_NAME
import com.miku.ray.BuildConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityBackupBinding
import com.miku.ray.databinding.DialogWebdavBinding
import com.miku.ray.dto.entities.WebDavConfig
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.WebDavManager
import com.miku.ray.util.AppNameHelper
import com.miku.ray.util.LogUtil
import com.miku.ray.util.showDeleteConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupActivity : HelperBaseActivity() {
    private val binding by lazy { ActivityBackupBinding.inflate(layoutInflater) }

    private val config_backup_options: Array<out String> by lazy {
        resources.getStringArray(R.array.config_backup_options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.backupScrollContent.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(
            toolbar,
            showHomeAsUp = true,
            title = getString(R.string.title_configuration_backup_restore),
            subtitle = getString(R.string.subtitle_backup)
        )

        binding.layoutBackup.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_configuration_backup)
                .setIcon(RemixR.drawable.rmx_device_save_3_line)
                .setItems(config_backup_options) { _, which ->
                    when (which) {
                        0 -> backupViaLocal()
                        1 -> backupViaWebDav()
                    }
                }
                .showBlur()
        }

        binding.layoutShare.setOnClickListener {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                val ret = BackupManager.exportToCacheFile(this@BackupActivity)
                withContext(Dispatchers.Main) {
                    hideLoading()
                    if (ret.first && ret.second != null) {
                        startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType(BackupManager.MIME_TYPE)
                                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .putExtra(
                                        Intent.EXTRA_STREAM,
                                        FileProvider.getUriForFile(
                                            this@BackupActivity,
                                            BuildConfig.APPLICATION_ID + ".cache",
                                            ret.second!!
                                        )
                                    ),
                                getString(R.string.title_configuration_share)
                            )
                        )
                    } else {
                        snackbarError(
                            getString(R.string.title_configuration_share),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            }
        }

        binding.layoutRestore.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_configuration_restore)
                .setIcon(RemixR.drawable.rmx_history_line)
                .setItems(config_backup_options) { _, which ->
                    when (which) {
                        0 -> restoreViaLocal()
                        1 -> restoreViaWebDav()
                    }
                }
                .showBlur()
        }

        binding.layoutProfileStorageCleanup.setOnClickListener {
            showDeleteConfirmDialog(
                context = this,
                titleRes = R.string.title_profile_storage_cleanup,
                messageRes = R.string.message_profile_storage_cleanup,
            ) {
                cleanupProfileStorage()
            }
        }

        binding.layoutWebdavConfigSetting.setOnClickListener {
            showWebDavSettingsDialog()
        }
    }

    private fun cleanupProfileStorage() {
        showLoading()

        lifecycleScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) {
                    MmkvManager.removeOrphanedServerProfiles()
                }
                if (removed == null) {
                    snackbarError(
                        getString(R.string.toast_profile_storage_cleanup_skipped),
                        title = getString(R.string.title_alerter_error)
                    )
                } else {
                    snackbarSuccess(
                        getString(R.string.toast_profile_storage_cleanup, removed),
                        title = getString(R.string.title_alerter_success)
                    )
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to clean up profile storage", e)
                snackbarError(
                    getString(R.string.toast_failure),
                    title = getString(R.string.title_alerter_error)
                )
            } finally {
                hideLoading()
            }
        }
    }

    private fun backupViaLocal() {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val defaultFileName =
            "${AppNameHelper.getDisplayName(this)}_$dateFormatted${BackupManager.FILE_EXTENSION}"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri == null) return@launchCreateDocument
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    BackupManager.exportTo(this@BackupActivity, uri)
                    withContext(Dispatchers.Main) {
                        snackbarSuccess(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_success)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to backup configuration", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                    }
                }
            }
        }
    }

    private fun restoreViaLocal() {
        // Accept both the new format and legacy zip (chooser still * /* so user can pick either)
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Prefer streaming from Uri; if the system gave a zip content type we still
                    // copy to a temp file so the legacy path can inspect the magic bytes.
                    val result = BackupManager.importFrom(this@BackupActivity, uri)
                    // If the URI was a legacy zip, importFrom only understands JSON.
                    // Fall back by copying to a temp file and letting importFromFile decide.
                    val finalResult = if (result is BackupManager.ImportResult.Error) {
                        val targetFile = File(cacheDir, "restore_${System.nanoTime()}")
                        try {
                            contentResolver.openInputStream(uri)?.use { input ->
                                targetFile.outputStream().use { input.copyTo(it) }
                            }
                            BackupManager.importFromFile(this@BackupActivity, targetFile)
                        } finally {
                            targetFile.delete()
                        }
                    } else {
                        result
                    }

                    withContext(Dispatchers.Main) {
                        when (finalResult) {
                            is BackupManager.ImportResult.Success -> {
                                snackbarSuccess(
                                    getString(R.string.title_configuration_restore),
                                    title = getString(R.string.title_alerter_success)
                                )
                            }
                            is BackupManager.ImportResult.Error -> {
                                snackbarError(
                                    finalResult.message.ifBlank {
                                        getString(R.string.title_configuration_restore)
                                    },
                                    title = getString(R.string.title_alerter_error)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Error during file restore", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.title_configuration_restore),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                    }
                }
            }
        }
    }

    private fun backupViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            snackbarError(
                getString(R.string.title_webdav_config_setting_unknown),
                title = getString(R.string.title_alerter_error)
            )
            return
        }

        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val ret = BackupManager.exportToCacheFile(this@BackupActivity)
                if (!ret.first || ret.second == null) {
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                    return@launch
                }
                tempFile = ret.second
                val ok = WebDavManager.uploadFile(tempFile!!, WEBDAV_BACKUP_FILE_NAME)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        snackbarSuccess(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        snackbarError(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV upload error", e)
                withContext(Dispatchers.Main) {
                    snackbarError(
                        getString(R.string.title_configuration_backup),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            } finally {
                try {
                    tempFile?.delete()
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }
        }
    }

    private fun restoreViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            snackbarError(
                getString(R.string.title_webdav_config_setting_unknown),
                title = getString(R.string.title_alerter_error)
            )
            return
        }

        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var target: File? = null
            try {
                target = File(cacheDir, "download_${System.currentTimeMillis()}${BackupManager.FILE_EXTENSION}")
                val ok = WebDavManager.downloadFile(WEBDAV_BACKUP_FILE_NAME, target)
                if (!ok) {
                    // Also try the legacy zip name once, for migration
                    val legacy = File(cacheDir, "download_legacy_${System.currentTimeMillis()}.zip")
                    val legacyOk = WebDavManager.downloadFile("backup_ng.zip", legacy)
                    if (legacyOk) {
                        target = legacy
                    } else {
                        withContext(Dispatchers.Main) {
                            snackbarError(
                                getString(R.string.title_configuration_restore),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                        return@launch
                    }
                }

                val result = BackupManager.importFromFile(this@BackupActivity, target!!)
                withContext(Dispatchers.Main) {
                    when (result) {
                        is BackupManager.ImportResult.Success -> {
                            snackbarSuccess(
                                getString(R.string.title_configuration_restore),
                                title = getString(R.string.title_alerter_success)
                            )
                        }
                        is BackupManager.ImportResult.Error -> {
                            snackbarError(
                                result.message.ifBlank {
                                    getString(R.string.title_configuration_restore)
                                },
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV download error", e)
                withContext(Dispatchers.Main) {
                    snackbarError(
                        getString(R.string.title_configuration_restore),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            } finally {
                try {
                    target?.delete()
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }
        }
    }

    private fun showWebDavSettingsDialog() {
        val dialogBinding = DialogWebdavBinding.inflate(layoutInflater)

        MmkvManager.decodeWebDavConfig()?.let { cfg ->
            dialogBinding.etWebdavUrl.setText(cfg.baseUrl)
            dialogBinding.etWebdavUser.setText(cfg.username ?: "")
            dialogBinding.etWebdavPass.setText(cfg.password ?: "")
            dialogBinding.etWebdavRemotePath.setText(cfg.remoteBasePath)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_webdav_config_setting)
            .setIcon(RemixR.drawable.rmx_cloud_line)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.menu_item_save_config) { _, _ ->
                val url = dialogBinding.etWebdavUrl.text.toString().trim()
                val user = dialogBinding.etWebdavUser.text.toString().trim().ifEmpty { null }
                val pass = dialogBinding.etWebdavPass.text.toString()
                val remotePath = dialogBinding.etWebdavRemotePath.text.toString().trim()
                    .ifEmpty { AppConfig.WEBDAV_BACKUP_DIR }
                val cfg = WebDavConfig(
                    baseUrl = url,
                    username = user,
                    password = pass,
                    remoteBasePath = remotePath
                )
                MmkvManager.encodeWebDavConfig(cfg)

                snackbarSuccess(
                    getString(R.string.title_webdav_config_setting),
                    title = getString(R.string.title_alerter_success)
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBlur()
    }
}
