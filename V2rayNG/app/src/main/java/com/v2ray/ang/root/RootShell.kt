package com.v2ray.ang.root

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal privileged command runner. Backed by either the `su` binary or, when
 * [AppConfig.PREF_ROOT_BACKEND] is set to [AppConfig.ROOT_BACKEND_SHIZUKU], by Shizuku's
 * `newProcess` (run through reflection: the method is intentionally non-public in the
 * Shizuku API, see https://github.com/RikkaApps/Shizuku-API/issues/276). Either way the
 * caller gets the same [Result], so [RootProxyManager]'s iptables/tun scripts don't need to
 * know which backend is active.
 *
 * Scripts are written to the app's private root runtime dir (the app itself has normal
 * filesystem access there) and executed with `sh <file>` under the chosen backend, so shell
 * quoting stays simple. stderr is merged into stdout to avoid pipe-buffer deadlocks.
 */
object RootShell {

    data class Result(val code: Int, val output: String) {
        val success: Boolean get() = code == 0
    }

    private fun backend(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_ROOT_BACKEND) ?: AppConfig.ROOT_BACKEND_SU

    /** Write [script] to `<filesDir>/root/<name>` and run it under the configured backend. */
    fun runScript(context: Context, name: String, script: String): Result {
        val dir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR).apply { mkdirs() }
        val file = File(dir, name).apply {
            writeText(script)
            setExecutable(true, false)
        }
        return exec("sh ${file.absolutePath}")
    }

    fun exec(command: String, timeoutSeconds: Long = 30): Result {
        return if (backend() == AppConfig.ROOT_BACKEND_SHIZUKU) {
            execViaShizuku(command, timeoutSeconds)
        } else {
            execViaSu(command, timeoutSeconds)
        }
    }

    private fun execViaSu(command: String, timeoutSeconds: Long): Result {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                LogUtil.e(AppConfig.TAG, "RootShell(su): timed out: $command")
                return Result(-1, output)
            }
            val result = Result(process.exitValue(), output)
            if (!result.success) {
                LogUtil.w(AppConfig.TAG, "RootShell(su): '$command' exited ${result.code}: ${output.trim()}")
            }
            result
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "RootShell(su): failed to run '$command'", e)
            Result(-1, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun execViaShizuku(command: String, timeoutSeconds: Long): Result {
        return try {
            val process = ShizukuProcessCompat.newProcess(arrayOf("sh", "-c", command), null, null)
                ?: return Result(-1, "Shizuku: binder not available")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = waitForWithTimeout(process, timeoutSeconds)
            if (!finished) {
                process.destroy()
                LogUtil.e(AppConfig.TAG, "RootShell(shizuku): timed out: $command")
                return Result(-1, output)
            }
            val result = Result(process.exitValue(), output)
            if (!result.success) {
                LogUtil.w(AppConfig.TAG, "RootShell(shizuku): '$command' exited ${result.code}: ${output.trim()}")
            }
            result
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "RootShell(shizuku): failed to run '$command'", e)
            Result(-1, e.message ?: e.javaClass.simpleName)
        }
    }

    /** [rikka.shizuku.ShizukuRemoteProcess.waitFor] has no timeout overload, so poll exitValue(). */
    private fun waitForWithTimeout(process: Process, timeoutSeconds: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (e: IllegalThreadStateException) {
                Thread.sleep(50)
            }
        }
        return false
    }
}

