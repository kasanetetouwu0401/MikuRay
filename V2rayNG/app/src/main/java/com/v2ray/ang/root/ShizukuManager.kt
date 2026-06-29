package com.v2ray.ang.root

import android.content.pm.PackageManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Tracks the Shizuku binder lifecycle and exposes permission/availability checks.
 *
 * Shizuku's privilege depends entirely on how the *user* started it: backed by `adb`
 * (wireless debugging or a one-time USB command) it runs as the shell uid (2000); backed by
 * Sui/Magisk it runs as root (uid 0). [getUid] is the only reliable way to tell which one is
 * active — see [com.v2ray.ang.root.RootProxyManager], which needs real root (uid 0) for
 * iptables/ip rule/tun and will refuse to start under a shell-only Shizuku session.
 */
object ShizukuManager {

    private const val TAG = AppConfig.TAG
    private const val REQUEST_CODE = 9001

    @Volatile
    private var binderAlive = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        binderAlive = true
        LogUtil.i(TAG, "ShizukuManager: binder received")
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        binderAlive = false
        LogUtil.w(TAG, "ShizukuManager: binder dead")
    }

    private var listenersAttached = false

    /** Registers the binder listeners once. Safe to call repeatedly (e.g. from Application.onCreate). */
    fun init() {
        if (listenersAttached) return
        listenersAttached = true
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    /** Whether the Shizuku/Sui app is installed at all (a prerequisite, not sufficient on its own). */
    fun isAppInstalled(context: android.content.Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(AppConfig.SHIZUKU_PACKAGE_NAME, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** True once a live binder has been received from Shizuku or Sui. */
    fun isBinderAlive(): Boolean = binderAlive && Shizuku.pingBinder()

    /**
     * The uid Shizuku's privileged process runs as: 0 when backed by root (Sui/Magisk or
     * Shizuku started via a rooted `su`), 2000 when backed by plain adb/shell.
     * Returns -1 when the binder isn't alive yet.
     */
    fun getUid(): Int {
        if (!isBinderAlive()) return -1
        return try {
            Shizuku.getUid()
        } catch (e: Exception) {
            LogUtil.w(TAG, "ShizukuManager: getUid failed (${e.message})")
            -1
        }
    }

    /** Whether Shizuku is currently backed by root, i.e. iptables/ip rule/tun setup will work. */
    fun isRootBacked(): Boolean = getUid() == 0

    fun hasPermission(): Boolean {
        if (!isBinderAlive()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Requests the Shizuku permission if needed and suspends until the user responds.
     * Must be called after [isBinderAlive] is true. Safe to call from any dispatcher; the
     * Shizuku result listener fires on the main thread regardless.
     */
    suspend fun requestPermission(): Boolean {
        if (hasPermission()) return true
        if (!isBinderAlive()) return false
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            // The user denied it before and asked not to be asked again.
            return false
        }
        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }
}
