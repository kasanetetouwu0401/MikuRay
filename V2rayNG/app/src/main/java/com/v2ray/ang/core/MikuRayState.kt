package com.v2ray.ang.core

/**
 * Ported from Exclave's BaseService.State.
 *
 * [Idle] only ever exists on the UI side, seeded before the AIDL connection resolves - the
 * service itself never reports it (see CoreServiceManager.isRunning()/Binder.getState()).
 * Having an explicit Idle/Connecting/Stopping distinct from Connected/Stopped is what closes
 * the "isRunning == null vs false" ambiguity that let the FAB and connection-test button
 * silently misfire (or take the wrong branch) during the first bind after app start: a plain
 * Boolean can't tell "not connected yet" apart from "actively connecting/stopping", so code
 * gating on `isRunning.value == true` treated both as "not running" and let the FAB try to
 * start an already-running tunnel.
 */
enum class MikuRayState(val canStop: Boolean = false) {
    Idle,
    Connecting(true),
    Connected(true),
    Stopping,
    Stopped,
}
