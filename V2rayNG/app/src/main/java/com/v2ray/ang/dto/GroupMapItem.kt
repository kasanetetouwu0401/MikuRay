package com.v2ray.ang.dto

data class GroupMapItem(
    var id: String,
    var remarks: String,
    var serverCount: Int = 0,
    var icon: String? = null,   // nama drawable resource, e.g. "ic_cloud"
)