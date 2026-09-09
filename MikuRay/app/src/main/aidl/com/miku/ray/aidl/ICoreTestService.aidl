package com.miku.ray.aidl;

import com.miku.ray.aidl.ICoreTestServiceCallback;

interface ICoreTestService {
    void registerCallback(in ICoreTestServiceCallback cb);
    oneway void unregisterCallback(in ICoreTestServiceCallback cb);

    void cancelTest(String testId);
}
