package com.miku.ray.aidl;

import com.miku.ray.aidl.IMikuRayCallback;

// Implemented by CoreServiceManager.Binder and exposed from CoreVpnService / CoreProxyOnlyService /
// CoreRootService's onBind(). Replaces the old BROADCAST_ACTION_SERVICE / BROADCAST_ACTION_ACTIVITY
// generic broadcast bus (MessageUtil.sendMsg2Service / sendMsg2UI) for the state+query channel.
interface IMikuRayService {
    // 1 = core running, 0 = not running.
    int getState();

    // Registering immediately replays the current running state to the new callback,
    // replacing the old MSG_REGISTER_CLIENT broadcast round-trip.
    void registerCallback(IMikuRayCallback cb);
    oneway void unregisterCallback(IMikuRayCallback cb);

    // Replaces sendMsg2Service(MSG_MEASURE_DELAY / MSG_MEASURE_IP, ...).
    oneway void requestMeasureDelay();
    oneway void requestMeasureIp();
}
