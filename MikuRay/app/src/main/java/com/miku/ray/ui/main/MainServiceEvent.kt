package com.miku.ray.ui.main

import com.miku.ray.dto.RealPingProgress
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.RealPingSummary
import com.miku.ray.dto.TestProgressInfo

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    data class StateStartSuccess(val restarted: Boolean) : MainServiceEvent()
    data class StateStartFailure(val message: String) : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()
    data object StateRestart : MainServiceEvent()
    data class MeasureDelayResult(val result: RealPingResult) : MainServiceEvent()
    data class MeasureDelayText(val value: String) : MainServiceEvent()
    data class MeasureIp(val value: String) : MainServiceEvent()
    data class MeasureConfigSuccess(val result: RealPingResult) : MainServiceEvent()
    data class MeasureConfigNotify(val progress: RealPingProgress) : MainServiceEvent()
    data class MeasureConfigFinish(val summary: RealPingSummary) : MainServiceEvent()
    data class CountryCodeSuccess(val guid: String) : MainServiceEvent()
    data class CountryCodeNotify(val info: TestProgressInfo) : MainServiceEvent()
    data object CountryCodeFinish : MainServiceEvent()
    data class TrafficSpeed(val value: String) : MainServiceEvent()
    data class TrafficUpdated(val guid: String) : MainServiceEvent()
}
