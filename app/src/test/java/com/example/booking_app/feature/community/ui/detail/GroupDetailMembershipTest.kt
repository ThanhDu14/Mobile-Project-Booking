package com.example.booking_app.feature.community.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.ME
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.GroupMembership
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.domain.model.JoinResult
import com.example.booking_app.feature.community.domain.usecase.JoinGroup
import com.example.booking_app.feature.community.domain.usecase.LeaveGroup
import com.example.booking_app.feature.community.group
import com.example.booking_app.feature.community.ui.common.DistrictNames
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T032 (phần nút): nút tham gia/chờ duyệt/rời nhóm theo `GroupMembership` trên trang nhóm. */
class GroupDetailMembershipTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groups = mockk<GroupRepository>()
    private var uid: String? = ME
    private val session = mockk<SessionManager> { every { currentUid() } answers { uid } }
    private val catalog = mockk<CatalogRepository> {
        coEvery { districts() } returns listOf(District("q10", "Quận 10", 1, true))
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle(mapOf("groupId" to "g1"))) = GroupDetailViewModel(
        handle, groups, session, JoinGroup(groups), LeaveGroup(groups), DistrictNames(catalog),
    )

    private fun given(
        visibility: GroupVisibility = GroupVisibility.PUBLIC,
        membership: GroupMembership = GroupMembership.NONE,
        status: GroupStatus = GroupStatus.ACTIVE,
        pending: Int = 0,
    ) {
        every { groups.observeGroup("g1") } returns flowOf(
            group(ownerId = "other", visibility = visibility, status = status, pendingRequestCount = pending, districtId = "q10"),
        )
        every { groups.observeMembership("g1") } returns flowOf(membership)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.content(vm: GroupDetailViewModel): GroupDetailUiState.Content {
        advanceUntilIdle()
        return vm.state.value as GroupDetailUiState.Content
    }

    // ---------- Nút theo GroupMembership ----------

    @Test
    fun `nhom cong khai nguoi ngoai thay nut Tham gia`() = runTest {
        given(visibility = GroupVisibility.PUBLIC)
        assertEquals(JoinAction.JOIN, content(viewModel()).joinAction)
    }

    @Test
    fun `nhom rieng tu nguoi ngoai thay nut Xin tham gia`() = runTest {
        given(visibility = GroupVisibility.PRIVATE)
        assertEquals(JoinAction.REQUEST, content(viewModel()).joinAction)
    }

    @Test
    fun `dang cho duyet thi hien Dang cho duyet va Huy yeu cau`() = runTest {
        given(visibility = GroupVisibility.PRIVATE, membership = GroupMembership(role = null, pending = true))
        val c = content(viewModel())
        assertEquals(JoinAction.PENDING, c.joinAction)
        assertFalse(c.canLeave)
    }

    @Test
    fun `da la thanh vien thi an nut tham gia va co Roi nhom`() = runTest {
        given(membership = GroupMembership(GroupRole.MEMBER, false))
        val c = content(viewModel())
        assertEquals(JoinAction.NONE, c.joinAction)
        assertTrue(c.canLeave)
    }

    @Test
    fun `nhom bi khoa thi khong cho tham gia nhung thanh vien van roi duoc`() = runTest {
        given(status = GroupStatus.LOCKED)
        assertEquals(JoinAction.NONE, content(viewModel()).joinAction)

        given(status = GroupStatus.LOCKED, membership = GroupMembership(GroupRole.MEMBER, false))
        assertTrue(content(viewModel()).canLeave)
    }

    @Test
    fun `nut Yeu cau tham gia chi cho chu nhom va quan tri vien kem so cho duyet`() = runTest {
        given(membership = GroupMembership(GroupRole.ADMIN, false), pending = 3)
        val admin = content(viewModel())
        assertTrue(admin.canReviewRequests)
        assertEquals(3, admin.pendingRequestCount)

        given(membership = GroupMembership(GroupRole.MEMBER, false), pending = 3)
        assertFalse(content(viewModel()).canReviewRequests)

        given(pending = 3)
        assertFalse(content(viewModel()).canReviewRequests)
    }

    @Test
    fun `hien ten quan thay cho districtId`() = runTest {
        given()
        assertEquals("Quận 10", content(viewModel()).districtName)
    }

    // ---------- Tham gia / hủy yêu cầu / rời nhóm ----------

    @Test
    fun `tham gia nhom cong khai goi join va bao ket qua`() = runTest {
        given()
        coEvery { groups.join("g1", any()) } returns Result.success(JoinResult.JOINED)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onJoinClicked()
            assertEquals(GroupDetailEvent.JoinOutcome(JoinResult.JOINED), awaitItem())
        }
        coVerify(exactly = 1) { groups.join("g1", any()) }
    }

    @Test
    fun `nguoi tung bi moi ra vao nhom cong khai nhan REQUESTED thi bao da gui yeu cau`() = runTest {
        given(visibility = GroupVisibility.PUBLIC)
        coEvery { groups.join("g1", any()) } returns Result.success(JoinResult.REQUESTED)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onJoinClicked()
            assertEquals(GroupDetailEvent.JoinOutcome(JoinResult.REQUESTED), awaitItem())
        }
    }

    @Test
    fun `khach bam tham gia thi mo dang nhap kem returnTo va khong goi server`() = runTest {
        uid = null
        given()
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onJoinClicked()
            assertEquals(GroupDetailEvent.RequireLogin("smashnow://community/group/g1"), awaitItem())
        }
        coVerify(exactly = 0) { groups.join(any(), any()) }
    }

    @Test
    fun `join loi nghiep vu thi ShowError`() = runTest {
        given()
        coEvery { groups.join("g1", any()) } returns Result.failure(CommunityError.OwnerLimitReached())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onJoinClicked()
            assertTrue((awaitItem() as GroupDetailEvent.ShowError).error is CommunityError.OwnerLimitReached)
        }
    }

    @Test
    fun `dang xu ly thi bam lan hai bi bo qua`() = runTest {
        given()
        val gate = CompletableDeferred<Result<JoinResult>>()
        coEvery { groups.join("g1", any()) } coAnswers { gate.await() }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onJoinClicked()
        runCurrent()
        assertTrue((vm.state.value as GroupDetailUiState.Content).busy)
        vm.onJoinClicked()
        runCurrent()
        gate.complete(Result.success(JoinResult.JOINED))
        advanceUntilIdle()

        coVerify(exactly = 1) { groups.join("g1", any()) }
        assertFalse((vm.state.value as GroupDetailUiState.Content).busy)
    }

    @Test
    fun `thu lai sau loi mang dung cung requestId, thanh cong thi doi requestId`() = runTest {
        given()
        val ids = mutableListOf<String>()
        val captured = slot<String>()
        coEvery { groups.join("g1", capture(captured)) } answers {
            ids += captured.captured
            if (ids.size < 3) Result.failure(CommunityError.Network()) else Result.success(JoinResult.JOINED)
        }
        val vm = viewModel()
        advanceUntilIdle()

        repeat(3) {
            vm.onJoinClicked()
            advanceUntilIdle()
        }
        vm.onJoinClicked()
        advanceUntilIdle()

        assertEquals(1, ids.take(3).toSet().size)
        assertNotEquals(ids[2], ids[3])
    }

    @Test
    fun `huy yeu cau goi cancelRequest`() = runTest {
        given(visibility = GroupVisibility.PRIVATE, membership = GroupMembership(null, true))
        coEvery { groups.cancelRequest("g1", any()) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onCancelRequestClicked()
            assertEquals(GroupDetailEvent.RequestCancelled, awaitItem())
        }
        coVerify(exactly = 1) { groups.cancelRequest("g1", any()) }
    }

    @Test
    fun `thanh vien roi nhom thanh cong`() = runTest {
        given(membership = GroupMembership(GroupRole.MEMBER, false))
        coEvery { groups.leave("g1", any()) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onLeaveConfirmed()
            assertEquals(GroupDetailEvent.Left, awaitItem())
        }
    }

    @Test
    fun `chu nhom roi nhom bi OwnerMustTransfer thi mo chon nguoi nhan quyen`() = runTest {
        given(membership = GroupMembership(GroupRole.OWNER, false))
        coEvery { groups.leave("g1", any()) } returns Result.failure(CommunityError.OwnerMustTransfer(soleMember = false))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onLeaveConfirmed()
            assertEquals(GroupDetailEvent.OpenTransferOwner("g1"), awaitItem())
        }
    }

    @Test
    fun `chu nhom la thanh vien duy nhat roi nhom thi goi y xoa nhom`() = runTest {
        given(membership = GroupMembership(GroupRole.OWNER, false))
        coEvery { groups.leave("g1", any()) } returns Result.failure(CommunityError.OwnerMustTransfer(soleMember = true))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onLeaveConfirmed()
            assertEquals(GroupDetailEvent.SuggestDeleteGroup, awaitItem())
        }
    }

    // ---------- Không đọc được nhóm ----------

    @Test
    fun `doc nhom bi PERMISSION_DENIED Forbidden thi NotFound khong phai Error`() = runTest {
        every { groups.observeGroup("g1") } returns flow { throw CommunityError.Forbidden() }
        every { groups.observeMembership("g1") } returns flowOf(GroupMembership.NONE)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(GroupDetailUiState.NotFound, vm.state.value)
    }
}
