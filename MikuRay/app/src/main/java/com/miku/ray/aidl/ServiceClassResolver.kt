package com.miku.ray.aidl

import com.miku.ray.handler.SettingsManager
import com.miku.ray.service.CoreProxyOnlyService
import com.miku.ray.service.CoreRootService
import com.miku.ray.service.CoreVpnService

object ServiceClassResolver {
    fun coreServiceClass(): Class<*> = when {
        SettingsManager.isRootMode() -> CoreRootService::class.java
        SettingsManager.isVpnMode() -> CoreVpnService::class.java
        else -> CoreProxyOnlyService::class.java
    }
}
