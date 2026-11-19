package com.example.booking_app.feature.community.ui.report

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.ReportReason
import com.example.booking_app.feature.community.domain.usecase.ReportPost
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T065: báo cáo bài đăng — chọn lý do, "Khác" bắt buộc mô tả ≤ 500, thành công, AlreadyReported, Network giữ lựa chọn. */
class ReportPostViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val posts = mockk<PostRepository>()

    private fun viewModel() = ReportPostViewModel(
        SavedStateHandle(mapOf("groupId" to "g1", "postId" to "p1")),
        ReportPost(posts),
    )

    @Test
    fun `chua chon ly do thi bao loi va khong gui`() = runTest {
        val vm = viewModel()
        vm.submit()
        assertTrue(vm.state.value.reasonError)
        coVerify(exactly = 0) { posts.report(any(), any(), any(), any()) }
    }

    @Test
    fun `chon Khac bat buoc mo ta toi da 500 ky tu`() = runTest {
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.OTHER)
        assertTrue(vm.state.value.showDetailField)

        vm.submit()
        assertEquals(ReportDetailError.REQUIRED, vm.state.value.detailError)

        vm.onDetailChanged("a".repeat(501))
        vm.submit()
        assertEquals(ReportDetailError.TOO_LONG, vm.state.value.detailError)
        coVerify(exactly = 0) { posts.report(any(), any(), any(), any()) }
    }

    @Test
    fun `gui bao cao thanh cong phat su kien Sent`() = runTest {
        coEvery { posts.report("g1", "p1", ReportReason.SPAM, null) } returns Result.success("POST_g1_p1_me")
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.SPAM)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(ReportPostEvent.Sent, awaitItem())
        }
        coVerify(exactly = 1) { posts.report("g1", "p1", ReportReason.SPAM, null) }
    }

    @Test
    fun `da bao cao truoc do nhan AlreadyReported`() = runTest {
        coEvery { posts.report("g1", "p1", ReportReason.SCAM, null) } returns Result.failure(CommunityError.AlreadyReported())
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.SCAM)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            val event = awaitItem() as ReportPostEvent.ShowError
            assertTrue(event.error is CommunityError.AlreadyReported)
        }
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `mat mang giu nguyen ly do va mo ta da nhap`() = runTest {
        coEvery { posts.report("g1", "p1", ReportReason.OTHER, "Nội dung vi phạm") } returns
            Result.failure(CommunityError.Network())
        val vm = viewModel()
        vm.onReasonSelected(ReportReason.OTHER)
        vm.onDetailChanged("Nội dung vi phạm")

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            val event = awaitItem() as ReportPostEvent.ShowError
            assertTrue(event.error is CommunityError.Network)
        }
        assertEquals(ReportReason.OTHER, vm.state.value.selectedReason)
        assertEquals("Nội dung vi phạm", vm.state.value.detail)
        assertFalse(vm.state.value.submitting)
    }
}
