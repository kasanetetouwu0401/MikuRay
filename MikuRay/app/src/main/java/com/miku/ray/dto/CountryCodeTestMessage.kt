package com.miku.ray.dto

data class CountryCodeTestMessage(
    val key: Int,
    val subscriptionId: String = "",
    val serverGuids: List<String> = emptyList()
)
