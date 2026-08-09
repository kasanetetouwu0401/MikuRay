package com.v2ray.ang.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import com.v2ray.ang.AppConfig
import com.v2ray.ang.aidl.IMikuRayService
import com.v2ray.ang.aidl.IMikuRayServiceCallback
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.service.CoreProxyOnlyService
import com.v2ray.ang.service.CoreRootService
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.util.LogUtil

/**
 * Binds to whichever of [CoreVpnService] / [CoreProxyOnlyService] / [CoreRootService] is
 * active for the current run mode and exposes [IMikuRayService] over AIDL.
 *
 * Ported from Exclave's SagerConnection. This replaces the old
 * sendBroadcast()/registerReceiver() command channel described in the comment on
 * CoreServiceManager.serviceControl: that channel could silently drop MSG_STATE_STOP /
 * MSG_MEASURE_DELAY / MSG_REGISTER_CLIENT if the daemon's receiver wasn't registered yet
 * (or a SoftReference had been reclaimed). bindService() has no such window - the
 * connection is only ever considered "up" once the system has actually handed back a live
 * Binder, and command calls go straight through it instead of relying on a receiver.
 *
 * Deliberately does NOT use BIND_AUTO_CREATE-avoidance tricks: binding with
 * BIND_AUTO_CREATE while the tunnel isn't running just creates an idle Service instance
 * (onCreate() only sets CoreServiceManager.serviceControl, it never starts the core - that
 * only happens in onStartCommand()), so [IMikuRayService.getState] stays the source of
 * truth regardless of process lifecycle.
 */
class MikuRayConnection : ServiceConnection {
    companion object {
        private val serviceClass
            get() = when {
                SettingsManager.isRootMode() && RootManager.isRootAvailable() -> CoreRootService::class
                SettingsManager.isVpnMode() -> CoreVpnService::class
                else -> CoreProxyOnlyService::class
            }.java

        private val mainHandler = Handler(Looper.getMainLooper())
        private fun runOnMain(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
        }
    }

    interface Callback {
        fun onServiceConnected(service: IMikuRayService) {}
        fun onServiceDisconnected() {}

        /** See [MikuRayState] - msg is only ever non-null alongside [MikuRayState.Stopped]. */
        fun stateChanged(state: MikuRayState, msg: String?) {}
        fun measureDelayResult(result: String) {}
        fun measureIpResult(ip: String) {}
        fun trafficUpdated(guid: String) {}
        fun trafficSpeedUpdated(speedText: String) {}
    }

    private var connectionActive = false
    private var callbackRegistered = false
    private var callback: Callback? = null
    private var binder: IBinder? = null

    var service: IMikuRayService? = null
        private set

    private val serviceCallback = object : IMikuRayServiceCallback.Stub() {
        override fun stateChanged(state: Int, msg: String?) = runOnMain {
            callback?.stateChanged(MikuRayState.entries[state], msg)
        }

        override fun measureDelayResult(result: String) =
            runOnMain { callback?.measureDelayResult(result) }

        override fun measureIpResult(ip: String) = runOnMain { callback?.measureIpResult(ip) }
        override fun trafficUpdated(guid: String) = runOnMain { callback?.trafficUpdated(guid) }
        override fun trafficSpeedUpdated(speedText: String) =
            runOnMain { callback?.trafficSpeedUpdated(speedText) }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        this.binder = binder
        val service = IMikuRayService.Stub.asInterface(binder)
        this.service = service
        try {
            check(!callbackRegistered)
            service.registerCallback(serviceCallback)
            callbackRegistered = true
        } catch (e: RemoteException) {
            LogUtil.e(AppConfig.TAG, "MikuRayConnection: Failed to register callback", e)
        }
        callback?.onServiceConnected(service)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        callbackRegistered = false
        service = null
        binder = null
        callback?.onServiceDisconnected()
    }

    /**
     * Connects to the service for the current run mode. Safe to call repeatedly - a second
     * call while already connected is a no-op, matching the old startListenBroadcast()
     * contract used by MainViewModel/QSTileService.
     */
    fun connect(context: Context, callback: Callback) {
        if (connectionActive) return
        connectionActive = true
        this.callback = callback
        val intent = Intent(context, serviceClass).setAction(AppConfig.AIDL_SERVICE_ACTION)
        context.bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    fun disconnect(context: Context) {
        val service = service
        if (service != null && callbackRegistered) {
            try {
                service.unregisterCallback(serviceCallback)
            } catch (_: RemoteException) {
            }
        }
        callbackRegistered = false
        if (connectionActive) {
            try {
                context.unbindService(this)
            } catch (_: IllegalArgumentException) {
                // already unbound
            }
        }
        connectionActive = false
        this.service = null
        binder = null
        this.callback = null
    }
}
