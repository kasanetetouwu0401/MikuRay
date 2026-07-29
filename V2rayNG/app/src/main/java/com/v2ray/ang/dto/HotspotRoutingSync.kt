package com.v2ray.ang.dto

import java.io.Serializable

/**
 * Authenticated native-core lifecycle update, broadcast from the `:RunSoLibV2RayDaemon`
 * process to [com.v2ray.ang.shizuku.ShizukuRoutingSyncReceiver] in the main process.
 *
 * The [token] must match [com.v2ray.ang.AppConfig.PREF_SHIZUKU_SYNC_TOKEN]; a stale or
 * blank token is rejected so an old/duplicate broadcast can never resurrect a routing
 * session that the UI already tore down.
 */
data class HotspotRoutingSync(
    val token: String,
    val event: Int,
    val snapshot: HotspotRoutingSnapshot? = null,
    val detail: String = "",
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L

        const val EVENT_CORE_STOPPING = 1
        const val EVENT_CORE_STARTED = 2
        const val EVENT_CORE_START_FAILED = 3
    }
}
