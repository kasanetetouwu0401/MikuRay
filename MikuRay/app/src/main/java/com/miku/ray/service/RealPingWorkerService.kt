package com.miku.ray.service

import android.content.Context
import com.miku.ray.core.CoreConfigContextBuilder
import com.miku.ray.core.CoreConfigManager
import com.miku.ray.core.CoreNativeManager
import com.miku.ray.dto.RealPingEvent
import com.miku.ray.enums.BalancerStrategyType
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SpeedtestManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

internal object RealPingExecutionLimiter {
    private val customConfigMutex = Mutex()

    suspend fun <T> run(configType: EConfigType, block: () -> T): T {
        // Custom profiles bypass speed-test trimming and start complete Xray configs.
        // Parallel teardown can abort the native probe process, so serialize their
        // JNI measurements globally across batches.
        return if (configType == EConfigType.CUSTOM) {
            customConfigMutex.withLock { block() }
        } else {
            block()
        }
    }
}

private class RealPingProgressState(private val total: Int) {
    private var completed = 0

    fun initial(): RealPingEvent.Progress = RealPingEvent.Progress(completed = 0, total = total)

    @Synchronized
    fun record(): RealPingEvent.Progress? {
        if (completed >= total) return null
        completed++
        return RealPingEvent.Progress(completed = completed, total = total)
    }
}

private data class RealPingProbeSource(
    val guid: String,
    val memberGuids: List<String>,
    val strategy: BalancerStrategyType?,
) {
    fun aggregate(delays: Map<String, Long>): Long {
        val liveDelays = memberGuids.mapNotNull { memberGuid ->
            delays[memberGuid]?.takeIf { it >= 0L }
        }
        if (liveDelays.isEmpty()) return -1L
        return when (strategy) {
            BalancerStrategyType.LEAST_PING -> liveDelays.min()
            BalancerStrategyType.LEAST_LOAD,
            BalancerStrategyType.RANDOM,
            BalancerStrategyType.ROUND_ROBIN,
            null -> liveDelays.average().roundToLong()
        }
    }
}

private data class RealPingProbePlan(
    val sources: List<RealPingProbeSource>,
    val probeGuids: List<String>,
    val sourcesByMemberGuid: Map<String, List<RealPingProbeSource>>,
) {
    companion object {
        fun build(guids: List<String>): RealPingProbePlan {
            val sources = guids.map { guid ->
                val profile = MmkvManager.decodeServerConfig(guid)
                if (profile?.configType == EConfigType.POLICYGROUP) {
                    RealPingProbeSource(
                        guid = guid,
                        memberGuids = CoreConfigContextBuilder.resolvePolicyGroupGuids(profile),
                        strategy = BalancerStrategyType.from(profile.policyGroupType),
                    )
                } else {
                    RealPingProbeSource(guid, listOf(guid), strategy = null)
                }
            }
            val sourcesByMemberGuid = mutableMapOf<String, MutableList<RealPingProbeSource>>()
            sources.forEach { source ->
                source.memberGuids.forEach { memberGuid ->
                    sourcesByMemberGuid.getOrPut(memberGuid, ::mutableListOf).add(source)
                }
            }
            return RealPingProbePlan(
                sources = sources,
                probeGuids = sources.flatMap { it.memberGuids }.distinct(),
                sourcesByMemberGuid = sourcesByMemberGuid,
            )
        }
    }
}

/** Runs one bounded batch of individual delay tests in the disposable probe process. */
class RealPingWorkerService(
    private val context: Context,
    guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {},
) {
    private val guids = guids.distinct()
    private val job = SupervisorJob()
    private val dispatcher = Dispatchers.IO.limitedParallelism(SettingsManager.getRealPingConcurrency())
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))
    private val finished = AtomicBoolean(false)
    private val resultLock = Any()
    private val eventLock = Any()
    private val probeDelays = mutableMapOf<String, Long>()
    private val sourceDelays = mutableMapOf<String, Long>()

    fun start() {
        scope.launch {
            val plan = RealPingProbePlan.build(guids)
            val progress = RealPingProgressState(plan.sources.size)
            onEvent(progress.initial())
            emitCompletedSources(plan.sources.filter { it.memberGuids.isEmpty() }, progress)

            val jobs = plan.probeGuids.map { guid ->
                scope.launch {
                    val delayMillis = safelyProbe(guid)
                    currentCoroutineContext().ensureActive()
                    synchronized(resultLock) { probeDelays[guid] = delayMillis }
                    emitCompletedSources(plan.sourcesByMemberGuid[guid].orEmpty(), progress)
                }
            }
            jobs.joinAll()
            if (finished.compareAndSet(false, true)) {
                onEvent(summary())
            }
        }
    }

    fun cancel(): RealPingEvent.Finish {
        finished.set(true)
        job.cancel()
        return summary()
    }

    private fun emitCompletedSources(
        sources: List<RealPingProbeSource>,
        progress: RealPingProgressState,
    ) {
        val results = synchronized(resultLock) {
            sources.mapNotNull { source ->
                if (source.guid in sourceDelays ||
                    source.memberGuids.any { it !in probeDelays }
                ) {
                    return@mapNotNull null
                }
                val delayMillis = source.aggregate(probeDelays)
                sourceDelays[source.guid] = delayMillis
                RealPingEvent.Result(source.guid, delayMillis)
            }
        }

        synchronized(eventLock) {
            if (!finished.get()) {
                results.forEach { result ->
                    if (finished.get()) return@forEach
                    onEvent(result)
                    progress.record()?.let(onEvent)
                }
            }
        }
    }

    private fun summary(): RealPingEvent.Finish = synchronized(resultLock) {
        RealPingEvent.Finish(
            live = sourceDelays.values.count { it >= 0L },
            completed = sourceDelays.size,
            total = guids.size,
        )
    }

    private suspend fun safelyProbe(guid: String): Long = try {
        if (onlyTcp) startTcping(guid) else startRealPing(guid)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        -1L
    }

    private suspend fun startRealPing(guid: String): Long {
        val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.startsWith("h3") != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val tcpTime = SpeedtestManager.socketConnectTime(
                config.server.orEmpty(),
                config.serverPort.orEmpty().toInt(),
                1000,
            )
            if (tcpTime <= -1L) return -1L
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) return -1L
        return RealPingExecutionLimiter.run(config.configType) {
            CoreNativeManager.measureOutboundDelay(
                configResult.content,
                SettingsManager.getDelayTestUrl(),
            )
        }
    }

    private fun startTcping(guid: String): Long {
        val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.split(',')?.all { it.trim().startsWith("h3") } != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            return SpeedtestManager.socketConnectTime(
                config.server.orEmpty(),
                config.serverPort.orEmpty().toInt(),
                1000,
            )
        }
        return -1L
    }
}
