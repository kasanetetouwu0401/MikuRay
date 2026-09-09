package com.miku.ray.aidl;

import com.miku.ray.aidl.IMikuRayServiceCallback;

interface IMikuRayService {
    int getState();
    String getProfileName();
    boolean command(int command, String content);
    void registerCallback(in IMikuRayServiceCallback callback);
    oneway void unregisterCallback(in IMikuRayServiceCallback callback);
}
