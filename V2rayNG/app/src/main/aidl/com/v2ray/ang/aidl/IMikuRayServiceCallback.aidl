// Mirrors Exclave's ISagerNetServiceCallback.stateChanged(): a single state transition push
// (see MikuRayState/CoreServiceManager.Binder) instead of one method per old MSG_STATE_*
// broadcast key, so a client can never observe an undefined/ambiguous state.
package com.v2ray.ang.aidl;

oneway interface IMikuRayServiceCallback {
    void stateChanged(int state, String msg);

    void measureDelayResult(String result);
    void measureIpResult(String ip);

    void trafficUpdated(String guid);
    void trafficSpeedUpdated(String speedText);
}
