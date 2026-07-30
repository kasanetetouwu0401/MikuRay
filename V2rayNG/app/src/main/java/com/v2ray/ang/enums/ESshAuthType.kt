package com.v2ray.ang.enums

enum class ESshAuthType(val value: Int) {
    PASSWORD(0),
    PRIVATE_KEY(1),
    CERTIFICATE(2);

    companion object {
        fun fromInt(value: Int?) = entries.firstOrNull { it.value == value } ?: PASSWORD
        fun fromName(name: String?) = entries.firstOrNull { it.name.equals(name, true) } ?: PASSWORD
    }
}
