package com.example.booking_app.feature.notification

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.booking_app.R
import com.example.booking_app.feature.notification.data.*
import org.junit.Assert.*
import org.junit.Test

class NotificationPushRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun privateAndPublicContentAreGenericAndTapIsImmutable() {
        assertTrue(context.packageName.endsWith(".adminlocal"))
        val notification = NotificationPushRenderer(context).build(NotificationPush("alice", "one"))
        assertEquals(NotificationChannels.GENERAL_ID, notification.channelId)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        for (version in listOf(notification, notification.publicVersion)) {
            assertEquals(context.getString(R.string.app_name), version.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("Bạn có thông báo mới", version.extras.getString(Notification.EXTRA_TEXT))
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) assertTrue(notification.contentIntent.isImmutable)
        notification.contentIntent.cancel()
    }
    @Test fun distinctInboxAndAccountHaveDistinctTapIntents() {
        val renderer = NotificationPushRenderer(context)
        val one = renderer.build(NotificationPush("alice", "one")).contentIntent
        val two = renderer.build(NotificationPush("alice", "two")).contentIntent
        val other = renderer.build(NotificationPush("bob", "one")).contentIntent
        assertNotEquals(one, two); assertNotEquals(one, other)
        listOf(one, two, other).forEach { it.cancel() }
    }
}
