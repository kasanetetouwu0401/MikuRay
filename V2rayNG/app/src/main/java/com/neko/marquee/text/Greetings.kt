package com.neko.marquee.text

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import com.v2ray.ang.R
import com.v2ray.ang.service.MediaListenerService
import java.util.*

class Greetings @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val mediaSessionManager: MediaSessionManager? by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    }

    private val mediaListenerComponent by lazy {
        ComponentName(context, MediaListenerService::class.java)
    }

    /** Active controllers we've attached a callback to, so we can detach them later. */
    private val observedControllers = mutableListOf<MediaController>()

    private var sessionListenerRegistered = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            mainHandler.post { refreshNowPlaying() }
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            mainHandler.post { refreshNowPlaying() }
        }

        override fun onSessionDestroyed() {
            mainHandler.post { refreshNowPlaying() }
        }
    }

    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener {
            mainHandler.post { refreshNowPlaying() }
        }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED
                )
            ) {
                refreshNowPlaying()
            }
        }
    }

    init {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        setHorizontallyScrolling(true)
        isSelected = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isSelected = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(timeReceiver, filter)

        registerMediaSessionListener()
        refreshNowPlaying()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(timeReceiver)
        } catch (e: Exception) {
        }
        unregisterMediaSessionListener()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            isSelected = true
            // Re-register listener in case notification access was just granted
            // while the app was in background / settings was open.
            if (!sessionListenerRegistered) {
                registerMediaSessionListener()
            }
            refreshNowPlaying()
        }
    }

    /**
     * Returns true if the user has granted Notification Access (required for
     * [MediaSessionManager.getActiveSessions]).
     */
    private fun hasNotificationListenerPermission(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val myPkg = context.packageName
        return flat.split(":").any { it.startsWith(myPkg) }
    }

    private fun registerMediaSessionListener() {
        if (!hasNotificationListenerPermission()) {
            sessionListenerRegistered = false
            return
        }
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                activeSessionsChangedListener,
                mediaListenerComponent,
                mainHandler
            )
            sessionListenerRegistered = true
        } catch (e: SecurityException) {
            sessionListenerRegistered = false
        }
    }

    private fun unregisterMediaSessionListener() {
        sessionListenerRegistered = false
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        } catch (e: Exception) {
        }
        detachAllControllerCallbacks()
    }

    private fun detachAllControllerCallbacks() {
        observedControllers.forEach {
            try { it.unregisterCallback(controllerCallback) } catch (e: Exception) { }
        }
        observedControllers.clear()
    }

    /**
     * Looks for a currently-playing media session (Spotify, YT Music, etc). If one is found,
     * we show its title/artist instead of the regular clock greeting. If nothing is playing
     * (or we don't have notification access), we fall back to [updateGreeting].
     */
    private fun refreshNowPlaying() {
        if (!hasNotificationListenerPermission()) {
            updateGreeting()
            return
        }

        val controllers = try {
            mediaSessionManager?.getActiveSessions(mediaListenerComponent)
        } catch (e: SecurityException) {
            null
        }

        // Re-attach callbacks so future playback/metadata changes keep us updated.
        detachAllControllerCallbacks()
        controllers?.forEach {
            try {
                it.registerCallback(controllerCallback, mainHandler)
                observedControllers.add(it)
            } catch (e: Exception) {
            }
        }

        val playingController = controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        val metadata = playingController?.metadata
        val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)

        if (playingController != null && !title.isNullOrBlank()) {
            text = if (!artist.isNullOrBlank()) {
                context.getString(R.string.uwu_now_playing, title, artist)
            } else {
                context.getString(R.string.uwu_now_playing_no_artist, title)
            }
            isSelected = false
            isSelected = true
        } else {
            updateGreeting()
        }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        @StringRes val greetRes = when (hour) {
            in 5..10 -> R.string.uwu_greeting_morning
            in 11..14 -> R.string.uwu_greeting_afternoon
            in 15..18 -> R.string.uwu_greeting_evening
            in 19..23 -> R.string.uwu_greeting_night
            else -> R.string.uwu_greeting_late_night
        }

        text = context.getString(greetRes)

        isSelected = false
        isSelected = true
    }
}
