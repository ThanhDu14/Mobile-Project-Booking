package com.example.booking_app.feature.notification.data

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationMessagingService : FirebaseMessagingService() {
    @Inject lateinit var tokens: NotificationTokenCoordinator
    @Inject lateinit var handler: NotificationPushHandler
    override fun onNewToken(token: String) { tokens.requestSync() }
    override fun onMessageReceived(message: RemoteMessage) {
        // A notification block would let the OS bypass the UID/privacy guard in background.
        handler.handle(message.data, message.notification != null)
    }
}
