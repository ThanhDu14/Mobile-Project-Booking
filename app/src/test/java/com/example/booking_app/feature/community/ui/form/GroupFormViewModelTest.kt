package com.example.booking_app.feature.community.ui.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.GroupInput
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.domain.usecase.AvatarChange
import com.example.booking_app.feature.community.domain.usecase.CreateGroup
import com.example.booking_app.feature.community.domain.usecase.DeleteGroup
import com.example.booking_app.feature.community.domain.usecase.UpdateGroup
import com.example.booking_app.feature.community.group
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T022: form tạo/sửa nhóm. */
class GroupFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val createGroup = mockk<CreateGroup>()
    private val updateGroup = mockk<UpdateGroup>()
    private val groups = mockk<GroupRepository>()
    private val deleteGroup = DeleteGroup(groups)
    private val catalog = mockk<CatalogRepository> {
        coEvery { districts() } returns listOf(District("q10", "Quận 10", 1, true), District("q1", "Quận 1", 2, true))
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        GroupFormViewModel(handle, createGroup, updateGroup, deleteGroup, groups, catalog)

    private fun GroupFormViewModel.fillValid() {
        onNameChanged("CLB Cầu lông")
        onDescriptionChanged("Chơi tối thứ 6")
        onDistrictSelected("q10")
    }

    // ---------- Kiểm tra từng ô (AC 1.2) ----------

    @Test
    fun `ten trong, qua ngan, qua dai, mo ta qua dai, chua chon khu vuc deu bao loi tung o`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()
        assertEquals(FieldError.REQUIRED, vm.state.value.nameError)
        assertTrue(vm.state.value.districtError)

        vm.onNameChanged("ab")
        vm.submit()
        assertEquals(FieldError.TOO_SHORT, vm.state.value.nameError)

        vm.onNameChanged("a".repeat(51))
        vm.submit()
        assertEquals(FieldError.TOO_LONG, vm.state.value.nameError)

        vm.onNameChanged("Tên hợp lệ")
        vm.onDescriptionChanged("m".repeat(501))
        vm.onDistrictSelected("q10")
        vm.submit()
        assertNull(vm.state.value.nameError)
        assertEquals(FieldError.TOO_LONG, vm.state.value.descriptionError)
        assertFalse(vm.state.value.districtError)
        coVerify(exactly = 0) { createGroup(any(), any(), any()) }
    }

    @Test
    fun `ten co khoang trang dau cuoi duoc cat truoc khi dem`() = runTest {
        val vm = viewModel()
        vm.onNameChanged("   ab   ")
        vm.onDistrictSelected("q10")
        vm.submit()
        assertEquals(FieldError.TOO_SHORT, vm.state.value.nameError)
    }

    // ---------- requestId (AC 1.6) ----------

    @Test
    fun `requestId giu qua SavedStateHandle khi thu lai sau mat mang`() = runTest {
        val handle = SavedStateHandle()
        val ids = mutableListOf<String>()
        coEvery { createGroup(any(), any(), capture(ids)) } returnsMany listOf(
            Result.failure(CommunityError.Network()),
            Result.success(group()),
        )
        val vm = viewModel(handle)
        vm.fillValid()

        vm.submit()
        advanceUntilIdle()
        vm.submit()
        advanceUntilIdle()

        assertEquals(2, ids.size)
        assertEquals(ids[0], ids[1])
        assertNotNull(handle.get<String>("requestId"))
        // ViewModel mới (xoay màn hình) cùng handle thì dùng lại mã.
        coEvery { createGroup(any(), any(), capture(ids)) } returns Result.success(group())
        val vm2 = viewModel(handle)
        vm2.fillValid()
        vm2.submit()
        advanceUntilIdle()
        assertEquals(ids[0], ids[2])
    }

    // ---------- Ảnh đại diện lỗi (AC 1.3) ----------

    @Test
    fun `tai anh loi giu form, Thu lai thanh cong thi mo nhom`() = runTest {
        coEvery { createGroup(any(), "content://a", any()) } returns Result.failure(CommunityError.AvatarUploadFailed("g1"))
        coEvery { createGroup.retryAvatar("g1", "content://a") } returns Result.success(group())
        val vm = viewModel()
        vm.fillValid()
        vm.onAvatarPicked("content://a")

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals("g1", vm.state.value.avatarFailure?.groupId)
            assertEquals("CLB Cầu lông", vm.state.value.name)
            assertFalse(vm.state.value.saving)
            expectNoEvents()

            vm.retryAvatar()
            advanceUntilIdle()
            assertEquals(GroupFormEvent.OpenGroup("g1"), awaitItem())
        }
        assertNull(vm.state.value.avatarFailure)
    }

    @Test
    fun `Dung anh mac dinh thi mo nhom ma khong tai lai anh`() = runTest {
        coEvery { createGroup(any(), any(), any()) } returns Result.failure(CommunityError.AvatarUploadFailed("g1"))
        val vm = viewModel()
        vm.fillValid()
        vm.onAvatarPicked("content://a")

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            vm.useDefaultAvatar()
            assertEquals(GroupFormEvent.OpenGroup("g1"), awaitItem())
        }
        coVerify(exactly = 0) { createGroup.retryAvatar(any(), any()) }
    }

    @Test
    fun `bam gui lai khi dang cho thu lai anh thi khong tao nhom thu hai`() = runTest {
        coEvery { createGroup(any(), any(), any()) } returns Result.failure(CommunityError.AvatarUploadFailed("g1"))
        coEvery { createGroup.retryAvatar("g1", "content://a") } returns Result.failure(CommunityError.AvatarUploadFailed("g1"))
        val vm = viewModel()
        vm.fillValid()
        vm.onAvatarPicked("content://a")
        vm.submit()
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        coVerify(exactly = 1) { createGroup(any(), any(), any()) }
        coVerify(exactly = 1) { createGroup.retryAvatar("g1", "content://a") }
    }

    // ---------- Lỗi khác ----------

    @Test
    fun `OwnerLimitReached bao loi va khong dieu huong`() = runTest {
        coEvery { createGroup(any(), any(), any()) } returns Result.failure(CommunityError.OwnerLimitReached())
        val vm = viewModel()
        vm.fillValid()

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            val event = awaitItem() as GroupFormEvent.ShowError
            assertTrue(event.error is CommunityError.OwnerLimitReached)
            expectNoEvents()
        }
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `Network giu noi dung va mo khoa nut`() = runTest {
        coEvery { createGroup(any(), any(), any()) } returns Result.failure(CommunityError.Network())
        val vm = viewModel()
        vm.fillValid()
        vm.onVisibilityChanged(GroupVisibility.PRIVATE)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertTrue((awaitItem() as GroupFormEvent.ShowError).error is CommunityError.Network)
        }
        with(vm.state.value) {
            assertEquals("CLB Cầu lông", name)
            assertEquals("Chơi tối thứ 6", description)
            assertEquals("q10", districtId)
            assertEquals(GroupVisibility.PRIVATE, visibility)
            assertFalse(saving)
        }
    }

    @Test
    fun `dang gui thi nut khoa va goi lan hai bi bo qua`() = runTest {
        coEvery { createGroup(any(), any(), any()) } returns Result.success(group())
        val vm = viewModel()
        vm.fillValid()

        vm.submit()
        assertTrue(vm.state.value.saving)
        vm.submit()
        advanceUntilIdle()

        coVerify(exactly = 1) { createGroup(any(), any(), any()) }
    }

    // ---------- Thành công ----------

    @Test
    fun `tao thanh cong gui input da cat khoang trang va mo GroupDetail`() = runTest {
        val input = slot<GroupInput>()
        coEvery { createGroup(capture(input), null, any()) } returns Result.success(group(id = "g7"))
        val vm = viewModel()
        vm.onNameChanged("  CLB Cầu lông  ")
        vm.onDescriptionChanged("  Chơi tối  ")
        vm.onDistrictSelected("q1")
        vm.onVisibilityChanged(GroupVisibility.PRIVATE)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(GroupFormEvent.OpenGroup("g7"), awaitItem())
        }
        assertEquals(GroupInput("CLB Cầu lông", "Chơi tối", "q1", GroupVisibility.PRIVATE), input.captured)
    }

    // ---------- Chế độ sửa ----------

    @Test
    fun `che do sua nap san du lieu nhom va goi update`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(
            group(name = "Tên cũ", description = "Mô tả cũ", districtId = "q1", visibility = GroupVisibility.PRIVATE, avatarPath = "g1/avatar.jpg"),
        )
        coEvery { updateGroup("g1", any(), AvatarChange.Keep, any()) } returns Result.success(group())
        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1")))
        advanceUntilIdle()

        with(vm.state.value) {
            assertTrue(isEdit)
            assertEquals("Tên cũ", name)
            assertEquals("Mô tả cũ", description)
            assertEquals("q1", districtId)
            assertEquals(GroupVisibility.PRIVATE, visibility)
            assertEquals("g1/avatar.jpg", existingAvatarPath)
        }

        vm.onNameChanged("Tên mới")
        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(GroupFormEvent.Saved("g1"), awaitItem())
        }
        coVerify { updateGroup("g1", GroupInput("Tên mới", "Mô tả cũ", "q1", GroupVisibility.PRIVATE), AvatarChange.Keep, any()) }
        coVerify(exactly = 0) { createGroup(any(), any(), any()) }
    }

    @Test
    fun `che do sua nhom khong con thi bao NotFound`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(null)
        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1")))
        advanceUntilIdle()
        assertTrue(vm.state.value.loadError is CommunityError.GroupNotFound)
    }

    @Test
    fun `xoa nhom go sai ten thi bao ConfirmNameMismatch, go dung ten thi goi delete va phat Deleted`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(name = "CLB Quận 10"))
        coEvery { groups.delete("g1", "CLB Quận 10", any()) } returns Result.success(Unit)
        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1")))
        advanceUntilIdle()

        vm.events.test {
            vm.deleteGroup("CLB khác")
            val err = awaitItem() as GroupFormEvent.ShowError
            assertTrue(err.error is CommunityError.ConfirmNameMismatch)
            coVerify(exactly = 0) { groups.delete(any(), any(), any()) }

            vm.deleteGroup("CLB Quận 10")
            advanceUntilIdle()
            assertEquals(GroupFormEvent.Deleted, awaitItem())
        }
        coVerify(exactly = 1) { groups.delete("g1", "CLB Quận 10", any()) }
    }
}
