package com.example.booking_app.feature.notification

import com.example.booking_app.feature.notification.domain.NewAccountNotificationPreferences
import com.example.booking_app.feature.notification.domain.NotificationType
import com.example.booking_app.feature.notification.domain.NotificationPreferences
import com.example.booking_app.feature.notification.domain.GroupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContractTest {
    @Test fun `missing keys use approved defaults and mandatory type ignores false`() {
        val values = NotificationPreferences.resolve(mapOf("BOOKING_NEW" to false, "CHAT_MESSAGE" to false))
        assertTrue(values.getValue(NotificationType.BOOKING_NEW))
        assertFalse(values.getValue(NotificationType.CHAT_MESSAGE))
        assertFalse(values.getValue(NotificationType.PROMOTION))
        assertTrue(values.getValue(NotificationType.BOOKING_REMINDER))
    }

    @Test fun `group state distinguishes mixed preferences`() {
        val types = setOf(NotificationType.BOOKING_CONFIRMED, NotificationType.BOOKING_REJECTED)
        assertEquals(GroupState.ON, NotificationPreferences.groupState(types, types.associateWith { true }))
        assertEquals(GroupState.OFF, NotificationPreferences.groupState(types, types.associateWith { false }))
        assertEquals(GroupState.MIXED, NotificationPreferences.groupState(types, types.associateWith { it == NotificationType.BOOKING_CONFIRMED }))
    }
    @Test fun `catalog maps all nineteen persisted types without coercing unknown types`() {
        assertEquals(19, NotificationType.entries.size)
        NotificationType.entries.forEach { assertEquals(it, NotificationType.fromStoredValue(it.name)) }
        assertNull(NotificationType.fromStoredValue("UNKNOWN"))
    }

    @Test fun `new account disables only promotion`() {
        val preferences = NewAccountNotificationPreferences.defaults()
        assertEquals(19, preferences.size)
        assertFalse(preferences.getValue(NotificationType.PROMOTION))
        assertTrue(preferences.filterKeys { it != NotificationType.PROMOTION }.values.all { it })
    }

    @Test fun `booking new cannot be disabled and other types obey explicit push preference`() {
        NotificationType.entries.forEach {
            assertTrue(NewAccountNotificationPreferences.isPushEligible(it, true))
            assertEquals(it == NotificationType.BOOKING_NEW, NewAccountNotificationPreferences.isPushEligible(it, false))
        }
    }
}
