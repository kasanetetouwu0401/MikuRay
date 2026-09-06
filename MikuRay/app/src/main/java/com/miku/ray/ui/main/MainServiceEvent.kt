package com.miku.ray.ui.main

import com.miku.ray.dto.RealPingProgress
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.RealPingSummary
import com.miku.ray.dto.TestProgressInfo

/**
 * Every event the daemon/service processes can push to the UI, translated
 * out of the raw broadcast intent by [MainRepository]. Mirrors the shape of
 * v2rayNG's MainServiceEvent, extended with MikuRay's country-code test,
 * traffic, and subscription-update events.
 */
sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    data object StateRestart : MainServiceEvent()
    data class StateStartSuccess(val restarted: Boolean) : MainServiceEvent()
    data class StateStartFailure(val message: String?) : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()

    data class MeasureDelaySuccess(val content: String) : MainServiceEvent()
    data class MeasureIpSuccess(val ip: String?) : MainServiceEvent()

    data class MeasureConfigResult(val result: RealPingResult?, val legacyGuid: String?) : MainServiceEvent()
    data class MeasureConfigNotify(val progress: RealPingProgress?, val legacy: TestProgressInfo?) : MainServiceEvent()
    data class MeasureConfigFinish(val summary: RealPingSummary?) : MainServiceEvent()

    data class CountryCodeSuccess(val guid: String) : MainServiceEvent()
    data class CountryCodeNotify(val info: TestProgressInfo?) : MainServiceEvent()
    data object CountryCodeFinish : MainServiceEvent()

    data class TrafficUpdated(val guid: String) : MainServiceEvent()
    data class TrafficSpeedUpdated(val text: String) : MainServiceEvent()

    data object SubUpdateFinish : MainServiceEvent()
}
