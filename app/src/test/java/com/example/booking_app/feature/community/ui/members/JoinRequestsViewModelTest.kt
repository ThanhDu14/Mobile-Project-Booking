package com.example.booking_app.feature.community.ui.members

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.JoinRequest
import com.example.booking_app.feature.community.domain.usecase.ReviewJoinRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** T032 (phần yêu cầu tham gia): danh sách yêu cầu, duyệt/từ chối lạc quan, lỗi thì hoàn lại. */
class JoinRequestsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groups = mockk<GroupRepository>()

    private fun request(uid: String, removed: Boolean = false) =
        JoinRequest(uid, "Người $uid", null, removed, Instant.parse("2026-11-10T10:00:00Z"))

    private fun viewModel() = JoinRequestsViewModel(SavedStateHandle(mapOf("groupId" to "g1")), groups, ReviewJoinRequest(groups))

    private fun ids(vm: JoinRequestsViewModel) = (vm.state.value as JoinRequestsUiState.Content).items.map { it.userId }

    @Test
    fun `Loading roi Content theo thu tu cu nhat truoc kem nhan Tung bi moi ra`() = runTest {
        every { groups.observeJoinRequests("g1") } returns flowOf(listOf(request("a"), request("b", removed = true)))

        val vm = viewModel()
        vm.state.test {
            assertEquals(JoinRequestsUiState.Loading, awaitItem())
            val content = awaitItem() as JoinRequestsUiState.Content
            assertEquals(listOf("a", "b"), content.items.map { it.userId })
            assertTrue(content.items[1].wasRemoved)
        }
    }

    @Test
    fun `khong co yeu cau thi Empty`() = runTest {
        every { groups.observeJoinRequests("g1") } returns flowOf(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(JoinRequestsUiState.Empty, vm.state.value)
    }

    @Test
    fun `doc loi thi Error roi thu lai`() = runTest {
        var calls = 0
        every { groups.observeJoinRequests("g1") } answers {
            if (calls++ == 0) flow { throw CommunityError.Network() } else flowOf(listOf(request("a")))
        }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value is JoinRequestsUiState.Error)

        vm.retry()
        advanceUntilIdle()
        assertEquals(listOf("a"), ids(vm))
    }

    @Test
    fun `duyet la lac quan, dong bien mat ngay, server thanh cong thi giu nguyen`() = runTest {
        every { groups.observeJoinRequests("g1") } returns flowOf(listOf(request("a"), request("b")))
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { groups.approve("g1", "a", any()) } coAnswers { gate.await() }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onApprove("a")
        runCurrent()
        assertEquals(listOf("b"), ids(vm))

        gate.complete(Result.success(Unit))
        advanceUntilIdle()
        assertEquals(listOf("b"), ids(vm))
        coVerify(exactly = 1) { groups.approve("g1", "a", any()) }
    }

    @Test
    fun `tu choi la lac quan va goi reject`() = runTest {
        every { groups.observeJoinRequests("g1") } returns flowOf(listOf(request("a"), request("b")))
        coEvery { groups.reject("g1", "b", any()) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReject("b")
        advanceUntilIdle()

        assertEquals(listOf("a"), ids(vm))
        coVerify(exactly = 1) { groups.reject("g1", "b", any()) }
    }

    @Test
    fun `duyet loi thi hoan lai dung cho cu va bao loi`() = runTest {
        every { groups.observeJoinRequests("g1") } returns flowOf(listOf(request("a"), request("b"), request("c")))
        coEvery { groups.approve("g1", "b", any()) } returns Result.failure(CommunityError.Network())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onApprove("b")
            assertTrue((awaitItem() as JoinRequestsEvent.ShowError).error is CommunityError.Network)
        }
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), ids(vm))
    }

    @Test
    fun `tu choi loi thi hoan lai`() = runTest {
        every { groups.observeJoinRequests("g1") } returns flowOf(listOf(request("a")))
        coEvery { groups.reject("g1", "a", any()) } returns Result.failure(CommunityError.Forbidden())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReject("a")
        advanceUntilIdle()

        assertEquals(listOf("a"), ids(vm))
    }

    @Test
    fun `yeu cau da bi nguoi khac xu ly REQUEST_NOT_FOUND thi khong hoan lai`() = runTest {
        every { groups.observeJoinRequests("g1") } returns flowOf(listOf(request("a"), request("b")))
        coEvery { groups.approve("g1", "a", any()) } returns Result.failure(CommunityError.RequestNotFound())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onApprove("a")
            assertTrue((awaitItem() as JoinRequestsEvent.ShowError).error is CommunityError.RequestNotFound)
        }
        advanceUntilIdle()
        assertEquals(listOf("b"), ids(vm))
    }

    @Test
    fun `snapshot moi trong luc dang duyet khong lam dong hien lai`() = runTest {
        val source = MutableSharedFlow<List<JoinRequest>>(replay = 1)
        source.tryEmit(listOf(request("a"), request("b")))
        every { groups.observeJoinRequests("g1") } returns source
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { groups.approve("g1", "a", any()) } coAnswers { gate.await() }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onApprove("a")
        runCurrent()
        source.tryEmit(listOf(request("a"), request("b"), request("c")))
        runCurrent()

        assertEquals(listOf("b", "c"), ids(vm))
        gate.complete(Result.success(Unit))
        advanceUntilIdle()
    }

    @Test
    fun `duyet het yeu cau thi Empty`() = runTest {
        every { groups.observeJoinRequests("g1") } returns flowOf(listOf(request("a")))
        coEvery { groups.approve("g1", "a", any()) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onApprove("a")
        advanceUntilIdle()

        assertEquals(JoinRequestsUiState.Empty, vm.state.value)
    }
}
