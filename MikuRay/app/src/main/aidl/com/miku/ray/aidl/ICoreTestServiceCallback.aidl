package com.miku.ray.aidl;

oneway interface ICoreTestServiceCallback {
    void onTestProgress(String json);
    void onTestResult(String json);
    void onTestFinish(String json);
}
