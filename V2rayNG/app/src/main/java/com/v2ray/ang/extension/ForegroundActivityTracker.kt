package com.v2ray.ang.extension

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Keeps a weak reference to the Activity that is currently in the foreground (resumed).
 *
 * This allows non-UI callers (Services, BroadcastReceivers, etc.) to still show a
 * Snackbar by borrowing the currently visible Activity's window, instead of having
 * to fall back to a plain Toast.
 *
 * Registered once from [com.v2ray.ang.AngApplication.onCreate].
 */
object ForegroundActivityTracker : Application.ActivityLifecycleCallbacks {

    private var resumedActivity: WeakReference<Activity>? = null

    /**
     * The currently resumed Activity, or null if the app has no Activity in the foreground
     * (e.g. fully backgrounded, or only a Service is running).
     */
    val currentActivity: Activity?
        get() = resumedActivity?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity?.get() === activity) {
            resumedActivity = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
