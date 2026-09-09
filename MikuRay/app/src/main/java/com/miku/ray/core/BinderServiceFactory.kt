package com.miku.ray.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.miku.ray.AppConfig
import com.miku.ray.aidl.ICoreService
import com.miku.ray.aidl.ICoreServiceCallback
import com.miku.ray.handler.SettingsManager
import com.miku.ray.service.CoreProxyOnlyService
import com.miku.ray.service.CoreRootService
import com.miku.ray.service.CoreVpnService
import com.miku.ray.util.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NekoBox-style AIDL connection registry (the equivalent of SagerConnection).
 *
 * Holds one [Connection] per connection id, binds the mode-appropriate core
 * service (VPN / Root / ProxyOnly) and forwards binder callbacks to the
 * registered [Callback].
 */
object BinderServiceFactory {

    const val CONNECTION_ID_TILE = 1
    const val CONNECTION_ID_MAIN_ACTIVITY = 2
    const val CONNECTION_ID_STOP = 3
    const val CONNECTION_ID_RESTART = 4

    interface Callback {
        fun stateChanged(state: Int, profileName: String, msg: String)
        fun cbSpeedUpdate(speedText: String) {}
        fun cbTrafficUpdate(guid: String) {}
        fun cbMeasureDelayResult(result: String) {}
        fun cbMeasureIpResult(ip: String) {}
        fun onServiceConnected(service: ICoreService) {}
        fun onServiceDisconnected() {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

    private val connections = mutableMapOf<Int, Connection>()

    fun connect(
        context: Context,
        connectionId: Int,
        callback: Callback?,
        listenForDeath: Boolean = true,
    ): Connection {
        synchronized(connections) {
            connections[connectionId]?.let { existing ->
                existing.callback = callback
                return existing
            }
            val connection = Connection(connectionId, listenForDeath)
            connections[connectionId] = connection
            connection.callback = callback
            connection.connect(context)
            return connection
        }
    }

    fun disconnect(context: Context, connectionId: Int) {
        synchronized(connections) {
            connections.remove(connectionId)?.disconnect(context)
        }
    }

    fun getConnection(connectionId: Int): Connection? = synchronized(connections) {
        connections[connectionId]
    }

    fun coreServiceClass(): Class<*> = when {
        SettingsManager.isRootMode() -> CoreRootService::class.java
        SettingsManager.isVpnMode() -> CoreVpnService::class.java
        else -> CoreProxyOnlyService::class.java
    }

    private fun serviceClass(): Class<*> = coreServiceClass()

    inner class Connection(
        private val connectionId: Int,
        private val listenForDeath: Boolean,
    ) : ServiceConnection, IBinder.DeathRecipient {

        private val reconnecting = AtomicBoolean(false)
        private var connectionActive = false
        private var callbackRegistered = false
        private var appContext: Context? = null
        private var binder: IBinder? = null

        var callback: Callback? = null
        var service: ICoreService? = null
            private set

        private val serviceCallback = object : ICoreServiceCallback.Stub() {
            override fun stateChanged(state: Int, profileName: String?, msg: String?) {
                post {
                    callback?.stateChanged(state, profileName.orEmpty(), msg.orEmpty())
                }
            }

            override fun cbSpeedUpdate(speedText: String?) {
                post {
                    callback?.cbSpeedUpdate(speedText.orEmpty())
                }
            }

            override fun cbTrafficUpdate(guid: String?) {
                post {
                    callback?.cbTrafficUpdate(guid.orEmpty())
                }
            }

            override fun cbMeasureDelayResult(result: String?) {
                post {
                    callback?.cbMeasureDelayResult(result.orEmpty())
                }
            }

            override fun cbMeasureIpResult(ip: String?) {
                post {
                    callback?.cbMeasureIpResult(ip.orEmpty())
                }
            }
        }

        fun connect(context: Context) {
            if (connectionActive) return
            connectionActive = true
            appContext = context.applicationContext
            val intent = Intent(context, serviceClass())
            try {
                context.bindService(intent, this, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Binder: Failed to bind core service", e)
                connectionActive = false
            }
        }

        fun disconnect(context: Context) {
            unregisterCallback()
            if (connectionActive) {
                try {
                    context.unbindService(this)
                } catch (_: IllegalArgumentException) {
                }
            }
            connectionActive = false
            if (listenForDeath) {
                try {
                    binder?.unlinkToDeath(this, 0)
                } catch (_: NoSuchElementException) {
                }
            }
            binder = null
            service = null
            callback = null
        }

        fun notifyLocalStateChanged(state: Int, msg: String) {
            post {
                callback?.stateChanged(state, "", msg)
            }
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
            this.binder = binder
            val service = ICoreService.Stub.asInterface(binder) ?: return
            this.service = service
            try {
                if (listenForDeath) binder.linkToDeath(this, 0)
                check(!callbackRegistered)
                service.registerCallback(serviceCallback)
                callbackRegistered = true
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "Binder: Failed to register callback", e)
            }
            callback?.onServiceConnected(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            unregisterCallback()
            callback?.onServiceDisconnected()
            service = null
            binder = null
        }

        override fun binderDied() {
            service = null
            callbackRegistered = false
            if (!reconnecting.compareAndSet(false, true)) return
            post {
                try {
                    reconnect()
                } finally {
                    reconnecting.set(false)
                }
            }
        }

        private fun unregisterCallback() {
            val service = service
            if (service != null && callbackRegistered) {
                try {
                    service.unregisterCallback(serviceCallback)
                } catch (_: Exception) {
                }
            }
            callbackRegistered = false
        }

        private fun reconnect() {
            val context = appContext ?: return
            try {
                if (connectionActive) {
                    try {
                        context.unbindService(this)
                    } catch (_: IllegalArgumentException) {
                    }
                    connectionActive = false
                }
                LogUtil.i(AppConfig.TAG, "Binder: Reconnecting core service (connectionId=$connectionId)")
                connect(context)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Binder: Failed to reconnect core service", e)
            }
        }
    }
}
