package com.example.booking_app.feature.notification.data

import androidx.core.app.NotificationManagerCompat
import com.example.booking_app.core.session.SessionManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationMessagingService : FirebaseMessagingService() {
    @Inject lateinit var session: SessionManager
    @Inject lateinit var channels: NotificationChannels
    @Inject lateinit var tokens: NotificationTokenCoordinator
    @Inject lateinit var renderer: NotificationPushRenderer
    override fun onNewToken(token: String) { tokens.requestSync() }
    override fun onMessageReceived(message: RemoteMessage) {
        // A notification block would let the OS bypass the UID/privacy guard in background.
        if (message.notification != null) return
        val manager = NotificationManagerCompat.from(this)
        val push = NotificationPushPolicy.parse(message.data, session.currentUid(), manager.areNotificationsEnabled()) ?: return
        channels.ensureCreated()
        val notification = renderer.build(push)
        // Permission/account can change between parsing and notify. Never crash the service on revocation.
        if (session.currentUid() != push.recipientUid) return
        try { manager.notify("notification:${push.recipientUid}:${push.notificationId}", 0, notification) }
        catch (_: SecurityException) { /* Inbox is independent of OS permission. */ }
    }
}
