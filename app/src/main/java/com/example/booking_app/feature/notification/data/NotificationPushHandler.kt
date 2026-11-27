package com.example.booking_app.feature.notification.data

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.booking_app.core.session.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Shared dispatch path for FCM service and local payload injection tests. */
class NotificationPushHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val channels: NotificationChannels,
    private val renderer: NotificationPushRenderer,
) {
    fun handle(data: Map<String, String>, hasNotificationBlock: Boolean = false): Boolean {
        if (hasNotificationBlock) return false
        val manager = NotificationManagerCompat.from(context)
        val push = NotificationPushPolicy.parse(data, session.currentUid(), manager.areNotificationsEnabled()) ?: return false
        channels.ensureCreated()
        val notification = renderer.build(push)
        if (session.currentUid() != push.recipientUid) return false
        return try {
            manager.notify("notification:${push.recipientUid}:${push.notificationId}", 0, notification)
            true
        } catch (_: SecurityException) { false }
    }
}
