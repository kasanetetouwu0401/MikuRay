package com.v2ray.ang.shizuku;

import com.v2ray.ang.shizuku.ICoreTetheringLease;

/**
 * Privileged Shizuku UserService running under the shell UID. Owns the Android
 * test-network TUN used as tethering's protected upstream, and a second HEV
 * tunnel instance that consumes it and forwards into MikuRay's SOCKS inbound.
 */
interface IShizukuTetheringService {
    /** @return one of the ROUTING_STATE_* constants below. */
    int getRoutingState() = 1;

    /** Human readable detail for the current state (last error, etc). */
    String getRoutingDetail() = 2;

    /** Bitmask of TETHERING_TYPE_* currently active on the device. */
    int getActiveTetheringTypes() = 3;

    /** Enable/disable the Wi-Fi hotspot directly. */
    int setWifiHotspotEnabled(boolean enabled) = 4;

    /**
     * Starts protected routing: creates the test-network TUN, starts a HEV instance
     * consuming it and forwarding to the given SOCKS endpoint, then prefers the test
     * network as tethering's upstream.
     */
    int startRouting(
        String hevConfigYaml,
        String profileName,
        boolean ipv6Enabled,
        String syncToken,
        ICoreTetheringLease lease
    ) = 5;

    /** Stops protected routing and tears down the test network. */
    int stopRouting() = 6;

    /**
     * Rebuilds the tethering datapath in place after a profile change or core restart,
     * reusing the existing TUN where possible.
     */
    int synchronizeRouting(
        String syncToken,
        String hevConfigYaml,
        String profileName,
        boolean ipv6Enabled,
        ICoreTetheringLease lease
    ) = 7;

    /** Sent right before the normal core stops; keeps clients fail-closed during the gap. */
    int notifyCoreStopping(String syncToken) = 8;

    /** Sent when the normal core failed to (re)start; routing cannot continue. */
    int notifyCoreStartFailed(String syncToken, String detail) = 9;

    /** Called once per session by the UI to pop the latest transient warning, if any. */
    String consumeWarning() = 10;

    /** Explicit shutdown request, e.g. app is being uninstalled/reset. */
    void destroy() = 16777114;
}
