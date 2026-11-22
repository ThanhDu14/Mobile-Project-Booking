package com.example.booking_app.feature.notification

import com.example.booking_app.feature.notification.ui.NotificationPermissionAction
import com.example.booking_app.feature.notification.ui.notificationPermissionAction
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionTest {
    @Test fun `older Android opens OS switch without requesting unavailable permission`() {
        assertEquals(NotificationPermissionAction.SETTINGS, notificationPermissionAction(32, false))
    }
    @Test fun `Android 13 requests missing runtime permission only on explicit user action`() {
        assertEquals(NotificationPermissionAction.REQUEST, notificationPermissionAction(33, false))
        assertEquals(NotificationPermissionAction.REQUEST, notificationPermissionAction(35, false))
    }
    @Test fun `granted runtime permission with app notifications disabled opens system settings`() {
        assertEquals(NotificationPermissionAction.SETTINGS, notificationPermissionAction(35, true))
    }
}
