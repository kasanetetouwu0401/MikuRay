package com.miku.ray.dto

import java.io.Serializable

data class SpeedTestMessage(
    val key: Int,
    val requestId: String = "",
    val subscriptionId: String = "",
    val serverGuids: List<String> = emptyList(),
) : Serializable

data class SpeedTestProgress(
    val guid: String,
    val current: Int,
    val total: Int,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val error: String? = null,
) : Serializable
