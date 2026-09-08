package com.miku.ray.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

class MainServiceRepository private constructor(private val app: AngApplication) {
    private val closed = AtomicBoolean(false)
    private val _events = MutableSharedFlow<Intent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Intent> = _events.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { _events.tryEmit(Intent(it)) }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags(),
        )
        resync()
    }

    fun resync() {
        if (!closed.get()) MessageUtil.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { MessageUtil.sendMsg2Service(app, AppConfig.MSG_UNREGISTER_CLIENT, "") }
        runCatching { app.unregisterReceiver(receiver) }
    }

    companion object {
        @Volatile private var instance: MainServiceRepository? = null
        fun get(app: AngApplication): MainServiceRepository =
            instance ?: synchronized(this) {
                instance ?: MainServiceRepository(app).also { instance = it }
            }
    }
}
