package com.miku.ray.aidl;

oneway interface ICoreServiceCallback {
    void stateChanged(int state, String profileName, String msg);
    void cbSpeedUpdate(String speedText);
    void cbTrafficUpdate(String guid);
    void cbMeasureDelayResult(String result);
    void cbMeasureIpResult(String ip);
}
