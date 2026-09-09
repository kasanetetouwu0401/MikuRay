package com.miku.ray.aidl

import android.os.RemoteCallbackList
import android.os.RemoteException
import com.miku.ray.AppConfig
import com.miku.ray.util.LogUtil

/**
 * Small helper wrapping [RemoteCallbackList] so every service-side AIDL binder
 * (CoreServiceManager.Binder, CoreTestService.Binder, CountryCodeTestService.Binder,
 * SubscriptionUpdateService.Binder) shares the exact same, safe broadcast logic instead of
 * each one hand-rolling it. This is the direct replacement for MessageUtil.sendMsg2UI(...).
 */
class EventCallbackDispatcher {
    private val callbacks = RemoteCallbackList<IMikuRayCallback>()

    @Synchronized
    fun register(cb: IMikuRayCallback) {
        callbacks.register(cb)
    }

    @Synchronized
    fun unregister(cb: IMikuRayCallback) {
        callbacks.unregister(cb)
    }

    @Synchronized
    fun broadcastEvent(key: Int, content: String? = "") {
        val count = callbacks.beginBroadcast()
        try {
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i).onEvent(key, content.orEmpty())
                } catch (e: RemoteException) {
                    LogUtil.w(AppConfig.TAG, "EventCallbackDispatcher: callback died", e)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "EventCallbackDispatcher: callback failed", e)
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    @Synchronized
    fun kill() {
        callbacks.kill()
    }
}
