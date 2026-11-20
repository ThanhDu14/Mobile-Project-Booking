package com.example.booking_app.feature.community.ui.event

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.Attendee
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** T052: danh sách người tham gia sự kiện (`EventAttendeesViewModel`). */
class EventAttendeesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val posts = mockk<PostRepository>()

    private fun viewModel() = EventAttendeesViewModel(
        SavedStateHandle(mapOf("groupId" to "g1", "postId" to "e1")),
        posts,
    )

    @Test
    fun `hien danh sach ten nguoi tham gia`() = runTest {
        val list = listOf(
            Attendee("u1", "Nguyễn Văn A", null, Instant.parse("2026-11-10T01:00:00Z")),
            Attendee("u2", "Trần Thị B", "u2/avatar.jpg", Instant.parse("2026-11-10T02:00:00Z")),
        )
        every { posts.observeAttendees("g1", "e1") } returns flowOf(list)

        viewModel().state.test {
            assertEquals(EventAttendeesUiState.Loading, awaitItem())
            val content = awaitItem() as EventAttendeesUiState.Content
            assertEquals(listOf("Nguyễn Văn A", "Trần Thị B"), content.attendees.map { it.displayName })
        }
    }

    @Test
    fun `chua co ai tham gia thi Empty`() = runTest {
        every { posts.observeAttendees("g1", "e1") } returns flowOf(emptyList())

        viewModel().state.test {
            assertEquals(EventAttendeesUiState.Loading, awaitItem())
            assertEquals(EventAttendeesUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `loi mang thi Error va retry tai lai`() = runTest {
        var calls = 0
        every { posts.observeAttendees("g1", "e1") } answers {
            if (calls++ == 0) flow { throw CommunityError.Network() }
            else flowOf(listOf(Attendee("u1", "An", null, null)))
        }

        val vm = viewModel()
        vm.state.test {
            assertEquals(EventAttendeesUiState.Loading, awaitItem())
            assertTrue(awaitItem() is EventAttendeesUiState.Error)
            vm.retry()
            assertEquals(EventAttendeesUiState.Loading, awaitItem())
            assertEquals(1, (awaitItem() as EventAttendeesUiState.Content).attendees.size)
        }
    }
}
