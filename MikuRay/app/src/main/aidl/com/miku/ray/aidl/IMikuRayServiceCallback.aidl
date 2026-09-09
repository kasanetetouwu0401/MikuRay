package com.miku.ray.aidl;

oneway interface IMikuRayServiceCallback {
    void onEvent(int event, String content);
}
