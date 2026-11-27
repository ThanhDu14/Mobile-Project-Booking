package com.example.booking_app.feature.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.booking_app.R
import com.example.booking_app.feature.notification.data.NotificationChannels
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** OS-only checks in the isolated adminlocal app. Does not post a notification or request permission. */
class NotificationChannelsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val id = NotificationChannels.GENERAL_ID + "_test_" + UUID.randomUUID()
    private val setup = NotificationChannels(context)

    @After fun removeTestChannel() { manager.deleteNotificationChannel(id) }

    @Test fun createsNamedDefaultChannelWithoutChangingPermissionOrPosting() {
        assertTrue("Use only the isolated local APK", context.packageName.endsWith(".adminlocal"))
        val enabled = manager.areNotificationsEnabled()
        val existingNotifications = manager.activeNotifications.map { it.key }.toSet()
        setup.ensureCreated(id)
        val channel = manager.getNotificationChannel(id)
        assertNotNull(channel)
        assertEquals(context.getString(R.string.notification_title), channel.name.toString())
        assertEquals(context.getString(R.string.notification_channel_description), channel.description)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(enabled, manager.areNotificationsEnabled())
        assertEquals(existingNotifications, manager.activeNotifications.map { it.key }.toSet())
    }

    @Test fun preservesExistingImportanceAndMetadata() {
        manager.createNotificationChannel(NotificationChannel(id, "Existing", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Existing description"
        })
        setup.ensureCreated(id)
        val channel = manager.getNotificationChannel(id)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals("Existing", channel.name.toString())
        assertEquals("Existing description", channel.description)
    }

    @Test fun repeatedSetupKeepsOneChannel() {
        repeat(3) { setup.ensureCreated(id) }
        assertEquals(1, manager.notificationChannels.count { it.id == id })
    }
}
