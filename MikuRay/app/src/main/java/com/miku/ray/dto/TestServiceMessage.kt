package com.miku.ray.dto

data class TestServiceMessage(
    val key: Int,
    val testId: String = "",
    val subscriptionId: String = "",
    val serverGuids: List<String> = emptyList(),
    val onlyTcp: Boolean = false
)
