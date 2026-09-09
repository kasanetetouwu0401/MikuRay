package com.miku.ray.aidl;

import com.miku.ray.aidl.IMikuRayCallback;

// Shared by CoreTestService, CountryCodeTestService and SubscriptionUpdateService.
// Replaces MessageUtil.sendMsg2UI(...) progress/result/finish broadcasts for these jobs.
interface IJobService {
    void registerCallback(IMikuRayCallback cb);
    oneway void unregisterCallback(IMikuRayCallback cb);
}
