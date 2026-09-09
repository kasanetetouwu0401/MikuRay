package com.miku.ray.dto

data class SubscriptionUpdateMessage(
    val key: Int,
    val forcedUpdate: Boolean,
    val subIds: List<String> = emptyList()
)
