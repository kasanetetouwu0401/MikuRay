package com.v2ray.ang.util

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager

object SensorTextController {

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_SENSOR_TEXT, false)

    fun getAddress(profile: ProfileItem): String {
        return if (isEnabled()) {
            // Show full address without *** masking
            profile.description.nullIfBlank() ?: run {
                val server = profile.server ?: ""
                val port = profile.serverPort ?: ""
                if (server.isBlank() && port.isBlank()) "" else "$server : $port"
            }
        } else {
            // Default: masked address (xxx.xxx.***)
            profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
        }
    }
}
