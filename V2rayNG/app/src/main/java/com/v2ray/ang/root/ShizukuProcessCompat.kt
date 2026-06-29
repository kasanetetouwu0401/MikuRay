package com.v2ray.ang.root

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * Thin wrapper around `Shizuku#newProcess`, which spawns a process under whatever privilege
 * Shizuku's backend currently has (root or shell/adb).
 *
 * The Shizuku maintainer has announced intent to remove this method in favor of
 * `UserService` (a bound, long-lived privileged process — more powerful, but considerably
 * more ceremony for what is, here, just running a handful of shell scripts). Depending on the
 * installed Shizuku version this method may already throw "method ... is not visible" when
 * called directly, so it's invoked via reflection with `isAccessible = true` rather than as a
 * normal Kotlin call — that keeps working whether the method is merely `@Deprecated` (older
 * Shizuku) or has been demoted to non-public (newer Shizuku). If a future release removes the
 * method outright, [newProcessMethod] resolves to null and every call site here fails closed
 * (treated the same as "binder not available") instead of crashing.
 * Tracking issue: https://github.com/RikkaApps/Shizuku-API/issues/276
 */
object ShizukuProcessCompat {

    private val newProcessMethod by lazy {
        try {
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                .apply { isAccessible = true }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ShizukuProcessCompat: newProcess not found", e)
            null
        }
    }

    /**
     * @param command argv, e.g. `arrayOf("sh", "-c", "id -u")`
     * @param env optional `KEY=VALUE` environment overrides; null inherits Shizuku's own.
     * @param dir optional working directory; null uses Shizuku's default.
     * @return a [Process]-like [ShizukuRemoteProcess], or null if the binder isn't available
     *   or reflection failed.
     */
    fun newProcess(command: Array<String>, env: Array<String>?, dir: String?): ShizukuRemoteProcess? {
        if (!ShizukuManager.isBinderAlive()) return null
        val method = newProcessMethod ?: return null
        return try {
            method.invoke(null, command, env, dir) as ShizukuRemoteProcess
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ShizukuProcessCompat: newProcess invoke failed", e)
            null
        }
    }
}
