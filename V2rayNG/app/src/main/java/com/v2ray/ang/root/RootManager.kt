package com.v2ray.ang.root

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Detects whether the device grants privileged shell access through whichever backend
 * [AppConfig.PREF_ROOT_BACKEND] currently selects: the `su` binary, or Shizuku/Sui.
 *
 * For the `su` backend the result is cached after the first successful probe (probing spawns
 * `su` and blocks). For the Shizuku backend there's no process to spawn — availability is just
 * "is the binder alive and has permission been granted" — so it's cheap enough to check live
 * every time; nothing is cached for it.
 *
 * Note this only reports whether a privileged shell exists, not whether it's *root* — see
 * [isShizukuRootBacked] for that distinction, which [RootProxyManager] needs separately
 * because its iptables/ip rule/tun setup requires real root (uid 0), not just adb/shell (uid
 * 2000).
 */
object RootManager {

    @Volatile
    private var cachedSu: Boolean? = null

    private fun backend(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_ROOT_BACKEND) ?: AppConfig.ROOT_BACKEND_SU

    /** Last known result without probing. Defaults to false when never probed. */
    fun cachedRoot(): Boolean = when (backend()) {
        AppConfig.ROOT_BACKEND_SHIZUKU -> isShizukuAvailable()
        else -> cachedSu ?: false
    }

    /**
     * Returns whether a privileged shell is available, probing once if unknown (`su` backend)
     * or checking live (Shizuku backend).
     * May block while `su` is spawned; avoid calling on the main thread before a probe.
     */
    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        return when (backend()) {
            AppConfig.ROOT_BACKEND_SHIZUKU -> isShizukuAvailable()
            else -> {
                if (!forceRefresh) cachedSu?.let { return it }
                val result = probeSu()
                cachedSu = result
                result
            }
        }
    }

    /** Probes for privileged access off the main thread, updates the cache, and returns the result. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        when (backend()) {
            AppConfig.ROOT_BACKEND_SHIZUKU -> isShizukuAvailable()
            else -> {
                val result = probeSu()
                cachedSu = result
                result
            }
        }
    }

    /** Whether the currently selected backend is Shizuku and it's backed by real root (uid 0). */
    fun isShizukuRootBacked(): Boolean =
        backend() == AppConfig.ROOT_BACKEND_SHIZUKU && ShizukuManager.isRootBacked()

    /** Whether the user has selected Shizuku as the privileged-shell backend (regardless of its uid). */
    fun usesShizukuBackend(): Boolean = backend() == AppConfig.ROOT_BACKEND_SHIZUKU

    private fun isShizukuAvailable(): Boolean =
        ShizukuManager.isBinderAlive() && ShizukuManager.hasPermission()

    private fun probeSu(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id -u")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                LogUtil.w(AppConfig.TAG, "RootManager: su probe timed out")
                return false
            }
            val isRoot = process.exitValue() == 0 && output.lineSequence().lastOrNull()?.trim() == "0"
            LogUtil.i(AppConfig.TAG, "RootManager: root available = $isRoot")
            isRoot
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "RootManager: no root access (${e.message})")
            false
        }
    }
}
