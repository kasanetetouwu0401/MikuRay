package com.miku.ray.ui.main

import com.miku.ray.dto.RealPingResult

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    data object StateRestart : MainServiceEvent()
    data class StateStartSuccess(val restarted: Boolean) : MainServiceEvent()
    data class StateStartFailure(val message: String?) : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()
    data class MeasureDelayResult(val result: RealPingResult) : MainServiceEvent()
    data object MeasureConfigSuccess : MainServiceEvent()
    data class MeasureConfigNotify(val progress: String) : MainServiceEvent()
    data class MeasureConfigFinish(val finishedCount: String?) : MainServiceEvent()
}
