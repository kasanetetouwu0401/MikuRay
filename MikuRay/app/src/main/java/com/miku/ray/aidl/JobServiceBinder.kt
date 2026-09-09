package com.miku.ray.aidl

/**
 * Shared IJobService.Stub implementation used by CoreTestService, CountryCodeTestService and
 * SubscriptionUpdateService. Each service owns one instance and calls [broadcastEvent] wherever
 * it used to call MessageUtil.sendMsg2UI(...).
 */
class JobServiceBinder : IJobService.Stub() {
    private val dispatcher = EventCallbackDispatcher()

    override fun registerCallback(cb: IMikuRayCallback) {
        dispatcher.register(cb)
    }

    override fun unregisterCallback(cb: IMikuRayCallback) {
        dispatcher.unregister(cb)
    }

    fun broadcastEvent(key: Int, content: String? = "") {
        dispatcher.broadcastEvent(key, content)
    }

    fun close() {
        dispatcher.kill()
    }
}
