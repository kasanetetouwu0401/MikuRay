package com.v2ray.ang.util

/**
 * Predictive back gesture animation styles, selectable via
 * [com.v2ray.ang.AppConfig.PREF_PREDICTIVE_BACK_ANIMATION].
 *
 * Ported/adapted from InstallerX-Revived's PredictiveBackAnimation concept,
 * reimplemented for MikuRay's View-based (non-Compose) activities.
 */
enum class PredictiveBackAnimation(val value: String) {
    NONE("none"),
    AOSP("aosp"),
    SCALE("scale"),
    CLASSIC("classic");

    companion object {
        fun fromValue(value: String?): PredictiveBackAnimation =
            entries.find { it.value == value } ?: AOSP
    }
}
