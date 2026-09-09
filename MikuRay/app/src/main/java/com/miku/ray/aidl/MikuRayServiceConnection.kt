package com.miku.ray.aidl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.miku.ray.util.LogUtil

class MikuRayServiceConnection(
    private val context: Context,
    private val serviceClass: Class<*>,
    private val autoCreate: Boolean,
    private val onEvent: (Int, String) -> Unit = { _, _ -> },
    private val onConnected: (IMikuRayService) -> Unit = {},
    private val onDisconnected: () -> Unit = {},
) : ServiceConnection {

    private var bound = false
    var service: IMikuRayService? = null
        private set

    private val callback = object : IMikuRayServiceCallback.Stub() {
        override fun onEvent(event: Int, content: String?) {
            onEvent(event, content.orEmpty())
        }
    }

    fun connect(): Boolean {
        if (bound) return true
        val intent = Intent(context, serviceClass).setAction(AidlProtocol.SERVICE_ACTION)
        val flags = if (autoCreate) Context.BIND_AUTO_CREATE else 0
        return try {
            bound = context.bindService(intent, this, flags)
            bound
        } catch (e: Exception) {
            LogUtil.e(com.miku.ray.AppConfig.TAG, "Failed to bind ${serviceClass.simpleName}", e)
            bound = false
            false
        }
    }

    fun command(command: Int, content: String = ""): Boolean = try {
        service?.command(command, content) == true
    } catch (e: RemoteException) {
        LogUtil.e(com.miku.ray.AppConfig.TAG, "AIDL command failed: $command", e)
        false
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        val remote = IMikuRayService.Stub.asInterface(binder) ?: return
        service = remote
        try {
            remote.registerCallback(callback)
            when (remote.getState()) {
                1 -> onEvent(AidlProtocol.EVENT_STATE_RUNNING, remote.getProfileName())
                else -> onEvent(AidlProtocol.EVENT_STATE_NOT_RUNNING, remote.getProfileName())
            }
        } catch (e: RemoteException) {
            LogUtil.e(com.miku.ray.AppConfig.TAG, "Failed to initialize AIDL callback", e)
        }
        onConnected(remote)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        unregister()
        bound = false
        service = null
        onDisconnected()
    }

    fun disconnect() {
        unregister()
        if (bound) {
            runCatching { context.unbindService(this) }
        }
        bound = false
        service = null
    }

    private fun unregister() {
        service?.let { remote ->
            runCatching { remote.unregisterCallback(callback) }
        }
    }
}
