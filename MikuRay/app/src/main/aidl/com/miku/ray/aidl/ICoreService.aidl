package com.miku.ray.aidl;

import com.miku.ray.aidl.ICoreServiceCallback;

interface ICoreService {
    int getState();
    String getProfileName();

    void registerCallback(in ICoreServiceCallback cb);
    oneway void unregisterCallback(in ICoreServiceCallback cb);

    boolean requestRestart();
    void stopCore();
    void measureDelay();
    void measureIpOnly();
}
