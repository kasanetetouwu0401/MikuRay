package com.miku.ray.ui.main

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    data class StateStartSuccess(val msg: String) : MainServiceEvent()
    data class StateStartFailure(val msg: String) : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()
    data object StateRestart : MainServiceEvent()
    data class MeasureDelayResult(val result: String) : MainServiceEvent()
    data class MeasureIpResult(val ip: String) : MainServiceEvent()
    data class MeasureConfigSuccess(val json: String) : MainServiceEvent()
    data class MeasureConfigNotify(val json: String) : MainServiceEvent()
    data class MeasureConfigFinish(val json: String?) : MainServiceEvent()
    data class CountryCodeSuccess(val guid: String) : MainServiceEvent()
    data class CountryCodeNotify(val json: String) : MainServiceEvent()
    data object CountryCodeFinish : MainServiceEvent()
    data class TrafficUpdated(val guid: String) : MainServiceEvent()
    data class TrafficSpeedUpdated(val speedText: String) : MainServiceEvent()
    data object SubUpdateFinish : MainServiceEvent()
}
