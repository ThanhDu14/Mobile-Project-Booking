package com.example.booking_app.feature.notification

import com.example.booking_app.feature.notification.domain.NotificationType
import com.example.booking_app.feature.notification.ui.notificationGroups
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationGroupsTest {
    @Test fun `approved UI groups cover every type exactly once and keep mandatory type separate`() {
        val types = notificationGroups.flatMap { it.types }
        assertEquals(NotificationType.entries.toSet(), types.toSet())
        assertEquals(19, types.size)
        assertEquals(setOf(NotificationType.BOOKING_NEW), notificationGroups.single { NotificationType.BOOKING_NEW in it.types }.types)
    }
}
