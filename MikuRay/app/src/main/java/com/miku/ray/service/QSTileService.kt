package com.miku.ray.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.aidl.AidlProtocol
import com.miku.ray.aidl.MikuRayServiceConnection
import com.miku.ray.aidl.ServiceClassResolver
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.core.LauncherManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.ui.shortcut.ScStartActivity

class QSTileService : TileService() {

    private var serviceConnection: MikuRayServiceConnection? = null

    fun setState(state: Int) {
        qsTile?.icon = Icon.createWithResource(applicationContext, R.drawable.ic_stat_name)
        if (state == Tile.STATE_INACTIVE) {
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.label = com.miku.ray.util.AppNameHelper.getDisplayName(applicationContext)
        } else if (state == Tile.STATE_ACTIVE) {
            qsTile?.state = Tile.STATE_ACTIVE
            qsTile?.label = CoreServiceManager.getRunningServerName()
        }
        qsTile?.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        setState(if (CoreServiceManager.isRunning()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE)
        serviceConnection?.disconnect()
        serviceConnection = MikuRayServiceConnection(
            context = applicationContext,
            serviceClass = ServiceClassResolver.coreServiceClass(),
            autoCreate = false,
            onEvent = { event, _ ->
                when (event) {
                    AidlProtocol.EVENT_STATE_RUNNING,
                    AidlProtocol.EVENT_STATE_START_SUCCESS -> setState(Tile.STATE_ACTIVE)
                    AidlProtocol.EVENT_STATE_NOT_RUNNING,
                    AidlProtocol.EVENT_STATE_START_FAILURE,
                    AidlProtocol.EVENT_STATE_STOP_SUCCESS -> setState(Tile.STATE_INACTIVE)
                }
            },
        ).also { it.connect() }
    }

    override fun onStopListening() {
        serviceConnection?.disconnect()
        serviceConnection = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> {
                if (isLocked) {
                    unlockAndRun { startServiceWithActivityFallback() }
                } else {
                    startServiceWithActivityFallback()
                }
            }
            Tile.STATE_ACTIVE -> LauncherManager.stopService(this)
        }
    }

    private fun startServiceWithActivityFallback() {
        val needsVpnConsent = SettingsManager.isVpnMode() && VpnService.prepare(this) != null
        if (needsVpnConsent || !LauncherManager.startServiceFromToggle(this)) {
            startViaShortcutActivity()
        }
    }

    private fun startViaShortcutActivity() {
        val intent = Intent(this, ScStartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    REQUEST_CODE_START_FROM_TILE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val REQUEST_CODE_START_FROM_TILE = 4_104
    }
}
