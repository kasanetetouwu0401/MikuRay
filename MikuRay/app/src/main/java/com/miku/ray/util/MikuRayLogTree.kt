package com.miku.ray.util

import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import timber.log.Timber
import java.util.Locale

class MikuRayLogTree : Timber.DebugTree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean = true

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val effectiveTag = tag ?: AppConfig.TAG
        val fullMessage = if (t != null) "$message\n${t.stackTraceToString()}" else message

        InProcessLogBuffer.append(priority, effectiveTag, fullMessage)

        if (priority >= minPriority()) {
            super.log(priority, effectiveTag, message, t)
        }
    }

    companion object {
        private const val DEFAULT_LEVEL = "warning"
        private const val CACHE_UNSET = Int.MIN_VALUE

        @Volatile
        private var cachedMinPriority: Int = CACHE_UNSET

        private fun parsePriority(level: String?): Int {
            return when ((level ?: DEFAULT_LEVEL).lowercase(Locale.US)) {
                "verbose" -> LogPriority.VERBOSE
                "debug" -> LogPriority.DEBUG
                "info" -> LogPriority.INFO
                "warn", "warning" -> LogPriority.WARN
                "error" -> LogPriority.ERROR
                "none", "off" -> Int.MAX_VALUE
                else -> LogPriority.WARN
            }
        }

        fun refreshLogLevel() {
            cachedMinPriority = parsePriority(MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL, DEFAULT_LEVEL))
        }

        private fun minPriority(): Int {
            val cached = cachedMinPriority
            if (cached != CACHE_UNSET) return cached

            return synchronized(this) {
                val current = cachedMinPriority
                if (current != CACHE_UNSET) {
                    current
                } else {
                    parsePriority(MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL, DEFAULT_LEVEL)).also {
                        cachedMinPriority = it
                    }
                }
            }
        }
    }
}
