package com.miku.ray.aidl

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.miku.ray.AppConfig
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Client side of the AIDL channel to a background job service (CoreTestService,
 * CountryCodeTestService or SubscriptionUpdateService). Replaces the BroadcastReceiver that
 * MainViewModel used to register for BROADCAST_ACTION_ACTIVITY to receive
 * MSG_MEASURE_CONFIG_*/MSG_COUNTRY_CODE_* progress events.
 *
 * These services intentionally kill their own process once a batch finishes, which naturally
 * tears the binding down (onServiceDisconnected) - call [reconnectIfNeeded] before starting a
 * new batch to rebind.
 */
class JobServiceConnection(
    private val serviceClass: Class<out Service>,
    private val onEvent: (key: Int, content: String) -> Unit,
) : ServiceConnection {

    private val callback = object : IMikuRayCallback.Stub() {
        override fun onEvent(key: Int, content: String?) {
            this@JobServiceConnection.onEvent(key, content.orEmpty())
        }
    }

    @Volatile
    private var service: IJobService? = null

    @Volatile
    private var bound = false

    @Volatile
    private var callbackRegistered = false

    @Volatile
    private var connectedSignal = CompletableDeferred<Unit>()

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        val svc = IJobService.Stub.asInterface(binder)
        service = svc
        try {
            svc.registerCallback(callback)
            callbackRegistered = true
        } catch (e: RemoteException) {
            LogUtil.e(AppConfig.TAG, "JobServiceConnection: failed to register callback", e)
        }
        connectedSignal.complete(Unit)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        callbackRegistered = false
        bound = false
        connectedSignal = CompletableDeferred()
    }

    fun connect(context: Context) {
        if (bound) return
        val app = context.applicationContext
        val intent = Intent(app, serviceClass)
        bound = try {
            app.bindService(intent, this, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "JobServiceConnection: bind failed", e)
            false
        }
    }

    fun disconnect(context: Context) {
        if (!bound) return
        val app = context.applicationContext
        val svc = service
        if (svc != null && callbackRegistered) {
            try {
                svc.unregisterCallback(callback)
            } catch (_: RemoteException) {
            }
        }
        try {
            app.unbindService(this)
        } catch (_: IllegalArgumentException) {
        }
        bound = false
        callbackRegistered = false
        service = null
        connectedSignal = CompletableDeferred()
    }

    /**
     * Ensures we're bound and, if a rebind just happened (e.g. the worker process died at the
     * end of the previous batch), waits briefly for onServiceConnected so the caller doesn't
     * fire the start Intent before the callback is registered and miss early progress events.
     */
    suspend fun reconnectIfNeeded(context: Context, timeoutMs: Long = 3000L) {
        if (service != null) return
        disconnect(context)
        connect(context)
        try {
            withTimeoutOrNull(timeoutMs) { connectedSignal.await() }
        } catch (_: TimeoutCancellationException) {
        }
    }
}
