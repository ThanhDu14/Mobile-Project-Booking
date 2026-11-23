package com.example.booking_app.feature.notification

import com.example.booking_app.core.session.*
import com.example.booking_app.feature.notification.domain.*
import com.example.booking_app.feature.notification.ui.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    private val session = mockk<NotificationSession>()
    private val states = MutableStateFlow<String?>("alice")
    init { every { session.uids } returns states }
    private fun item() = Notification("one", "SYSTEM", "Title", "Body", emptyMap(), false, 1)
    @Test fun `inbox load error retry read and account switch clear state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<NotificationRepository>()
        val resolver = mockk<NotificationRouteResolver>()
        coEvery { repository.loadPage(any()) } throws IllegalStateException("offline") andThen NotificationPage(listOf(item()), null)
        coEvery { repository.markRead("one") } returns Unit
        coEvery { resolver.resolve(any()) } returns null
        val model = NotificationViewModel(repository, session, resolver)
        try {
            runCurrent(); assertTrue(model.state.value.error)
            model.refresh(); runCurrent(); assertEquals(1, model.state.value.items.size)
            assertFalse(model.state.value.loading)
            model.tap(item()); runCurrent(); assertTrue(model.state.value.items.single().isRead)
            states.value = null; runCurrent()
            assertTrue(model.state.value.items.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `successful empty inbox finishes loading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<NotificationRepository>()
        coEvery { repository.loadPage(any()) } returns NotificationPage(emptyList(), null)
        val model = NotificationViewModel(repository, session, mockk())
        try {
            runCurrent()
            assertFalse(model.state.value.loading)
            assertFalse(model.state.value.error)
            assertTrue(model.state.value.items.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `settings failed save preserves value and retry applies group only`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<NotificationPreferencesRepository>()
        coEvery { repository.loadSnapshot() } returns NotificationPreferenceSnapshot(NewAccountNotificationPreferences.defaults())
        val group = setOf(NotificationType.BOOKING_CONFIRMED, NotificationType.BOOKING_REJECTED)
        coEvery { repository.setEnabled(group, false) } throws IllegalStateException("offline") andThen Unit
        val model = NotificationSettingsViewModel(repository, session)
        try {
            runCurrent()
            model.toggle(group); runCurrent()
            assertTrue(model.state.value.error)
            assertTrue(model.state.value.values.getValue(NotificationType.BOOKING_CONFIRMED))
            model.retry(); runCurrent()
            assertFalse(model.state.value.values.getValue(NotificationType.BOOKING_CONFIRMED))
            assertTrue(model.state.value.values.getValue(NotificationType.BOOKING_NEW))
            model.toggle(setOf(NotificationType.BOOKING_NEW)); runCurrent()
            coVerify(exactly = 2) { repository.setEnabled(any(), any()) }
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `cached preferences are read only and retry reloads server before editing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<NotificationPreferencesRepository>()
        coEvery { repository.loadSnapshot() } returns NotificationPreferenceSnapshot(NewAccountNotificationPreferences.defaults(), true) andThen
            NotificationPreferenceSnapshot(NewAccountNotificationPreferences.defaults())
        coEvery { repository.setEnabled(any(), any()) } returns Unit
        val model = NotificationSettingsViewModel(repository, session)
        try {
            runCurrent()
            assertTrue(model.state.value.readOnly)
            assertTrue(model.state.value.error)
            model.toggle(setOf(NotificationType.PROMOTION)); runCurrent()
            coVerify(exactly = 0) { repository.setEnabled(any(), any()) }
            model.retry(); runCurrent()
            assertFalse(model.state.value.readOnly)
            assertFalse(model.state.value.error)
            model.toggle(setOf(NotificationType.PROMOTION)); runCurrent()
            coVerify(exactly = 1) { repository.setEnabled(setOf(NotificationType.PROMOTION), true) }
            states.value = "bob"; runCurrent()
            states.value = null; runCurrent()
            assertTrue(model.state.value.values.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
