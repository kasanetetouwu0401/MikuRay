package com.v2ray.ang.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.HotspotRoutingSync
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the app-process side of Shizuku tethering: permission state, the bound
 * [IShizukuTetheringService] connection, and the last [HotspotRoutingSync] snapshot
 * received from the daemon process. [com.v2ray.ang.ui.ShizukuActivity] observes this
 * object; it does not talk to Shizuku directly.
 */
object ShizukuTetheringController {

    private const val USER_SERVICE_VERSION = 1

    fun interface StateListener {
        fun onStateChanged()
    }

    private val listeners = CopyOnWriteArrayList<StateListener>()
    private var binder: IShizukuTetheringService? = null
    private var lastSnapshot: HotspotRoutingSync? = null
    var lastWarning: String? = null
        private set

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShizukuTetheringService::class.java.name))
            .daemon(true)
            .processNameSuffix("shizuku_tethering")
            .debuggable(BuildConfig.DEBUG)
            .version(USER_SERVICE_VERSION)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service?.let { IShizukuTetheringService.Stub.asInterface(it) }
            notifyListeners()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            notifyListeners()
        }
    }

    fun addListener(listener: StateListener) = listeners.add(listener)
    fun removeListener(listener: StateListener) = listeners.remove(listener)

    fun isShizukuInstalled(): Boolean = try {
        Shizuku.pingBinder()
        true
    } catch (_: Throwable) {
        false
    }

    fun isShizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "ShizukuTetheringController: requestPermission failed", e)
        }
    }

    fun bind(context: Context) {
        if (binder != null) return
        if (!hasPermission()) return
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "ShizukuTetheringController: bind failed", e)
        }
    }

    fun unbind() {
        try {
            binder?.destroy()
        } catch (_: Throwable) {
        }
        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        } catch (_: Throwable) {
        }
        binder = null
        MmkvManager.encodeSettings(AppConfig.PREF_SHIZUKU_SYNC_TOKEN, "")
        notifyListeners()
    }

    fun routingState(): Int = try {
        binder?.routingState ?: ShizukuTetheringService.ROUTING_STATE_IDLE
    } catch (_: Throwable) {
        ShizukuTetheringService.ROUTING_STATE_ERROR
    }

    fun routingDetail(): String = try {
        binder?.routingDetail.orEmpty()
    } catch (_: Throwable) {
        ""
    }

    fun activeTetheringTypes(): Int = try {
        binder?.activeTetheringTypes ?: 0
    } catch (_: Throwable) {
        0
    }

    fun setWifiHotspotEnabled(enabled: Boolean): Boolean = try {
        binder?.setWifiHotspotEnabled(enabled) == 0
    } catch (e: Throwable) {
        LogUtil.e(AppConfig.TAG, "ShizukuTetheringController: setWifiHotspotEnabled failed", e)
        false
    }

    fun lastProfileName(): String = lastSnapshot?.snapshot?.profileName.orEmpty()

    /** Invoked by [ShizukuRoutingSyncReceiver] with each authenticated lifecycle event. */
    fun onCoreSync(context: Context, sync: HotspotRoutingSync, lease: ICoreTetheringLease?) {
        val storedToken = MmkvManager.decodeSettingsString(AppConfig.PREF_SHIZUKU_SYNC_TOKEN).orEmpty()
        if (storedToken.isBlank() || sync.token != storedToken) return
        lastSnapshot = sync

        if (binder == null) {
            bind(context)
        }
        val service = binder ?: run { notifyListeners(); return }

        try {
            when (sync.event) {
                HotspotRoutingSync.EVENT_CORE_STOPPING -> {
                    service.notifyCoreStopping(sync.token)
                }

                HotspotRoutingSync.EVENT_CORE_STARTED -> {
                    val snapshot = sync.snapshot ?: return
                    val config = HotspotRoutingConfig.fromSnapshot(snapshot) ?: return
                    val alreadyActive = service.routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE
                    val result = if (alreadyActive) {
                        service.synchronizeRouting(sync.token, config.hevYaml, config.profileName, snapshot.ipv6Enabled, lease)
                    } else {
                        service.startRouting(config.hevYaml, config.profileName, snapshot.ipv6Enabled, sync.token, lease)
                    }
                    if (result != 0) {
                        lastWarning = service.consumeWarning().ifBlank { "tethering_start_failed" }
                    }
                }

                HotspotRoutingSync.EVENT_CORE_START_FAILED -> {
                    service.notifyCoreStartFailed(sync.token, sync.detail)
                }
            }
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "ShizukuTetheringController: sync forwarding failed", e)
        }
        notifyListeners()
    }

    fun onAppForegrounded(context: Context) {
        if (binder == null && hasPermission()) bind(context)
    }

    private fun notifyListeners() {
        listeners.forEach { it.onStateChanged() }
    }
}
