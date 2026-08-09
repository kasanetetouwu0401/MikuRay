package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MyContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class CoreProxyOnlyService : Service(), ServiceControl {
    // startCoreLoop() blocks on the native core startup; keep it off onStartCommand's
    // main thread so it doesn't stall the rest of the (same-process) app UI.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Tracks the in-flight startCoreLoop() job so stopService() can cancelAndJoin() it
    // before tearing down, instead of racing a still-connecting core (same reasoning as
    // CoreVpnService.connectJob - ported from SagerNet/Exclave's BaseService.stopRunner()).
    private var connectJob: Job? = null
    private val isStoppingLock = AtomicBoolean(false)

    /**
     * Initializes the service.
     */
    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Proxy: Service created")
        CoreServiceManager.serviceControl = this
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationManager.ensureForeground()
        LogUtil.i(AppConfig.TAG, "StartCore-Proxy: Service command received")
        connectJob = serviceScope.launch {
            CoreServiceManager.startCoreLoop(null)
        }
        return START_STICKY
    }

    /**
     * Destroys the service.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Safety net for when the service is killed/destroyed without going through
        // stopService() first (e.g. system low-memory kill). Guarded so it doesn't
        // double-run stopCoreLoop() when stopService() already handled it.
        if (isStoppingLock.compareAndSet(false, true)) {
            CoreServiceManager.stopCoreLoop()
        }
        CoreServiceManager.clearServiceControl(this)
        serviceScope.cancel()
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    override fun getService(): Service {
        return this
    }

    /**
     * Starts the service.
     */
    override fun startService() {
        // do nothing
    }

    /**
     * Stops the service.
     */
    override fun stopService() {
        serviceScope.launch {
            if (isStoppingLock.compareAndSet(false, true)) {
                // See connectJob above: wait for any in-flight connect to actually finish
                // before tearing down, so a disconnect tap can't land while startCoreLoop()
                // is still mid-flight (more likely with a slow-to-build custom routing
                // config) and tear the core down under it.
                connectJob?.cancelAndJoin()
                connectJob = null
                CoreServiceManager.stopCoreLoop()
            }
            stopSelf()
        }
    }

    /**
     * Protects the VPN socket.
     * @param socket The socket to protect.
     * @return True if the socket is protected, false otherwise.
     */
    override fun vpnProtect(socket: Int): Boolean {
        return true
    }

    /**
     * Binds the service.
     * @param intent The intent.
     * @return The binder.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Attaches the base context to the service.
     * @param newBase The new base context.
     */
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }
}
