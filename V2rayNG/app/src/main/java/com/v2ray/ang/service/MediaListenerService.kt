package com.v2ray.ang.service

import android.service.notification.NotificationListenerService

/**
 * Minimal NotificationListenerService.
 *
 * We don't actually need to read notifications here — its only purpose is to give us
 * a valid, permission-bound component so [android.media.session.MediaSessionManager]
 * will hand us the list of currently active media sessions (used by Greetings to show
 * "now playing" info). The user still has to grant Notification Access for this to work.
 */
class MediaListenerService : NotificationListenerService()
