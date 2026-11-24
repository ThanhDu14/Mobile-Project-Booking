package com.example.booking_app.feature.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.example.booking_app.core.session.*
import com.example.booking_app.feature.notification.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Injects data into the exact service handler; does not use the FCM transport. */
class NotificationPushHandlerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private class Session(var uid: String?) : SessionManager {
        override val sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut(null))
        override val isGuest = MutableStateFlow(false)
        override suspend fun refresh(forceTokenRefresh: Boolean) {}
        override suspend fun signOut() { uid = null }
        override fun currentUid() = uid
        override fun currentAppRole() = AppRole.PLAYER
    }
    @Test fun injectedPayloadPostsGenericContentAndRejectsOtherAccountLogoutAndNotificationBlock() {
        assertTrue(context.packageName.endsWith(".adminlocal"))
        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())
        val session = Session("local-handler")
        val handler = NotificationPushHandler(context, session, NotificationChannels(context), NotificationPushRenderer(context))
        val data = mapOf("recipientUid" to "local-handler", "notificationId" to "local-one")
        val tag = "notification:local-handler:local-one"
        try {
            assertFalse(handler.handle(data, true))
            assertFalse(handler.handle(data + ("recipientUid" to "other-account")))
            assertTrue(handler.handle(data))
            val deadline = android.os.SystemClock.uptimeMillis() + 3000
            while (manager.activeNotifications.none { it.tag == tag } && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(30)
            val posted = manager.activeNotifications.single { it.tag == tag }.notification
            assertEquals("Bạn có thông báo mới", posted.extras.getString(Notification.EXTRA_TEXT))
            assertEquals("Bạn có thông báo mới", posted.publicVersion.extras.getString(Notification.EXTRA_TEXT))
            session.uid = "other-account"
            assertFalse(handler.handle(data))
            session.uid = null
            assertFalse(handler.handle(data))
            assertFalse(handler.handle(mapOf("recipientUid" to "local-handler", "notificationId" to "../bad")))
        } finally { manager.cancel(tag, 0) }
    }
    @Test fun deniedPermissionDoesNotPostInjectedPayload() {
        assumeTrue(!NotificationManagerCompat.from(context).areNotificationsEnabled())
        val session = Session("local-handler")
        val handler = NotificationPushHandler(context, session, NotificationChannels(context), NotificationPushRenderer(context))
        assertFalse(handler.handle(mapOf("recipientUid" to "local-handler", "notificationId" to "denied")))
        assertTrue(manager.activeNotifications.none { it.tag == "notification:local-handler:denied" })
    }
}
