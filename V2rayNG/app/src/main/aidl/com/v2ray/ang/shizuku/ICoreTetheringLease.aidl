package com.v2ray.ang.shizuku;

/**
 * Held by the shell-owned Shizuku UserService for the lifetime of a routing session.
 * The Binder itself is what matters: when the app/daemon process dies, this Binder
 * dies with it, and the UserService's DeathRecipient can react (keep TUN alive but
 * stop forwarding, i.e. fail closed) instead of silently leaking tethered traffic.
 */
interface ICoreTetheringLease {
    /** No-op liveness call; existence of the Binder connection is what is tracked. */
    void ping() = 1;
}
