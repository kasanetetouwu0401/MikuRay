package com.miku.ray.aidl

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.miku.ray.AppConfig
import com.miku.ray.handler.SettingsManager
import com.miku.ray.service.CoreProxyOnlyService
import com.miku.ray.service.CoreRootService
import com.miku.ray.service.CoreVpnService
import com.miku.ray.util.LogUtil

/**
 * Client side of the AIDL channel to whichever core service (Vpn/Proxy/Root) is active,
 * modeled after NekoBox's SagerConnection. Replaces the BroadcastReceiver +
 * MessageUtil.sendMsg2Service(MSG_REGISTER_CLIENT, ...) dance that used to live in
 * MainRepository / MainViewModel / QSTileService.
 */
class MikuRayConnection(
    private val onEvent: (key: Int, content: String) -> Unit,
) : ServiceConnection {

    companion object {
        /** Mirrors LauncherManager's own mode -> service class resolution. */
        val serviceClass: Class<out Service>
            get() = when {
                SettingsManager.isRootMode() -> CoreRootService::class.java
                SettingsManager.isVpnMode() -> CoreVpnService::class.java
                else -> CoreProxyOnlyService::class.java
            }
    }

    private val callback = object : IMikuRayCallback.Stub() {
        override fun onEvent(key: Int, content: String?) {
            this@MikuRayConnection.onEvent(key, content.orEmpty())
        }
    }

    @Volatile
    var service: IMikuRayService? = null
        private set

    @Volatile
    private var bound = false

    @Volatile
    private var callbackRegistered = false

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        val svc = IMikuRayService.Stub.asInterface(binder)
        service = svc
        try {
            svc.registerCallback(callback)
            callbackRegistered = true
        } catch (e: RemoteException) {
            LogUtil.e(AppConfig.TAG, "MikuRayConnection: failed to register callback", e)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        callbackRegistered = false
    }

    fun connect(context: Context) {
        if (bound) return
        val app = context.applicationContext
        val intent = Intent(app, serviceClass).setAction(AppConfig.ACTION_BIND_SERVICE)
        bound = try {
            app.bindService(intent, this, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "MikuRayConnection: bind failed", e)
            false
        }
    }

    fun disconnect(context: Context) {
        if (!bound) return
        val app = context.applicationContext
        val svc = service
        if (svc != null && callbackRegistered) {
            try {
                svc.unregisterCallback(callback)
            } catch (_: RemoteException) {
            }
        }
        try {
            app.unbindService(this)
        } catch (_: IllegalArgumentException) {
        }
        bound = false
        callbackRegistered = false
        service = null
    }

    /** Returns 1 if the core is running, 0 otherwise (mirrors ISagerNetService.getState()). */
    fun getState(): Int = try {
        service?.getState() ?: 0
    } catch (e: RemoteException) {
        0
    }

    fun requestMeasureDelay() {
        try {
            service?.requestMeasureDelay()
        } catch (e: RemoteException) {
            LogUtil.e(AppConfig.TAG, "MikuRayConnection: requestMeasureDelay failed", e)
        }
    }

    fun requestMeasureIp() {
        try {
            service?.requestMeasureIp()
        } catch (e: RemoteException) {
            LogUtil.e(AppConfig.TAG, "MikuRayConnection: requestMeasureIp failed", e)
        }
    }
}
