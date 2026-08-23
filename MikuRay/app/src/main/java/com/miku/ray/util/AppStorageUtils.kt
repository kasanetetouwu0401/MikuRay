package com.miku.ray.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helpers for reporting and clearing MikuRay's on-device storage footprint
 * (app data directory vs. cache directory), independent of network traffic stats.
 */
object AppStorageUtils {

    /** Total size (bytes) of persistent app data: filesDir + no-backup files dir + databases. */
    suspend fun getAppDataSize(context: Context): Long = withContext(Dispatchers.IO) {
        runCatching {
            dirSize(context.filesDir) +
                dirSize(context.noBackupFilesDir) +
                dirSize(context.getDatabasePath("x").parentFile)
        }.getOrDefault(0L)
    }

    /** Total size (bytes) of cache: internal cacheDir + external cache dir (if present). */
    suspend fun getAppCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        runCatching {
            dirSize(context.cacheDir) + dirSize(context.externalCacheDir)
        }.getOrDefault(0L)
    }

    /** Deletes the contents of the cache directories (not the directories themselves). */
    suspend fun clearAppCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            var success = true
            success = deleteDirContents(context.cacheDir) && success
            success = deleteDirContents(context.externalCacheDir) && success
            success
        }.getOrDefault(false)
    }

    private fun dirSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { child ->
            size += if (child.isDirectory) dirSize(child) else child.length()
        }
        return size
    }

    private fun deleteDirContents(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        var allDeleted = true
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                allDeleted = deleteDirContents(child) && allDeleted
                allDeleted = child.delete() && allDeleted
            } else {
                allDeleted = child.delete() && allDeleted
            }
        }
        return allDeleted
    }
}
