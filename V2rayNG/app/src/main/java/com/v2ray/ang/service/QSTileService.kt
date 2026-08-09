package com.v2ray.ang.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.v2ray.ang.R
import com.v2ray.ang.aidl.IMikuRayService
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.core.MikuRayConnection
import com.v2ray.ang.util.AppNameHelper

class QSTileService : TileService() {

    /**
     * Ported from Exclave/SagerConnection via MikuRayConnection. Replaces the old
     * SoftReference<QSTileService>-held ReceiveMessageHandler - see the comment on
     * CoreServiceManager.serviceControl and MikuRayConnection's class doc for why that
     * pattern could silently stop updating this tile once the reference was reclaimed.
     * onStartListening()/onStopListening() already have the same connect/disconnect
     * symmetry as bindService()/unbindService(), so this maps across 1:1.
     */
    private val connection = MikuRayConnection()
    private val connectionCallback = object : MikuRayConnection.Callback {
        override fun onServiceConnected(service: IMikuRayService) {
            val running = runCatching { service.isRunning }.getOrDefault(false)
            setState(if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE)
        }

        override fun onServiceDisconnected() {
            setState(Tile.STATE_INACTIVE)
        }

        override fun stateStartSuccess() {
            setState(Tile.STATE_ACTIVE)
        }

        override fun stateStartFailure(errorMessage: String) {
            setState(Tile.STATE_INACTIVE)
        }

        override fun stateStopSuccess() {
            setState(Tile.STATE_INACTIVE)
        }
    }

    /**
     * Sets the state of the tile.
     * @param state The state to set.
     */
    fun setState(state: Int) {
        qsTile?.icon = Icon.createWithResource(applicationContext, R.drawable.ic_stat_name)
        if (state == Tile.STATE_INACTIVE) {
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.label = AppNameHelper.getDisplayName(applicationContext)
        } else if (state == Tile.STATE_ACTIVE) {
            qsTile?.state = Tile.STATE_ACTIVE
            qsTile?.label = CoreServiceManager.getRunningServerName()
        }

        qsTile?.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()

        if (CoreServiceManager.isRunning()) {
            setState(Tile.STATE_ACTIVE)
        } else {
            setState(Tile.STATE_INACTIVE)
        }
        connection.connect(applicationContext, connectionCallback)
    }

    /**
     * Called when the tile stops listening.
     */
    override fun onStopListening() {
        super.onStopListening()
        connection.disconnect(applicationContext)
    }

    /**
     * Called when the tile is clicked.
     */
    override fun onClick() {
        super.onClick()
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> {
                LauncherManager.startServiceFromToggle(this)
            }

            Tile.STATE_ACTIVE -> {
                LauncherManager.stopService(this)
            }
        }
    }
}
