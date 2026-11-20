package com.example.booking_app.feature.chat.ui.report

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.R
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.chat.data.NetworkMonitor
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ReportReason
import com.example.booking_app.feature.chat.domain.usecase.ReportMessage
import com.example.booking_app.feature.chat.ui.common.ChatUiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T048: unit test cho [ReportMessageViewModel] (giống `ReportPostViewModelTest` của spec 090). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportMessageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ChatRepository>()

    private val networkMonitor = mockk<NetworkMonitor> { every { isCurrentlyOnline() } returns true }

    private fun viewModel() = ReportMessageViewModel(
        savedStateHandle = SavedStateHandle(mapOf("chatId" to "c1", "messageId" to "m1")),
        reportMessage = ReportMessage(repository),
        networkMonitor = networkMonitor,
    )

    @Test
    fun `chua chon ly do thi bao loi va khong gui`() = runTest {
        val vm = viewModel()
        vm.submit()
        assertTrue(vm.state.value.reasonError)
        coVerify(exactly = 0) { repository.report(any(), any(), any(), any()) }
    }

    @Test
    fun `chon Khac bat buoc mo ta toi da 500 ky tu`() = runTest {
        val exact500 = "a".repeat(500)
        coEvery { repository.report("c1", "m1", ReportReason.OTHER, exact500) } returns Result.success("MESSAGE_c1_m1_me")
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.OTHER)
        assertTrue(vm.state.value.showDetailField)

        vm.submit()
        assertEquals(ReportMessageDetailError.REQUIRED, vm.state.value.detailError)

        vm.onDetailChanged("   ")
        vm.submit()
        assertEquals(ReportMessageDetailError.REQUIRED, vm.state.value.detailError)

        vm.onDetailChanged("a".repeat(501))
        vm.submit()
        assertEquals(ReportMessageDetailError.TOO_LONG, vm.state.value.detailError)
        coVerify(exactly = 0) { repository.report(any(), any(), any(), any()) }

        vm.onDetailChanged(exact500)
        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(ReportMessageEvent.Sent, awaitItem())
        }
        coVerify(exactly = 1) { repository.report("c1", "m1", ReportReason.OTHER, exact500) }
    }

    @Test
    fun `nhap qua 500 ky tu o Khac roi doi sang ly do khac van gui thanh cong`() = runTest {
        coEvery { repository.report("c1", "m1", ReportReason.SPAM, null) } returns Result.success("MESSAGE_c1_m1_me")
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.OTHER)
        vm.onDetailChanged("x".repeat(501))
        vm.onReasonSelected(ReportReason.SPAM)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(ReportMessageEvent.Sent, awaitItem())
        }
        coVerify(exactly = 1) { repository.report("c1", "m1", ReportReason.SPAM, null) }
    }

    @Test
    fun `gui bao cao thanh cong phat su kien Sent`() = runTest {
        coEvery { repository.report("c1", "m1", ReportReason.SCAM, null) } returns Result.success("MESSAGE_c1_m1_me")
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.SCAM)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(ReportMessageEvent.Sent, awaitItem())
        }
        coVerify(exactly = 1) { repository.report("c1", "m1", ReportReason.SCAM, null) }
    }

    @Test
    fun `da bao cao truoc do nhan AlreadyReported`() = runTest {
        coEvery { repository.report("c1", "m1", ReportReason.SCAM, null) } returns Result.failure(ChatError.AlreadyReported())
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.SCAM)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            val event = awaitItem() as ReportMessageEvent.ShowError
            assertTrue(event.error is ChatError.AlreadyReported)
            assertEquals(R.string.chat_error_already_reported, ChatUiText.errorRes(event.error))
        }
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `mat mang giu nguyen ly do va mo ta da nhap`() = runTest {
        coEvery { repository.report("c1", "m1", ReportReason.OTHER, "Tin nhắn lừa đảo") } returns
            Result.failure(ChatError.Network())
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.OTHER)
        vm.onDetailChanged("Tin nhắn lừa đảo")

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            val event = awaitItem() as ReportMessageEvent.ShowError
            assertTrue(event.error is ChatError.Network)
        }
        assertEquals(ReportReason.OTHER, vm.state.value.selectedReason)
        assertEquals("Tin nhắn lừa đảo", vm.state.value.detail)
        assertFalse(vm.state.value.submitting)
        assertTrue(vm.state.value.submitError is ChatError.Network)
    }

    @Test
    fun `gui bao cao treo qua timeout thi dung submitting hien loi mang va cho thu lai`() = runTest {
        var attempt = 0
        coEvery { repository.report("c1", "m1", ReportReason.SPAM, null) } coAnswers {
            attempt++
            if (attempt == 1) {
                delay(ReportMessageViewModel.SUBMIT_TIMEOUT_MS + 5_000L)
                Result.success("late")
            } else {
                Result.success("MESSAGE_c1_m1_me")
            }
        }
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.SPAM)

        vm.events.test {
            vm.submit()
            advanceTimeBy(ReportMessageViewModel.SUBMIT_TIMEOUT_MS + 1L)
            val errEvent = awaitItem() as ReportMessageEvent.ShowError
            assertTrue(errEvent.error is ChatError.Network)
            assertFalse(vm.state.value.submitting)
            assertTrue(vm.state.value.submitError is ChatError.Network)

            // Thử lại lần 2 -> xóa lỗi cũ và gửi thành công.
            vm.submit()
            advanceUntilIdle()
            assertEquals(ReportMessageEvent.Sent, awaitItem())
            assertNull(vm.state.value.submitError)
        }
        coVerify(exactly = 2) { repository.report("c1", "m1", ReportReason.SPAM, null) }
    }

    @Test
    fun `M5 dang offline bao loi mang ngay, khong goi server`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.entries.first { it != ReportReason.OTHER })
        vm.events.test {
            vm.submit()
            assertTrue((awaitItem() as ReportMessageEvent.ShowError).error is ChatError.Network)
        }
        assertFalse(vm.state.value.submitting)
        assertTrue(vm.state.value.submitError is ChatError.Network)
        coVerify(exactly = 0) { repository.report(any(), any(), any(), any()) }
    }
}
