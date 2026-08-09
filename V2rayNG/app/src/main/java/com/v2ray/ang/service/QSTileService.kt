package com.v2ray.ang.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.v2ray.ang.R
import com.v2ray.ang.aidl.IMikuRayService
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.core.MikuRayConnection
import com.v2ray.ang.core.MikuRayState
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
            val state = runCatching { MikuRayState.entries[service.state] }.getOrDefault(MikuRayState.Stopped)
            applyState(state)
        }

        override fun onServiceDisconnected() {
            applyState(MikuRayState.Idle)
        }

        override fun stateChanged(state: MikuRayState, msg: String?) {
            applyState(state)
        }
    }

    /**
     * Maps [MikuRayState] to the tile's Active/Inactive display - see [MikuRayState] for why
     * Connecting/Stopping being distinct from Idle matters: this used to eagerly guess
     * Tile.STATE_INACTIVE from CoreServiceManager.isRunning() in onStartListening() before
     * the connection resolved, which could flash the wrong state (the same class of bug
     * fixed on the FAB/test button - see MainViewModel.startListenBroadcast()). Now the tile
     * just keeps its last displayed state until a real one arrives.
     */
    private fun applyState(state: MikuRayState) {
        when (state) {
            MikuRayState.Connected -> setState(Tile.STATE_ACTIVE)
            MikuRayState.Stopped -> setState(Tile.STATE_INACTIVE)
            MikuRayState.Idle, MikuRayState.Connecting, MikuRayState.Stopping -> {}
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
