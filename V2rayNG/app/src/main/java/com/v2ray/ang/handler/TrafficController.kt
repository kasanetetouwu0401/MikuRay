package com.v2ray.ang.handler

import android.app.Service
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

object TrafficController {

    // 1 second so any subscriber (e.g. a real-time speed display) can refresh every second.
    private const val QUERY_INTERVAL_MS = 1000L

    /**
     * The core's queryAllOutboundTrafficStats() resets its counters on every call, so only
     * one place in the app may call it. Consumers (NotificationManager, MainActivity's
     * real-time speed display, etc.) subscribe here instead of querying independently,
     * otherwise multiple consumers would race for the same delta and each one ends up
     * with incomplete/inaccurate numbers. Multiple listeners may be registered at once;
     * they all receive the same tick from the single query loop below.
     */
    interface Listener {
        fun onTraffic(
            proxyUplink: Long,
            proxyDownlink: Long,
            directUplink: Long,
            directDownlink: Long,
            intervalMs: Long,
        )
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    @Volatile private var lastTickTime: Long = 0L

    private var job: Job? = null

    /**
     * Kept for backward compatibility; prefer [addListener]/[removeListener]
     * since this object now supports more than one subscriber.
     */
    fun setListener(listener: Listener?) {
        listeners.clear()
        if (listener != null) listeners.add(listener)
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun start() {
        if (job != null) return
        lastTickTime = System.currentTimeMillis()
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                tick()
                delay(QUERY_INTERVAL_MS)
            }
        }
        LogUtil.i(AppConfig.TAG, "TrafficController: started")
    }

    fun stop() {
        job?.cancel()
        job = null
        lastTickTime = 0L
        LogUtil.i(AppConfig.TAG, "TrafficController: stopped")
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val intervalMs = if (lastTickTime == 0L) QUERY_INTERVAL_MS else (now - lastTickTime)
        lastTickTime = now

        var proxyUplink = 0L
        var proxyDownlink = 0L
        var directUplink = 0L
        var directDownlink = 0L

        runCatching {
            CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
                when {
                    stat.tag == AppConfig.TAG_DIRECT -> {
                        when (stat.direction) {
                            AppConfig.UPLINK -> directUplink += stat.value
                            AppConfig.DOWNLINK -> directDownlink += stat.value
                        }
                    }

                    stat.tag.startsWith(AppConfig.TAG_PROXY) -> {
                        when (stat.direction) {
                            AppConfig.UPLINK -> proxyUplink += stat.value
                            AppConfig.DOWNLINK -> proxyDownlink += stat.value
                        }
                    }
                }
            }
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "TrafficController: queryAllOutboundTrafficStats failed", e)
            return
        }

        listeners.forEach { l ->
            runCatching {
                l.onTraffic(proxyUplink, proxyDownlink, directUplink, directDownlink, intervalMs)
            }.onFailure { e ->
                LogUtil.e(AppConfig.TAG, "TrafficController: listener failed", e)
            }
        }

        if (proxyUplink + proxyDownlink <= 0L) return

        val guid = MmkvManager.getSelectServer() ?: return
        MmkvManager.addProfileTraffic(guid, proxyUplink, proxyDownlink)

        getService()?.let { svc ->
            MessageUtil.sendMsg2UI(svc, AppConfig.MSG_TRAFFIC_UPDATED, guid)
        }
    }

    private fun getService(): Service? =
        CoreServiceManager.serviceControl?.get()?.getService()
}
