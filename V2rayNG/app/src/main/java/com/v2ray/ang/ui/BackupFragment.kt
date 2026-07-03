package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.WEBDAV_BACKUP_FILE_NAME
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityBackupBinding
import com.v2ray.ang.databinding.DialogWebdavBinding
import com.v2ray.ang.dto.entities.WebDavConfig
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.snackbarSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.WebDavManager
import com.v2ray.ang.util.BannerColorExtractor
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.ZipUtil
import com.v2ray.ang.util.showBlur
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupFragment : HelperMikuFragment<ActivityBackupBinding>() {

    private val configBackupOptions: Array<out String> by lazy {
        resources.getStringArray(R.array.config_backup_options)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        ActivityBackupBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar(binding.toolbar, title = getString(R.string.title_configuration_backup_restore))

        binding.layoutBackup.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_configuration_backup)
                .setItems(configBackupOptions) { _, which ->
                    when (which) {
                        0 -> backupViaLocal()
                        1 -> backupViaWebDav()
                    }
                }
                .showBlur()
        }

        binding.layoutShare.setOnClickListener {
            val ret = backupConfigurationToCache()
            if (ret.first) {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("application/zip")
                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(
                                Intent.EXTRA_STREAM,
                                FileProvider.getUriForFile(
                                    requireContext(), BuildConfig.APPLICATION_ID + ".cache", File(ret.second)
                                )
                            ), getString(R.string.title_configuration_share)
                    )
                )
            } else {
                requireContext().snackbarError(
                    getString(R.string.title_configuration_share),
                    title = getString(R.string.title_alerter_error)
                )
            }
        }

        binding.layoutRestore.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_configuration_restore)
                .setItems(configBackupOptions) { _, which ->
                    when (which) {
                        0 -> restoreViaLocal()
                        1 -> restoreViaWebDav()
                    }
                }
                .showBlur()
        }

        binding.layoutWebdavConfigSetting.setOnClickListener {
            showWebDavSettingsDialog()
        }
    }

    /**
     * Backup configuration to cache directory.
     * Returns Pair<success, zipFilePath>
     */
    private fun backupConfigurationToCache(): Pair<Boolean, String> {
        val context = requireContext()
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val folderName = "${getString(R.string.app_name)}_${dateFormatted}"
        val backupDir = context.cacheDir.absolutePath + "/$folderName"
        val outputZipFilePath = "${context.cacheDir.absolutePath}/$folderName.zip"

        val count = MMKV.backupAllToDirectory(backupDir)
        if (count <= 0) {
            return Pair(false, "")
        }

        // Backup custom banner image files alongside MMKV data
        backupBannerImages(backupDir)

        // Backup custom font file alongside MMKV data
        backupCustomFont(backupDir)

        return if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) {
            Pair(true, outputZipFilePath)
        } else {
            Pair(false, "")
        }
    }

    /**
     * Copy banner image files into the backup directory.
     * Each banner is saved as "banners/<key>.jpg" so restore can find them by key.
     */
    private fun backupBannerImages(backupDir: String) {
        val context = requireContext()
        val bannerKeys = listOf(
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
            AppConfig.PREF_SELECTED_BANNER_URI,
        )
        val bannersDir = java.io.File(backupDir, "banners").also { it.mkdirs() }
        for (key in bannerKeys) {
            val uriString = MmkvManager.decodeSettingsString(key) ?: continue
            if (uriString.isBlank()) continue
            try {
                val uri = Uri.parse(uriString)
                val srcFile = if (uri.scheme == "file") {
                    java.io.File(uri.path!!)
                } else {
                    val tmp = java.io.File(context.cacheDir, "banner_backup_tmp_${key}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    tmp
                }
                if (srcFile.exists()) {
                    srcFile.copyTo(java.io.File(bannersDir, "$key.jpg"), overwrite = true)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to backup banner for key $key", e)
            }
        }
    }

    /**
     * Copy the saved custom font file (if any) into the backup directory as "fonts/<filename>".
     */
    private fun backupCustomFont(backupDir: String) {
        val srcFile = com.v2ray.ang.util.CustomFontManager.getFontFile(requireContext()) ?: return
        try {
            val fontsDir = java.io.File(backupDir, "fonts").also { it.mkdirs() }
            srcFile.copyTo(java.io.File(fontsDir, srcFile.name), overwrite = true)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to backup custom font", e)
        }
    }

    private fun restoreConfiguration(zipFile: File): Boolean {
        val context = requireContext()
        val backupDir = context.cacheDir.absolutePath + "/${System.currentTimeMillis()}"

        if (!ZipUtil.unzipToFolder(zipFile, backupDir)) {
            return false
        }

        val count = MMKV.restoreAllFromDirectory(backupDir)
        SettingsChangeManager.makeSetupGroupTab()
        SettingsChangeManager.makeRestartService()

        // Restore custom banner image files and fix their paths in MMKV
        restoreBannerImages(backupDir)
        SettingsManager.preloadAllBanners(context)

        // Restore custom font file, if one was included in the backup
        restoreCustomFont(backupDir)

        // Re-extract banner color from restored home banner image if present
        val restoredHomeBannerUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
        if (!restoredHomeBannerUri.isNullOrBlank()) {
            lifecycleScope.launch {
                BannerColorExtractor.extractAndSave(context, Uri.parse(restoredHomeBannerUri))
            }
        }

        SettingsManager.initApp(context)
        return count > 0
    }

    /**
     * Copy the custom font file from the backup dir (if present) into internal storage.
     * If the backed-up settings say "custom" font but no font file was included, fall back
     * to the default font instead of pointing at a missing file.
     */
    private fun restoreCustomFont(backupDir: String) {
        val fontsDir = java.io.File(backupDir, "fonts")
        val srcFile = fontsDir.takeIf { it.exists() }?.listFiles()?.firstOrNull { it.isFile }

        if (srcFile == null) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            }
            return
        }

        val existingDisplayName = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT_CUSTOM_NAME)
        val restored = com.v2ray.ang.util.CustomFontManager.restoreFontFile(requireContext(), srcFile, existingDisplayName ?: srcFile.name)
        if (restored == null) {
            LogUtil.e(AppConfig.TAG, "Restored custom font file was invalid, falling back to default")
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            }
        }
    }

    /**
     * Copy banner image files from the backup dir into cacheDir,
     * then update each URI key in MMKV to the new path.
     */
    private fun restoreBannerImages(backupDir: String) {
        val bannerKeys = listOf(
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
            AppConfig.PREF_SELECTED_BANNER_URI,
        )
        val bannersDir = java.io.File(backupDir, "banners")
        if (!bannersDir.exists()) return

        val filesDir = requireContext().filesDir
        for (key in bannerKeys) {
            val srcFile = java.io.File(bannersDir, "$key.jpg")
            if (!srcFile.exists()) {
                // No backup for this banner — clear the stale URI so we don't point to a missing file
                MmkvManager.encodeSettings(key, "")
                continue
            }
            try {
                val bannersOutDir = java.io.File(filesDir, "banners").apply { mkdirs() }
                val destFile = java.io.File(bannersOutDir, "${key}_${System.currentTimeMillis()}.jpg")
                srcFile.copyTo(destFile, overwrite = true)
                MmkvManager.encodeSettings(key, Uri.fromFile(destFile).toString())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to restore banner for key $key", e)
                MmkvManager.encodeSettings(key, "")
            }
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }
            val context = requireContext()
            try {
                val targetFile =
                    File(context.cacheDir.absolutePath, "${System.currentTimeMillis()}.zip")
                context.contentResolver.openInputStream(uri).use { input ->
                    targetFile.outputStream().use { fileOut ->
                        input?.copyTo(fileOut)
                    }
                }
                if (restoreConfiguration(targetFile)) {
                    context.snackbarSuccess(
                        getString(R.string.title_configuration_restore),
                        title = getString(R.string.title_alerter_success)
                    )
                } else {
                    context.snackbarError(
                        getString(R.string.title_configuration_restore),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error during file restore", e)
                context.snackbarError(
                    getString(R.string.title_configuration_restore),
                    title = getString(R.string.title_alerter_error)
                )
            }
        }
    }

    private fun backupViaLocal() {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val defaultFileName = "${getString(R.string.app_name)}_${dateFormatted}.zip"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri != null) {
                val context = requireContext()
                try {
                    val ret = backupConfigurationToCache()
                    if (ret.first) {
                        // Copy the cached zip file to user-selected location
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            File(ret.second).inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        // Clean up cache file
                        File(ret.second).delete()
                        context.snackbarSuccess(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        context.snackbarError(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to backup configuration", e)
                    context.snackbarError(
                        getString(R.string.title_configuration_backup),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            }
        }
    }

    private fun restoreViaLocal() {
        showFileChooser()
    }

    private fun backupViaWebDav() {
        val context = requireContext()
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            context.snackbarError(
                getString(R.string.title_webdav_config_setting_unknown),
                title = getString(R.string.title_alerter_error)
            )
            return
        }

        (activity as? BaseActivity)?.showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val ret = backupConfigurationToCache()
                if (!ret.first) {
                    withContext(Dispatchers.Main) {
                        context.snackbarError(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                    return@launch
                }

                tempFile = File(ret.second)
                WebDavManager.init(saved)

                val ok = try {
                    WebDavManager.uploadFile(tempFile, WEBDAV_BACKUP_FILE_NAME)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "WebDAV upload error", e)
                    false
                }

                withContext(Dispatchers.Main) {
                    if (ok) {
                        context.snackbarSuccess(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        context.snackbarError(
                            getString(R.string.title_configuration_backup),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV backup error", e)
                withContext(Dispatchers.Main) {
                    context.snackbarError(
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
                    (activity as? BaseActivity)?.hideLoading()
                }
            }
        }
    }

    private fun restoreViaWebDav() {
        val context = requireContext()
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            context.snackbarError(
                getString(R.string.title_webdav_config_setting_unknown),
                title = getString(R.string.title_alerter_error)
            )
            return
        }

        (activity as? BaseActivity)?.showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var target: File? = null
            try {
                target = File(context.cacheDir, "download_${System.currentTimeMillis()}.zip")
                WebDavManager.init(saved)
                val ok = WebDavManager.downloadFile(WEBDAV_BACKUP_FILE_NAME, target)
                if (!ok) {
                    withContext(Dispatchers.Main) {
                        context.snackbarError(
                            getString(R.string.title_configuration_restore),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                    return@launch
                }

                val restored = restoreConfiguration(target)
                withContext(Dispatchers.Main) {
                    if (restored) {
                        context.snackbarSuccess(
                            getString(R.string.title_configuration_restore),
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        context.snackbarError(
                            getString(R.string.title_configuration_restore),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV download error", e)
                withContext(Dispatchers.Main) {
                    context.snackbarError(
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
                    (activity as? BaseActivity)?.hideLoading()
                }
            }
        }
    }

    private fun showWebDavSettingsDialog() {
        val context = requireContext()
        val dialogBinding = DialogWebdavBinding.inflate(LayoutInflater.from(context))

        MmkvManager.decodeWebDavConfig()?.let { cfg ->
            dialogBinding.etWebdavUrl.setText(cfg.baseUrl)
            dialogBinding.etWebdavUser.setText(cfg.username ?: "")
            dialogBinding.etWebdavPass.setText(cfg.password ?: "")
            dialogBinding.etWebdavRemotePath.setText(cfg.remoteBasePath ?: "/")
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.title_webdav_config_setting)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.menu_item_save_config) { _, _ ->
                val url = dialogBinding.etWebdavUrl.text.toString().trim()
                val user = dialogBinding.etWebdavUser.text.toString().trim().ifEmpty { null }
                val pass = dialogBinding.etWebdavPass.text.toString()
                val remotePath = dialogBinding.etWebdavRemotePath.text.toString().trim().ifEmpty { AppConfig.WEBDAV_BACKUP_DIR }
                val cfg = WebDavConfig(baseUrl = url, username = user, password = pass, remoteBasePath = remotePath)
                MmkvManager.encodeWebDavConfig(cfg)

                context.snackbarSuccess(
                    getString(R.string.title_webdav_config_setting),
                    title = getString(R.string.title_alerter_success)
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBlur()
    }
}
