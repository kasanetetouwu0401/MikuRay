package com.miku.ray.aidl

/** IPC protocol shared by all MikuRay background services. */
object AidlProtocol {
    const val SERVICE_ACTION = "com.miku.ray.action.AIDL_SERVICE"

    const val CORE_STOP = 1
    const val CORE_RESTART = 2
    const val CORE_MEASURE_DELAY = 3
    const val CORE_MEASURE_IP = 4

    const val TEST_START = 10
    const val TEST_CANCEL = 11

    const val COUNTRY_START = 20
    const val COUNTRY_CANCEL = 21

    const val SUBSCRIPTION_START = 30
    const val SUBSCRIPTION_CANCEL = 31

    const val EVENT_STATE_RUNNING = 100
    const val EVENT_STATE_NOT_RUNNING = 101
    const val EVENT_STATE_START_SUCCESS = 102
    const val EVENT_STATE_START_FAILURE = 103
    const val EVENT_STATE_STOP_SUCCESS = 104
    const val EVENT_STATE_RESTART = 105
    const val EVENT_MEASURE_DELAY = 106
    const val EVENT_MEASURE_IP = 107
    const val EVENT_TEST_SUCCESS = 108
    const val EVENT_TEST_NOTIFY = 109
    const val EVENT_TEST_FINISH = 110
    const val EVENT_COUNTRY_SUCCESS = 111
    const val EVENT_COUNTRY_NOTIFY = 112
    const val EVENT_COUNTRY_FINISH = 113
    const val EVENT_TRAFFIC_UPDATED = 114
    const val EVENT_TRAFFIC_SPEED_UPDATED = 115
}
