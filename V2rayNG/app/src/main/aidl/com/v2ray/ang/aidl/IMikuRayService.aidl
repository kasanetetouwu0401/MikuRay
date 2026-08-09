// Ported from Exclave's ISagerNetService.aidl, scoped to MikuRay's control set
// (start/stop/test/traffic). Runs across the app <-> :RunSoLibV2RayDaemon process
// boundary via bindService(), replacing the old sendBroadcast/registerReceiver channel.
package com.v2ray.ang.aidl;

import com.v2ray.ang.aidl.IMikuRayServiceCallback;

interface IMikuRayService {
    int getState();
    String getRunningServerName();

    void registerCallback(in IMikuRayServiceCallback cb);
    oneway void unregisterCallback(in IMikuRayServiceCallback cb);

    oneway void requestStop();
    oneway void requestRestart();
    oneway void measureDelay();
    oneway void measureIp();
}
