package com.miku.ray.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miku.ray.aidl.AidlProtocol
import com.miku.ray.aidl.AidlServiceClient
import com.miku.ray.aidl.ServiceClassResolver

class ServiceCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val command = when (intent?.action) {
            ACTION_STOP -> AidlProtocol.CORE_STOP
            ACTION_RESTART -> AidlProtocol.CORE_RESTART
            else -> return
        }
        AidlServiceClient.commandIfRunning(
            context,
            ServiceClassResolver.coreServiceClass(),
            command,
        )
    }

    companion object {
        const val ACTION_STOP = "com.miku.ray.action.STOP_SERVICE"
        const val ACTION_RESTART = "com.miku.ray.action.RESTART_SERVICE"
    }
}
