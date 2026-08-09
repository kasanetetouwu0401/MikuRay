package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.TrafficController
import com.v2ray.ang.root.RootProxyManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

class CoreRootService : Service(), ServiceControl {

    private var isRunning = false
    private var setupJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service created")
        CoreServiceManager.serviceControl = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationManager.ensureForeground()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service command received")
        NotificationManager.showNotification(null)
        TrafficController.start()

        setupJob = CoroutineScope(Dispatchers.IO).launch {
            if (!CoreServiceManager.startCoreLoop(null)) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: Failed to start core loop")
                stopAllService()
                return@launch
            }

            isRunning = true

            CoroutineScope(Dispatchers.Main).launch {
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SOUND_ON_CONNECT, true)) {
                    try { 
                        SoundPlayer.playConnect(this@CoreRootService) 
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "StartCore-Root: Sound error", e)
                    }
                }
            }

            try {
                RootProxyManager.start(this@CoreRootService)
                LogUtil.i(AppConfig.TAG, "StartCore-Root: iptables/tun/hev setup complete")
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: setup failed", e)
                stopAllService()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service destroyed")
        CoreServiceManager.clearServiceControl(this)
        if (isRunning) {
            stopAllService(isForced = false)
        }
    }

    override fun getService(): Service = this

    override fun startService() {
    }

    override fun stopService() {
        stopAllService()
    }

    override fun vpnProtect(socket: Int): Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    private fun stopAllService(isForced: Boolean = true) {
        isRunning = false
        
        CoroutineScope(Dispatchers.Main).launch {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SOUND_ON_CONNECT, true)) {
                try { 
                    SoundPlayer.playDisconnect(this@CoreRootService) 
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Root: Sound disconnect error", e)
                }
            }
        }

        val jobToCancel = setupJob
        setupJob = null

        CoroutineScope(Dispatchers.IO).launch {
            try { 
                jobToCancel?.cancelAndJoin() 
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: Failed to cancel setup job", e)
            }
            try {
                RootProxyManager.stopFull(this@CoreRootService)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: teardown error", e)
            }
        }

        try {
            CoreServiceManager.stopCoreLoop()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Root: Failed to stop core loop", e)
            try { MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_STOP_SUCCESS, "") } catch (ex: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: Failed to send force stop msg", ex)
            }
        }

        if (isForced) {
            try { 
                stopSelf() 
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: Failed to stop self", e)
            }
        }
    }
}
