package com.miku.ray.aidl;

import com.miku.ray.aidl.ICountryCodeTestServiceCallback;

interface ICountryCodeTestService {
    void registerCallback(in ICountryCodeTestServiceCallback cb);
    oneway void unregisterCallback(in ICountryCodeTestServiceCallback cb);

    void cancelTest();
}
