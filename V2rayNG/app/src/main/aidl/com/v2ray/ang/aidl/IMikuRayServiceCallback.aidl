// Mirrors the old MSG_STATE_* / MSG_MEASURE_*_SUCCESS / MSG_TRAFFIC_* broadcast messages
// 1:1, so callers (MainViewModel, QSTileService) only need to swap the transport.
package com.v2ray.ang.aidl;

oneway interface IMikuRayServiceCallback {
    void stateRunning();
    void stateNotRunning();
    void stateStartSuccess();
    void stateStartFailure(String errorMessage);
    void stateStopSuccess();

    void measureDelayResult(String result);
    void measureIpResult(String ip);

    void trafficUpdated(String guid);
    void trafficSpeedUpdated(String speedText);
}
