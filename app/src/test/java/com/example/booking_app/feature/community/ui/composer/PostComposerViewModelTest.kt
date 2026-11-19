package com.example.booking_app.feature.community.ui.composer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.DraftPhoto
import com.example.booking_app.feature.community.domain.model.PostDraft
import com.example.booking_app.feature.community.domain.model.SignedUpload
import com.example.booking_app.feature.community.domain.usecase.EditPost
import com.example.booking_app.feature.community.domain.usecase.PublishPost
import com.example.booking_app.feature.community.domain.usecase.UploadPostPhotos
import com.example.booking_app.feature.community.noopSignedUrls
import com.example.booking_app.feature.community.post
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T042: soạn/sửa bài đăng — tối đa 4 ảnh, trạng thái từng ảnh, thử lại/bỏ ảnh, giữ requestId, bỏ bài gọi discard, mất mạng giữ nội dung. */
class PostComposerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val posts = mockk<PostRepository>()
    private val media = mockk<GroupMediaRepository>()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle(mapOf("groupId" to "g1"))) =
        PostComposerViewModel(
            savedStateHandle = handle,
            posts = posts,
            uploadPhotos = UploadPostPhotos(media),
            publishPost = PublishPost(posts),
            editPost = EditPost(posts),
            signedUrls = noopSignedUrls(),
        )

    private fun stubUploadSuccess() {
        coEvery { media.requestUploads(any(), any(), any()) } answers {
            val postId = secondArg<String>()
            val files = thirdArg<List<com.example.booking_app.feature.community.domain.model.UploadFile>>()
            Result.success(files.map { SignedUpload("g1/$postId/${it.index}.jpg", "https://up", "tok") })
        }
        every { media.upload(any(), any()) } answers {
            val uri = firstArg<String>()
            val target = secondArg<SignedUpload>()
            flowOf(
                DraftPhoto(uri, null, DraftPhoto.State.UPLOADING),
                DraftPhoto(uri, target.path, DraftPhoto.State.UPLOADED),
            )
        }
    }

    @Test
    fun `chon anh thu 5 thi bao toi da 4 va giu 4 anh`() = runTest {
        stubUploadSuccess()
        val vm = viewModel()

        vm.events.test {
            vm.onPhotosPicked(listOf("u1", "u2", "u3", "u4"))
            advanceUntilIdle()
            assertEquals(4, vm.state.value.photos.size)

            vm.onPhotosPicked(listOf("u5"))
            assertEquals(PostComposerEvent.MaxPhotosReached, awaitItem())
            assertEquals(4, vm.state.value.photos.size)
        }
    }

    @Test
    fun `nut Dang khoa khi con anh UPLOADING hoac FAILED, mo khi thu lai xong hoac bo anh`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { media.requestUploads(any(), any(), any()) } returns Result.success(
            listOf(SignedUpload("g1/p/0.jpg", "https://up", "tok")),
        )
        var attempts = 0
        every { media.upload("u1", any()) } answers {
            attempts++
            val target = secondArg<SignedUpload>()
            flow {
                emit(DraftPhoto("u1", null, DraftPhoto.State.UPLOADING))
                gate.await()
                if (attempts == 1) {
                    emit(DraftPhoto("u1", null, DraftPhoto.State.FAILED))
                } else {
                    emit(DraftPhoto("u1", target.path, DraftPhoto.State.UPLOADED))
                }
            }
        }

        val vm = viewModel()
        vm.onContentChanged("Xin chào")
        vm.onPhotosPicked(listOf("u1"))
        runCurrent()

        assertEquals(DraftPhoto.State.UPLOADING, vm.state.value.photos.single().state)
        assertFalse(vm.state.value.canPublish)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(DraftPhoto.State.FAILED, vm.state.value.photos.single().state)
        assertFalse(vm.state.value.canPublish)

        // Thử lại ảnh lỗi → thành công → mở nút Đăng.
        vm.onRetryPhoto("u1")
        advanceUntilIdle()
        assertEquals(DraftPhoto.State.UPLOADED, vm.state.value.photos.single().state)
        assertTrue(vm.state.value.canPublish)
    }

    @Test
    fun `bo anh loi thi nut Dang mo lai`() = runTest {
        coEvery { media.requestUploads(any(), any(), any()) } returns Result.failure(CommunityError.Network())
        val vm = viewModel()
        vm.onContentChanged("Nội dung")
        vm.onPhotosPicked(listOf("u1"))
        advanceUntilIdle()

        assertEquals(DraftPhoto.State.FAILED, vm.state.value.photos.single().state)
        assertFalse(vm.state.value.canPublish)

        vm.onRemovePhoto("u1")
        assertTrue(vm.state.value.photos.isEmpty())
        assertTrue(vm.state.value.canPublish)
    }

    @Test
    fun `requestId duoc giu qua SavedStateHandle`() {
        val handle = SavedStateHandle(mapOf("groupId" to "g1"))
        val vm1 = viewModel(handle)
        val vm2 = viewModel(handle)
        assertEquals(vm1.requestId, vm2.requestId)
    }

    @Test
    fun `thoat khi co noi dung thi hoi Bo bai dang viet va dong y goi discard`() = runTest {
        stubUploadSuccess()
        coEvery { media.discard("g1", any()) } returns Result.success(Unit)
        val vm = viewModel()
        vm.onContentChanged("Đang viết dở")
        vm.onPhotosPicked(listOf("u1"))
        advanceUntilIdle()

        vm.events.test {
            vm.onBackPressed()
            assertEquals(PostComposerEvent.ConfirmDiscard, awaitItem())

            vm.onDiscardConfirmed()
            advanceUntilIdle()
            assertEquals(PostComposerEvent.Close, awaitItem())
        }
        coVerify(exactly = 1) { media.discard("g1", vm.requestId) }
    }

    @Test
    fun `thoat khi chua nhap gi thi dong ngay khong hoi`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onBackPressed()
            assertEquals(PostComposerEvent.Close, awaitItem())
        }
    }

    @Test
    fun `mat mang khi dang bai giu nguyen noi dung va bao loi`() = runTest {
        val draftSlot = slot<PostDraft>()
        coEvery { posts.create("g1", capture(draftSlot)) } returns Result.failure(CommunityError.Network())
        val vm = viewModel()
        vm.onContentChanged("Nội dung quan trọng")

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            val event = awaitItem() as PostComposerEvent.ShowError
            assertTrue(event.error is CommunityError.Network)
        }
        assertEquals("Nội dung quan trọng", vm.state.value.content)
        assertEquals(vm.requestId, draftSlot.captured.requestId)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `che do sua nap san bai va goi update`() = runTest {
        val existing = post(id = "p9", content = "Nội dung cũ", photoPaths = listOf("g1/p9/0.jpg"))
        coEvery { posts.getPost("g1", "p9") } returns Result.success(existing)
        coEvery { posts.update("g1", any()) } returns Result.success(existing.copy(content = "Nội dung mới", isEdited = true))

        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1", "postId" to "p9")))
        advanceUntilIdle()

        assertTrue(vm.state.value.isEdit)
        assertEquals("Nội dung cũ", vm.state.value.content)
        assertEquals(1, vm.state.value.photos.size)

        vm.onContentChanged("Nội dung mới")
        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertTrue(awaitItem() is PostComposerEvent.Posted)
        }
        coVerify(exactly = 1) { posts.update("g1", match { it.requestId == "p9" && it.content == "Nội dung mới" }) }
    }
}
