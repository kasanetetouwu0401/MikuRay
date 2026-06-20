package com.v2ray.ang.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which app UIDs currently have live connections passing through Xray core's TUN.
 *
 * Fed directly by [com.v2ray.ang.core.CoreServiceManager]'s `XrayProcessFinder`, which the core
 * calls for every new connection it routes (`registerProcessFinder`). This means "active" here
 * reflects real proxied traffic from the core, not a guess derived from OS-wide traffic counters.
 *
 * The core only reports *that* a UID opened a connection, not how many bytes it transferred,
 * so this tracker answers "is this app active right now" — byte counts still come from
 * [AppTrafficUtil.snapshotUidTraffic].
 */
object UidActivityTracker {

    // How recently a UID must have had a connection resolved to still count as "active".
    private const val ACTIVE_WINDOW_MS = 4000L

    private val lastSeenByUid = ConcurrentHashMap<Long, Long>()

    /** Called by [com.v2ray.ang.core.CoreServiceManager]'s ProcessFinder for every resolved connection. */
    fun markActive(uid: Long) {
        if (uid < 0L) return // unresolved/unidentified connection
        lastSeenByUid[uid] = System.currentTimeMillis()
    }

    /** Whether [uid] has had a connection resolved within the active window. */
    fun isActive(uid: Int, now: Long = System.currentTimeMillis()): Boolean {
        val lastSeen = lastSeenByUid[uid.toLong()] ?: return false
        return (now - lastSeen) <= ACTIVE_WINDOW_MS
    }

    /** Clears all tracked state, e.g. when the VPN/core service stops. */
    fun clear() {
        lastSeenByUid.clear()
    }
}
