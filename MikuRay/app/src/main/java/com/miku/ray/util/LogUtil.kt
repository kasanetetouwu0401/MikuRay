package com.miku.ray.util

import com.miku.ray.AppConfig
import timber.log.Timber

object LogUtil {

    const val TAG_CORE = "XrayCore"

    @Suppress("unused")
    fun refreshLogLevel() = MikuRayLogTree.refreshLogLevel()

    fun v(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).v(message)
    fun d(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).d(message)
    fun i(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).i(message)
    fun w(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).w(message)
    fun e(tag: String = AppConfig.TAG, message: String) = Timber.tag(tag).e(message)

    fun d(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = Timber.tag(tag).d(throwable, message)
    fun i(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = Timber.tag(tag).i(throwable, message)
    fun w(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = Timber.tag(tag).w(throwable, message)
    fun e(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = Timber.tag(tag).e(throwable, message)

    fun core(levelHint: Long, message: String?) {
        if (message.isNullOrEmpty()) return
        when {
            levelHint >= 3L -> e(TAG_CORE, message)
            levelHint == 2L -> w(TAG_CORE, message)
            levelHint == 0L -> d(TAG_CORE, message)
            else -> i(TAG_CORE, message)
        }
    }
}
