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

object TrafficController {

    private const val QUERY_INTERVAL_MS = 3000L

    private var job: Job? = null

    fun start() {
        if (job != null) return
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
        LogUtil.i(AppConfig.TAG, "TrafficController: stopped")
    }

    private fun tick() {
        var proxyUplink = 0L
        var proxyDownlink = 0L

        runCatching {
            CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
                if (stat.tag.startsWith(AppConfig.TAG_PROXY)) {
                    when (stat.direction) {
                        AppConfig.UPLINK -> proxyUplink += stat.value
                        AppConfig.DOWNLINK -> proxyDownlink += stat.value
                    }
                }
            }
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "TrafficController: queryAllOutboundTrafficStats failed", e)
            return
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
