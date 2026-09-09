package com.miku.ray.core

import com.miku.ray.aidl.AidlProtocol
import com.miku.ray.aidl.MikuRayServiceBinder

class CoreAidlBinder : MikuRayServiceBinder(
    stateProvider = { if (CoreServiceManager.isRunning()) 1 else 0 },
    profileNameProvider = { CoreServiceManager.getRunningServerName() },
    commandHandler = { command, content ->
        CoreServiceManager.handleAidlCommand(command, content)
    },
) {
    fun emitStateRunning() = emit(AidlProtocol.EVENT_STATE_RUNNING)
}
