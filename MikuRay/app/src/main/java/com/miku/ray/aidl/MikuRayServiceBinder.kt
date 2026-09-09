package com.miku.ray.aidl

import android.os.RemoteCallbackList
import android.os.RemoteException

open class MikuRayServiceBinder(
    private val stateProvider: () -> Int = { 0 },
    private val profileNameProvider: () -> String = { "" },
    private val commandHandler: (Int, String) -> Boolean,
) : IMikuRayService.Stub() {

    private val callbacks = RemoteCallbackList<IMikuRayServiceCallback>()

    override fun getState(): Int = stateProvider()

    override fun getProfileName(): String = profileNameProvider()

    override fun command(command: Int, content: String?): Boolean =
        commandHandler(command, content.orEmpty())

    override fun registerCallback(callback: IMikuRayServiceCallback) {
        callbacks.register(callback)
    }

    override fun unregisterCallback(callback: IMikuRayServiceCallback) {
        callbacks.unregister(callback)
    }

    fun emit(event: Int, content: String = "") {
        val count = callbacks.beginBroadcast()
        try {
            repeat(count) { index ->
                try {
                    callbacks.getBroadcastItem(index).onEvent(event, content)
                } catch (_: RemoteException) {
                } catch (_: Exception) {
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    fun close() {
        callbacks.kill()
    }
}
