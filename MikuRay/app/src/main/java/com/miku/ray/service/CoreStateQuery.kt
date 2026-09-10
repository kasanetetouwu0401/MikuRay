package com.miku.ray.service

import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.miku.ray.AppConfig
import com.miku.ray.core.CoreServiceManager

object CoreStateQuery {
    const val ACTION_QUERY_STATE = "${AppConfig.ANG_PACKAGE}.action.query_core_state"

    fun binder(): IBinder = Messenger(
        Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == AppConfig.MSG_REGISTER_CLIENT) {
                val stateWhat = if (CoreServiceManager.isRunning()) {
                    AppConfig.MSG_STATE_RUNNING
                } else {
                    AppConfig.MSG_STATE_NOT_RUNNING
                }
                runCatching { msg.replyTo?.send(Message.obtain(null, stateWhat)) }
            }
            true
        },
    ).binder
}
