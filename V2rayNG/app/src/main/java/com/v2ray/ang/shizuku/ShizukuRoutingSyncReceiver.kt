package com.v2ray.ang.shizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.dto.HotspotRoutingSync

/**
 * Receives [HotspotRoutingSync] broadcasts from the `:RunSoLibV2RayDaemon` core process
 * and forwards them to [ShizukuTetheringController], which owns the actual bound
 * connection to the shell UserService.
 *
 * Registered `android:exported="false"` in the manifest and only ever targeted with an
 * explicit, package-scoped [Intent] from [TetheringCoreSync], so no other app can spoof
 * lifecycle events into this receiver.
 */
class ShizukuRoutingSyncReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SYNC = "com.v2ray.ang.action.SHIZUKU_ROUTING_SYNC"
        const val EXTRA_SYNC = "extra_sync"
        const val EXTRA_LEASE = "extra_lease"
        const val EXTRA_LEASE_BUNDLE = "extra_lease_bundle"
        const val EXTRA_FOREGROUND_NUDGE = "extra_foreground_nudge"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SYNC) return

        if (intent.getBooleanExtra(EXTRA_FOREGROUND_NUDGE, false)) {
            ShizukuTetheringController.onAppForegrounded(context)
            return
        }

        @Suppress("DEPRECATION")
        val sync = intent.getSerializableExtra(EXTRA_SYNC) as? HotspotRoutingSync ?: return
        val leaseBinder = intent.getBundleExtra(EXTRA_LEASE_BUNDLE)?.getBinder(EXTRA_LEASE)
        val lease = leaseBinder?.let { ICoreTetheringLease.Stub.asInterface(it) }

        ShizukuTetheringController.onCoreSync(context.applicationContext, sync, lease)
    }
}
