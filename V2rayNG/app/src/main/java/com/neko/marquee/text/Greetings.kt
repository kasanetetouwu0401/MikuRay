package com.neko.marquee.text

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
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

    private val observedControllers = mutableListOf<MediaController>()
    private var sessionListenerRegistered = false

    // Last known now-playing info from MUSIC_PACKAGE_CHANGED / ACTION_MEDIA_* broadcasts
    private var broadcastTitle: String? = null
    private var broadcastArtist: String? = null
    private var broadcastPlaying: Boolean = false

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

    /**
     * Catches legacy music broadcasts from many players (AOSP Music, Spotify, Poweramp,
     * Samsung Music, Xiaomi Music, YT Music, etc.).
     * These work even without Notification Access on older ROMs.
     */
    private val musicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val action = intent.action ?: ""
            val playing: Boolean = when {
                intent.hasExtra("playing") -> intent.getBooleanExtra("playing", false)
                intent.hasExtra("playstate") -> intent.getIntExtra("playstate", -1) == 1
                else -> action.contains("PLAY") && !action.contains("PAUSE")
            }

            val title  = intent.getStringExtra("track")
                ?: intent.getStringExtra("title")
                ?: intent.getStringExtra("song")
            val artist = intent.getStringExtra("artist")

            broadcastPlaying = playing
            broadcastTitle   = title?.takeIf { it.isNotBlank() }
            broadcastArtist  = artist?.takeIf { it.isNotBlank() }

            mainHandler.post { refreshNowPlaying() }
        }
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

        // TIME_TICK etc. are protected system broadcasts — NOT_EXPORTED is correct here
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            })
        }

        // Music broadcasts come from other apps — must be EXPORTED on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(musicReceiver, buildMusicIntentFilter(), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(musicReceiver, buildMusicIntentFilter())
        }

        registerMediaSessionListener()
        refreshNowPlaying()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        safeUnregister(timeReceiver)
        safeUnregister(musicReceiver)
        unregisterMediaSessionListener()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            isSelected = true
            // Android 13+ sometimes silently drops the listener binding when
            // the user navigates away and back; re-register defensively.
            if (!sessionListenerRegistered) {
                registerMediaSessionListener()
            }
            refreshNowPlaying()
        }
    }

    // ─── Permission ──────────────────────────────────────────────────────────

    private fun hasNotificationListenerPermission(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val myPkg = context.packageName
        return flat.split(":").any { it.startsWith(myPkg) }
    }

    // ─── MediaSessionManager path (API 21+, needs Notification Access) ───────

    private fun registerMediaSessionListener() {
        if (!hasNotificationListenerPermission()) { sessionListenerRegistered = false; return }
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                activeSessionsChangedListener, mediaListenerComponent, mainHandler
            )
            sessionListenerRegistered = true
        } catch (e: SecurityException) {
            sessionListenerRegistered = false
        }
    }

    private fun unregisterMediaSessionListener() {
        sessionListenerRegistered = false
        try { mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsChangedListener) } catch (_: Exception) {}
        detachAllControllerCallbacks()
    }

    private fun detachAllControllerCallbacks() {
        observedControllers.forEach {
            try { it.unregisterCallback(controllerCallback) } catch (_: Exception) {}
        }
        observedControllers.clear()
    }

    // ─── Core refresh ────────────────────────────────────────────────────────

    private fun refreshNowPlaying() {
        // 1. Try MediaSessionManager (most reliable, needs notification access)
        if (hasNotificationListenerPermission()) {
            val controllers = try {
                mediaSessionManager?.getActiveSessions(mediaListenerComponent)
            } catch (_: SecurityException) { null }

            detachAllControllerCallbacks()
            controllers?.forEach {
                try {
                    it.registerCallback(controllerCallback, mainHandler)
                    observedControllers.add(it)
                } catch (_: Exception) {}
            }

            // Prefer STATE_PLAYING, but also accept BUFFERING / CONNECTING in case
            // the player hasn't transitioned state yet.
            val playing = controllers?.firstOrNull {
                val s = it.playbackState?.state
                s == PlaybackState.STATE_PLAYING ||
                s == PlaybackState.STATE_BUFFERING ||
                s == PlaybackState.STATE_CONNECTING
            } ?: controllers?.firstOrNull { it.playbackState != null }

            if (playing != null) {
                val meta = playing.metadata
                // Try every known title key — different players use different ones
                val title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                    ?: meta?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: meta?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM)
                val artist = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: meta?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)

                if (!title.isNullOrBlank()) {
                    showNowPlaying(title, artist)
                    return
                }
            }
        }

        // 2. Fallback: legacy music broadcast (no permission needed)
        if (broadcastPlaying && !broadcastTitle.isNullOrBlank()) {
            showNowPlaying(broadcastTitle!!, broadcastArtist)
            return
        }

        updateGreeting()
    }

    private fun showNowPlaying(title: String, artist: String?) {
        text = if (!artist.isNullOrBlank()) {
            context.getString(R.string.uwu_now_playing, title, artist)
        } else {
            context.getString(R.string.uwu_now_playing_no_artist, title)
        }
        isSelected = false
        isSelected = true
    }

    // ─── Greeting fallback ───────────────────────────────────────────────────

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        @StringRes val greetRes = when (hour) {
            in 5..10  -> R.string.uwu_greeting_morning
            in 11..14 -> R.string.uwu_greeting_afternoon
            in 15..18 -> R.string.uwu_greeting_evening
            in 19..23 -> R.string.uwu_greeting_night
            else      -> R.string.uwu_greeting_late_night
        }
        text = context.getString(greetRes)
        isSelected = false
        isSelected = true
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun safeUnregister(receiver: BroadcastReceiver) {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    /**
     * Covers the broadcast actions used by the most common Android music players.
     * No permission is required for any of these.
     */
    private fun buildMusicIntentFilter() = IntentFilter().apply {
        // Standard / AOSP
        addAction("com.android.music.metachanged")
        addAction("com.android.music.playstatechanged")
        addAction("com.android.music.playbackcomplete")
        // Spotify
        addAction("com.spotify.music.metadatachanged")
        addAction("com.spotify.music.playbackstatechanged")
        // YouTube Music / Google Play Music
        addAction("com.google.android.music.metachanged")
        addAction("com.google.android.music.playstatechanged")
        // Samsung Music
        addAction("com.samsung.music.metachanged")
        addAction("com.samsung.music.playstatechanged")
        // Xiaomi / MIUI Music
        addAction("com.miui.player.metachanged")
        addAction("com.miui.player.playstatechanged")
        // Poweramp
        addAction("com.maxmpz.audioplayer.STATUS_CHANGED")
        addAction("com.maxmpz.audioplayer.PLAYING_MODE_CHANGED")
        // Pulsar / BlackPlayer / generic players that use standard action
        addAction("music.PLAYBACK_VIEWER_UPDATE")
        // Last.fm scrobbler standard
        addAction("fm.last.android.metachanged")
        addAction("fm.last.android.playstatechanged")
        // Winamp
        addAction("com.nullsoft.winamp.metachanged")
        addAction("com.nullsoft.winamp.playstatechanged")
        // Generic fallback used by many players
        addAction("com.android.music.musicservicecommand")
    }
}
