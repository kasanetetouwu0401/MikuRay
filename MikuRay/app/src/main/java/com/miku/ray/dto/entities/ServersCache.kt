package com.miku.ray.dto.entities

data class ServersCache(
    val guid: String,
    val profile: ProfileItem,
    val affiliation: ServerAffiliationInfo? = null,
    val traffic: String? = null,
    val isPinned: Boolean = false,
    val subscriptionRemarks: String? = null,
)