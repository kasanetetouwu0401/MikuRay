package com.miku.ray.aidl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

object AidlServiceClient {
    fun commandIfRunning(
        context: Context,
        serviceClass: Class<*>,
        command: Int,
        content: String = "",
        onResult: (Boolean) -> Unit = {},
    ): Boolean {
        return try {
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                    val service = IMikuRayService.Stub.asInterface(binder)
                    val handled = runCatching { service?.command(command, content) == true }.getOrDefault(false)
                    onResult(handled)
                    runCatching { context.unbindService(this) }
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    onResult(false)
                }
            }
            val bound = context.bindService(
                Intent(context, serviceClass).setAction(AidlProtocol.SERVICE_ACTION),
                connection,
                0,
            )
            if (!bound) onResult(false)
            bound
        } catch (_: Exception) {
            onResult(false)
            false
        }
    }

    fun startAndCommand(
        context: Context,
        serviceClass: Class<*>,
        command: Int,
        content: String = "",
    ): Boolean {
        return try {
            val intent = Intent(context, serviceClass).setAction(AidlProtocol.SERVICE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            OneShotCommandConnection(context.applicationContext, serviceClass, command, content).connect()
        } catch (_: Exception) {
            false
        }
    }

    private class OneShotCommandConnection(
        private val context: Context,
        private val serviceClass: Class<*>,
        private val command: Int,
        private val content: String,
    ) : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
            val service = IMikuRayService.Stub.asInterface(binder) ?: return
            runCatching { service.command(command, content) }
            runCatching { context.unbindService(this) }
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit

        fun connect(): Boolean = runCatching {
            context.bindService(
                Intent(context, serviceClass).setAction(AidlProtocol.SERVICE_ACTION),
                this,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
    }
}
