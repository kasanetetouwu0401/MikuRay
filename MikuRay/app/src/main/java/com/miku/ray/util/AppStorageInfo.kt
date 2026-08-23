package com.miku.ray.util

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Storage statistics for this application. On Android O and newer, the values
 * come from the same platform API used by the system app-info storage screen.
 * Older Android versions use a best-effort filesystem fallback.
 */
data class AppStorageInfo(
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long
) {
    val dataAndCacheBytes: Long
        get() = dataBytes + cacheBytes

    val totalBytes: Long
        get() = appBytes + dataAndCacheBytes
}

fun Context.getAppStorageInfo(): AppStorageInfo {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val systemStats = runCatching {
            val manager = getSystemService(StorageStatsManager::class.java)
            manager.queryStatsForPackage(
                applicationInfo.storageUuid,
                packageName,
                Process.myUserHandle()
            )
        }.getOrNull()

        if (systemStats != null) {
            return AppStorageInfo(
                appBytes = systemStats.appBytes,
                dataBytes = systemStats.dataBytes,
                cacheBytes = systemStats.cacheBytes
            )
        }
    }

    return getManualAppStorageInfo()
}

fun Context.clearAppCache(): Boolean {
    return cacheRoots().all { root ->
        clearDirectoryContents(root)
    }
}

fun formatStorageBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = safeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }

    val formatter = DecimalFormat("0.##", DecimalFormatSymbols(Locale.getDefault()))
    return "${formatter.format(value)} ${units[unitIndex]}"
}

private fun Context.getManualAppStorageInfo(): AppStorageInfo {
    val cacheRoots = cacheRoots()
    val cacheRootPaths = cacheRoots.mapNotNull { it.canonicalPath }.toSet()
    val codeCachePath = runCatching { codeCacheDir.canonicalPath }.getOrNull()

    val dataBytes = directorySize(File(applicationInfo.dataDir)) { file ->
        val path = runCatching { file.canonicalPath }.getOrNull()
        path != null && path !in cacheRootPaths && path != codeCachePath
    }
    val cacheBytes = cacheRoots.sumOf(::directorySize)
    val appBytes = listOfNotNull(
        applicationInfo.sourceDir,
        *applicationInfo.splitSourceDirs.orEmpty()
    ).distinct().sumOf { path -> File(path).length() }

    return AppStorageInfo(
        appBytes = appBytes,
        dataBytes = dataBytes,
        cacheBytes = cacheBytes
    )
}

private fun Context.cacheRoots(): List<File> {
    return buildList {
        add(cacheDir)
        add(codeCacheDir)
        externalCacheDirs.filterNotNullTo(this)
    }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
}

private fun directorySize(file: File, include: (File) -> Boolean = { true }): Long {
    if (!file.exists() || !include(file)) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { child -> directorySize(child, include) } ?: 0L
}

private fun clearDirectoryContents(directory: File): Boolean {
    if (!directory.exists()) return true
    return directory.listFiles()?.all { it.deleteRecursively() } ?: true
}
