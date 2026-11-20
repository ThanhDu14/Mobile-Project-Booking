package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.AvatarAction
import com.example.booking_app.feature.community.domain.model.CreateGroupResult
import com.example.booking_app.feature.community.domain.model.GroupInput
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.domain.model.SignedUpload
import com.example.booking_app.feature.community.domain.model.UpdateGroupResult
import com.example.booking_app.feature.community.group
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T026: tạo nhóm, tải ảnh, xác nhận ảnh; ảnh lỗi trả `AvatarUploadFailed` kèm `groupId`. */
class CreateGroupTest {

    private val groups = mockk<GroupRepository>()
    private val media = mockk<GroupMediaRepository>()
    private val createGroup = CreateGroup(groups, media)

    private val input = GroupInput("CLB Cầu lông", "Mô tả", "q10", GroupVisibility.PUBLIC)
    private val target = SignedUpload("g1/avatar.jpg", "https://signed", "tok")

    @Test
    fun `khong co anh thi chi goi create`() = runTest {
        coEvery { groups.create(input, false, "r1") } returns Result.success(CreateGroupResult(group(), null))

        val result = createGroup(input, avatarUri = null, requestId = "r1")

        assertEquals("g1", result.getOrThrow().id)
        coVerify(exactly = 0) { media.uploadAvatar(any(), any()) }
        coVerify(exactly = 0) { groups.update(any(), any(), any(), any()) }
    }

    @Test
    fun `co anh thi tao roi tai roi CONFIRM va tra nhom co avatarPath`() = runTest {
        coEvery { groups.create(input, true, "r1") } returns Result.success(CreateGroupResult(group(), target))
        coEvery { media.uploadAvatar("content://a", target) } returns Result.success(Unit)
        coEvery { groups.update("g1", null, AvatarAction.CONFIRM, any()) } returns
            Result.success(UpdateGroupResult(group(avatarPath = "g1/avatar.jpg"), null, false))

        val result = createGroup(input, "content://a", "r1")

        assertEquals("g1/avatar.jpg", result.getOrThrow().avatarPath)
    }

    @Test
    fun `tai anh loi thi AvatarUploadFailed kem groupId va khong CONFIRM`() = runTest {
        coEvery { groups.create(input, true, "r1") } returns Result.success(CreateGroupResult(group(id = "g9"), target))
        coEvery { media.uploadAvatar(any(), any()) } returns Result.failure(CommunityError.Network())

        val error = createGroup(input, "content://a", "r1").exceptionOrNull()

        assertEquals("g9", (error as CommunityError.AvatarUploadFailed).groupId)
        coVerify(exactly = 0) { groups.update(any(), any(), AvatarAction.CONFIRM, any()) }
    }

    @Test
    fun `server khong tra avatarUpload thi cung la AvatarUploadFailed`() = runTest {
        coEvery { groups.create(input, true, "r1") } returns Result.success(CreateGroupResult(group(), null))

        val error = createGroup(input, "content://a", "r1").exceptionOrNull()

        assertTrue(error is CommunityError.AvatarUploadFailed)
    }

    @Test
    fun `create loi thi tra nguyen loi va khong tai anh`() = runTest {
        coEvery { groups.create(any(), any(), any()) } returns Result.failure(CommunityError.OwnerLimitReached())

        val error = createGroup(input, "content://a", "r1").exceptionOrNull()

        assertTrue(error is CommunityError.OwnerLimitReached)
        coVerify(exactly = 0) { media.uploadAvatar(any(), any()) }
    }

    @Test
    fun `retryAvatar xin dich moi bang REPLACE roi tai va CONFIRM`() = runTest {
        coEvery { groups.update("g1", null, AvatarAction.REPLACE, any()) } returns
            Result.success(UpdateGroupResult(group(), target, false))
        coEvery { media.uploadAvatar("content://a", target) } returns Result.success(Unit)
        coEvery { groups.update("g1", null, AvatarAction.CONFIRM, any()) } returns
            Result.success(UpdateGroupResult(group(avatarPath = "g1/avatar.jpg"), null, false))

        val result = createGroup.retryAvatar("g1", "content://a")

        assertEquals("g1/avatar.jpg", result.getOrThrow().avatarPath)
    }
}
