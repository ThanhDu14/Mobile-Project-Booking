package com.example.booking_app.feature.community.ui.members

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.ME
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.GroupMember
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.usecase.ManageMember
import com.example.booking_app.feature.community.group
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** T058: danh sách thành viên — sắp vai trò + joinedAt, menu theo GroupPermissions, làm mới khi thành công, lỗi FORBIDDEN. */
class GroupMembersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groups = mockk<GroupRepository>()
    private val session = mockk<SessionManager> { every { currentUid() } returns ME }

    private fun viewModel(selectTransfer: Boolean = false) = GroupMembersViewModel(
        SavedStateHandle(mapOf("groupId" to "g1", "selectTransferTarget" to selectTransfer)),
        groups,
        session,
        ManageMember(groups),
    )

    private fun member(uid: String, role: GroupRole, sec: Long) =
        GroupMember(uid, role, "User $uid", null, Instant.ofEpochSecond(sec))

    @Test
    fun `sap chu nhom, quan tri vien, roi thanh vien theo joinedAt`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME))
        every { groups.observeMembers("g1") } returns flowOf(
            listOf(
                member("m2", GroupRole.MEMBER, 20),
                member("a2", GroupRole.ADMIN, 30),
                member("m1", GroupRole.MEMBER, 10),
                member(ME, GroupRole.OWNER, 50),
                member("a1", GroupRole.ADMIN, 15),
            ),
        )

        val vm = viewModel()
        advanceUntilIdle()

        val content = vm.state.value as GroupMembersUiState.Content
        assertEquals(listOf(ME, "a1", "a2", "m1", "m2"), content.items.map { it.member.userId })
    }

    @Test
    fun `menu theo GroupPermissions cua chu nhom, quan tri vien va thanh vien thuong`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME))
        val roster = listOf(
            member(ME, GroupRole.OWNER, 1),
            member("admin1", GroupRole.ADMIN, 2),
            member("mem1", GroupRole.MEMBER, 3),
        )
        every { groups.observeMembers("g1") } returns flowOf(roster)

        val ownerVm = viewModel()
        advanceUntilIdle()
        val ownerItems = (ownerVm.state.value as GroupMembersUiState.Content).items.associateBy { it.member.userId }
        assertFalse(ownerItems.getValue(ME).hasActions)
        assertTrue(ownerItems.getValue("admin1").canRemoveAdmin)
        assertFalse(ownerItems.getValue("admin1").canMakeAdmin)
        assertTrue(ownerItems.getValue("admin1").canTransferOwner)
        assertTrue(ownerItems.getValue("admin1").canRemove)
        assertTrue(ownerItems.getValue("mem1").canMakeAdmin)
        assertTrue(ownerItems.getValue("mem1").canTransferOwner)
        assertTrue(ownerItems.getValue("mem1").canRemove)

        // Quản trị viên: chỉ mời thành viên thường, không phong/gỡ hay chuyển quyền.
        every { groups.observeMembers("g1") } returns flowOf(
            listOf(
                member("owner1", GroupRole.OWNER, 1),
                member(ME, GroupRole.ADMIN, 2),
                member("admin2", GroupRole.ADMIN, 3),
                member("mem1", GroupRole.MEMBER, 4),
            ),
        )
        val adminVm = viewModel()
        advanceUntilIdle()
        val adminItems = (adminVm.state.value as GroupMembersUiState.Content).items.associateBy { it.member.userId }
        assertFalse(adminItems.getValue("owner1").hasActions)
        assertFalse(adminItems.getValue("admin2").hasActions)
        assertFalse(adminItems.getValue("mem1").canMakeAdmin)
        assertFalse(adminItems.getValue("mem1").canTransferOwner)
        assertTrue(adminItems.getValue("mem1").canRemove)

        // Thành viên thường: không có bất kỳ thao tác quản lý nào.
        every { groups.observeMembers("g1") } returns flowOf(
            listOf(
                member("owner1", GroupRole.OWNER, 1),
                member("admin1", GroupRole.ADMIN, 2),
                member(ME, GroupRole.MEMBER, 3),
                member("mem2", GroupRole.MEMBER, 4),
            ),
        )
        val memberVm = viewModel()
        advanceUntilIdle()
        val memberItems = (memberVm.state.value as GroupMembersUiState.Content).items.associateBy { it.member.userId }
        assertTrue(memberItems.values.none { it.hasActions })

        // Nhóm LOCKED: ẩn mọi thao tác quản lý.
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME, status = GroupStatus.LOCKED))
        every { groups.observeMembers("g1") } returns flowOf(roster)
        val lockedVm = viewModel()
        advanceUntilIdle()
        val lockedItems = (lockedVm.state.value as GroupMembersUiState.Content).items
        assertTrue(lockedItems.none { it.hasActions })
    }

    @Test
    fun `phong quan tri vien thanh cong lam moi danh sach`() = runTest {
        var calls = 0
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME))
        every { groups.observeMembers("g1") } answers {
            if (calls++ == 0) {
                flowOf(listOf(member(ME, GroupRole.OWNER, 1), member("u2", GroupRole.MEMBER, 2)))
            } else {
                flowOf(listOf(member(ME, GroupRole.OWNER, 1), member("u2", GroupRole.ADMIN, 2)))
            }
        }
        coEvery { groups.setRole("g1", "u2", GroupRole.ADMIN, any()) } returns Result.success(Unit)

        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onMakeAdminConfirmed("u2")
            advanceUntilIdle()
            assertEquals(GroupMembersEvent.RoleUpdated, awaitItem())
        }
        val refreshed = (vm.state.value as GroupMembersUiState.Content).items.first { it.member.userId == "u2" }
        assertEquals(GroupRole.ADMIN, refreshed.member.role)
        coVerify(exactly = 1) { groups.setRole("g1", "u2", GroupRole.ADMIN, any()) }
    }

    @Test
    fun `loi FORBIDDEN phat su kien ShowError`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = "owner1"))
        every { groups.observeMembers("g1") } returns flowOf(
            listOf(member("owner1", GroupRole.OWNER, 1), member(ME, GroupRole.ADMIN, 2)),
        )
        coEvery { groups.remove("g1", "owner1", any()) } returns Result.failure(CommunityError.Forbidden())

        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRemoveConfirmed("owner1")
            advanceUntilIdle()
            val event = awaitItem() as GroupMembersEvent.ShowError
            assertTrue(event.error is CommunityError.Forbidden)
        }
    }

    @Test
    fun `go quan tri vien, chuyen quyen chu nhom va moi ra khoi nhom thanh cong phat dung event`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME))
        every { groups.observeMembers("g1") } returns flowOf(
            listOf(
                member(ME, GroupRole.OWNER, 1),
                member("admin1", GroupRole.ADMIN, 2),
                member("mem1", GroupRole.MEMBER, 3),
            ),
        )
        coEvery { groups.setRole("g1", "admin1", GroupRole.MEMBER, any()) } returns Result.success(Unit)
        coEvery { groups.transferOwner("g1", "admin1", any()) } returns Result.success(Unit)
        coEvery { groups.remove("g1", "mem1", any()) } returns Result.success(Unit)

        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRemoveAdminConfirmed("admin1")
            advanceUntilIdle()
            assertEquals(GroupMembersEvent.RoleUpdated, awaitItem())

            vm.onTransferOwnerConfirmed("admin1")
            advanceUntilIdle()
            assertEquals(GroupMembersEvent.OwnerTransferred, awaitItem())

            vm.onRemoveConfirmed("mem1")
            advanceUntilIdle()
            assertEquals(GroupMembersEvent.MemberRemoved, awaitItem())
        }
        coVerify(exactly = 1) { groups.setRole("g1", "admin1", GroupRole.MEMBER, any()) }
        coVerify(exactly = 1) { groups.transferOwner("g1", "admin1", any()) }
        coVerify(exactly = 1) { groups.remove("g1", "mem1", any()) }
    }

    @Test
    fun `actionsEnabled tat tren MemberItem khi dang busy va bat lai khi hoan tat`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME))
        every { groups.observeMembers("g1") } returns flowOf(
            listOf(member(ME, GroupRole.OWNER, 1), member("u2", GroupRole.MEMBER, 2)),
        )
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { groups.setRole("g1", "u2", GroupRole.ADMIN, any()) } coAnswers { gate.await() }

        val vm = viewModel()
        advanceUntilIdle()
        assertTrue((vm.state.value as GroupMembersUiState.Content).items.all { it.actionsEnabled })

        vm.onMakeAdminConfirmed("u2")
        runCurrent()
        val busyState = vm.state.value as GroupMembersUiState.Content
        assertTrue(busyState.busy)
        assertTrue(busyState.items.none { it.actionsEnabled })

        gate.complete(Result.failure(CommunityError.Network()))
        advanceUntilIdle()
        val doneState = vm.state.value as GroupMembersUiState.Content
        assertFalse(doneState.busy)
        assertTrue(doneState.items.all { it.actionsEnabled })
    }

    @Test
    fun `khong doc duoc nhom fallback DELETED khoa moi thao tac`() = runTest {
        every { groups.observeGroup("g1") } returns flow { throw CommunityError.Network() }
        every { groups.observeMembers("g1") } returns flowOf(
            listOf(member(ME, GroupRole.OWNER, 1), member("u2", GroupRole.MEMBER, 2)),
        )

        val vm = viewModel()
        advanceUntilIdle()

        val items = (vm.state.value as GroupMembersUiState.Content).items
        assertTrue(items.none { it.hasActions })
    }

    @Test
    fun `act bat ngoai le truc tiep thanh ShowError khong crash`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME))
        every { groups.observeMembers("g1") } returns flowOf(
            listOf(member(ME, GroupRole.OWNER, 1), member("u2", GroupRole.MEMBER, 2)),
        )
        coEvery { groups.remove("g1", "u2", any()) } throws IllegalStateException("boom")

        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRemoveConfirmed("u2")
            advanceUntilIdle()
            assertTrue(awaitItem() is GroupMembersEvent.ShowError)
        }
        assertFalse((vm.state.value as GroupMembersUiState.Content).busy)
    }
}
