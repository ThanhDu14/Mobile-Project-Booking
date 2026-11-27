package com.example.booking_app.feature.community.ui.composer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.media.SignedUrlBatch
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.DraftPhoto
import com.example.booking_app.feature.community.domain.model.EventInfo
import com.example.booking_app.feature.community.domain.model.PostDraft
import com.example.booking_app.feature.community.domain.model.PostType
import com.example.booking_app.feature.community.domain.model.SignedUpload
import com.example.booking_app.feature.community.domain.model.UploadFile
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** T042: soạn/sửa bài đăng — tối đa 4 ảnh, trạng thái từng ảnh, thử lại/bỏ ảnh, giữ requestId, bỏ bài gọi discard, mất mạng giữ nội dung. */
class PostComposerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val posts = mockk<PostRepository>()
    private val media = mockk<GroupMediaRepository>()
    private val compressor = mockk<ImageCompressor> {
        coEvery { compress(any()) } returns ByteArray(1024) { 1 }
    }

    private val now = Instant.parse("2026-11-10T10:00:00Z")

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(mapOf("groupId" to "g1")),
        signedUrls: SignedUrlCache = noopSignedUrls(),
    ) =
        PostComposerViewModel(
            savedStateHandle = handle,
            posts = posts,
            uploadPhotos = UploadPostPhotos(media, compressor),
            publishPost = PublishPost(posts),
            editPost = EditPost(posts),
            signedUrls = signedUrls,
            clock = FakeClock { now.toEpochMilli() },
        )

    private fun stubUploadSuccess() {
        coEvery { media.requestUploads(any(), any(), any()) } answers {
            val postId = secondArg<String>()
            val files = thirdArg<List<UploadFile>>()
            Result.success(files.map { SignedUpload("g1/$postId/${it.index}.jpg", "https://up", "tok") })
        }
        every { media.uploadCompressed(any(), any(), any()) } answers {
            val uri = firstArg<String>()
            val target = thirdArg<SignedUpload>()
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
        every { media.uploadCompressed("u1", any(), any()) } answers {
            attempts++
            val target = thirdArg<SignedUpload>()
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

    // ---------- T052: chế độ EVENT ----------

    @Test
    fun `su kien thieu tieu de, dia diem, thoi gian qua khu va so du kien ngoai 2-100 deu bao loi`() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1", "type" to PostType.EVENT.name)))

        vm.submit()
        assertEquals(PostContentError.REQUIRED, vm.state.value.eventTitleError)
        assertEquals(EventTimeError.REQUIRED, vm.state.value.eventTimeError)
        assertEquals(PostContentError.REQUIRED, vm.state.value.eventLocationError)
        assertTrue(vm.state.value.expectedCountError)

        // Tiêu đề > 100, thời gian quá khứ (bằng now), địa điểm > 200, số dự kiến 1 và 101.
        vm.onEventTitleChanged("a".repeat(101))
        vm.onEventAtChanged(now)
        vm.onEventLocationChanged("b".repeat(201))
        vm.onExpectedCountChanged("1")
        vm.submit()

        assertEquals(PostContentError.TOO_LONG, vm.state.value.eventTitleError)
        assertEquals(EventTimeError.PAST, vm.state.value.eventTimeError)
        assertEquals(PostContentError.TOO_LONG, vm.state.value.eventLocationError)
        assertTrue(vm.state.value.expectedCountError)

        vm.onExpectedCountChanged("101")
        vm.submit()
        assertTrue(vm.state.value.expectedCountError)
        coVerify(exactly = 0) { posts.create(any(), any()) }
    }

    @Test
    fun `su kien hop le voi mo ta rong van dang thanh cong`() = runTest {
        val draftSlot = slot<PostDraft>()
        coEvery { posts.create("g1", capture(draftSlot)) } returns Result.success(post("e1").copy(type = PostType.EVENT))
        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1", "type" to PostType.EVENT.name)))

        val future = now.plusSeconds(3600)
        vm.onEventTitleChanged("Giao lưu tối thứ 6")
        vm.onEventAtChanged(future)
        vm.onEventLocationChanged("Sân Hòa Bình")
        vm.onExpectedCountChanged("8")

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertTrue(awaitItem() is PostComposerEvent.Posted)
        }
        assertNull(vm.state.value.contentError)
        assertEquals(PostType.EVENT, draftSlot.captured.type)
        assertEquals("Giao lưu tối thứ 6", draftSlot.captured.eventTitle)
        assertEquals(future, draftSlot.captured.eventAt)
        assertEquals("Sân Hòa Bình", draftSlot.captured.eventLocation)
        assertEquals(8, draftSlot.captured.expectedCount)
        assertEquals("", draftSlot.captured.content)
    }

    // ---------- US3 fix: slot ảnh gốc, nén trước, PENDING, PhotoMissing, contentError ----------

    @Test
    fun `sua bai go anh goc o slot 0 roi them anh moi thi anh moi khong nam o slot 0`() = runTest {
        stubUploadSuccess()
        val existing = post(id = "p9", content = "Bài cũ", photoPaths = listOf("g1/p9/0.jpg"))
        coEvery { posts.getPost("g1", "p9") } returns Result.success(existing)

        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1", "postId" to "p9")))
        advanceUntilIdle()

        vm.onRemovePhoto("g1/p9/0.jpg")
        vm.onPhotosPicked(listOf("content://new-photo"))
        advanceUntilIdle()

        val uploaded = vm.state.value.photos.single()
        assertEquals(DraftPhoto.State.UPLOADED, uploaded.state)
        assertEquals("g1/p9/1.jpg", uploaded.remotePath)
        coVerify(exactly = 1) {
            media.requestUploads("g1", "p9", match { it.single().index == 1 && it.single().size == 1024L })
        }
    }

    @Test
    fun `ghi de slot goc khi da du 4 anh goc thi xoa path do khoi SignedUrlCache`() = runTest {
        stubUploadSuccess()
        val origPaths = (0..3).map { "g1/p9/$it.jpg" }
        val existing = post(id = "p9", content = "Đủ 4 ảnh", photoPaths = origPaths)
        coEvery { posts.getPost("g1", "p9") } returns Result.success(existing)

        var fetchCount = 0
        val cache = SignedUrlCache(
            fetch = { paths ->
                fetchCount++
                Result.success(
                    SignedUrlBatch(
                        paths.associateWith { "https://cdn/$it?v=$fetchCount" },
                        Instant.ofEpochMilli(now.toEpochMilli() + 60L * 60 * 1000),
                    ),
                )
            },
            clock = FakeClock { now.toEpochMilli() },
        )

        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1", "postId" to "p9")), signedUrls = cache)
        advanceUntilIdle()
        assertEquals("https://cdn/g1/p9/0.jpg?v=1", cache.peek("g1/p9/0.jpg"))

        vm.onRemovePhoto("g1/p9/0.jpg")
        vm.onPhotosPicked(listOf("content://replace-0"))
        advanceUntilIdle()

        assertEquals("g1/p9/0.jpg", vm.state.value.photos.last().remotePath)
        assertNull(cache.peek("g1/p9/0.jpg"))
        assertEquals("https://cdn/g1/p9/1.jpg?v=1", cache.peek("g1/p9/1.jpg"))
    }

    @Test
    fun `nen anh loi hoac qua 2MB thi FAILED ngay ma khong goi mang`() = runTest {
        coEvery { compressor.compress("u-big") } throws ImageTooLargeException()
        val vm = viewModel()
        vm.onPhotosPicked(listOf("u-big"))
        advanceUntilIdle()

        assertEquals(DraftPhoto.State.FAILED, vm.state.value.photos.single().state)
        assertFalse(vm.state.value.canPublish)
        coVerify(exactly = 0) { media.requestUploads(any(), any(), any()) }
        coVerify(exactly = 0) { media.uploadCompressed(any(), any(), any()) }
    }

    @Test
    fun `canPublish va photosReady false khi con anh PENDING`() {
        val pending = DraftPhoto(localUri = "u1", remotePath = null, state = DraftPhoto.State.PENDING)
        val state = PostComposerState(content = "Xin chào", photos = listOf(pending))
        val draft = PostDraft(requestId = "r1", type = PostType.POST, content = "Xin chào", photos = listOf(pending))
        assertFalse(state.canPublish)
        assertFalse(draft.photosReady)
    }

    @Test
    fun `submit nhan PhotoMissing danh dau anh moi tai la FAILED va giu anh goc`() = runTest {
        stubUploadSuccess()
        val existing = post(id = "p9", content = "Bài cũ", photoPaths = listOf("g1/p9/0.jpg"))
        coEvery { posts.getPost("g1", "p9") } returns Result.success(existing)
        coEvery { posts.update("g1", any()) } returns Result.failure(CommunityError.PhotoMissing())

        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1", "postId" to "p9")))
        advanceUntilIdle()
        vm.onPhotosPicked(listOf("content://new-1"))
        advanceUntilIdle()

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            val event = awaitItem() as PostComposerEvent.ShowError
            assertTrue(event.error is CommunityError.PhotoMissing)
        }
        val photos = vm.state.value.photos.associateBy { it.localUri }
        assertEquals(DraftPhoto.State.UPLOADED, photos.getValue("g1/p9/0.jpg").state)
        assertEquals(DraftPhoto.State.FAILED, photos.getValue("content://new-1").state)
        assertNull(photos.getValue("content://new-1").remotePath)
        assertFalse(vm.state.value.saving)
        assertFalse(vm.state.value.canPublish)
    }

    @Test
    fun `bai dang thuong rong hoac qua 2000 ky tu bao contentError REQUIRED va TOO_LONG`() = runTest {
        val vm = viewModel()
        vm.onContentChanged("   ")
        vm.submit()
        assertEquals(PostContentError.REQUIRED, vm.state.value.contentError)

        vm.onContentChanged("x".repeat(PostComposerViewModel.MAX_CONTENT_LENGTH + 1))
        vm.submit()
        assertEquals(PostContentError.TOO_LONG, vm.state.value.contentError)
        coVerify(exactly = 0) { posts.create(any(), any()) }
    }

    @Test
    fun `che do sua su kien nap san truong event va goi update`() = runTest {
        val oldAt = now.plusSeconds(3600)
        val newAt = now.plusSeconds(7200)
        val existingEvent = post(id = "e9", content = "Mô tả cũ").copy(
            type = PostType.EVENT,
            event = EventInfo(
                title = "Giao lưu cũ",
                at = oldAt,
                location = "Sân A",
                expectedCount = 8,
                attendeeCount = 3,
                iAmAttending = true,
            ),
        )
        val updatedDraft = slot<PostDraft>()
        coEvery { posts.getPost("g1", "e9") } returns Result.success(existingEvent)
        coEvery { posts.update("g1", capture(updatedDraft)) } returns Result.success(
            existingEvent.copy(
                content = "Mô tả mới",
                isEdited = true,
                event = existingEvent.event!!.copy(
                    title = "Giao lưu mới",
                    at = newAt,
                    location = "Sân B",
                    expectedCount = 12,
                ),
            ),
        )

        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1", "postId" to "e9", "type" to PostType.EVENT.name)))
        advanceUntilIdle()

        assertTrue(vm.state.value.isEdit)
        assertTrue(vm.state.value.isEvent)
        assertEquals("Giao lưu cũ", vm.state.value.eventTitle)
        assertEquals(oldAt, vm.state.value.eventAt)
        assertEquals("Sân A", vm.state.value.eventLocation)
        assertEquals("8", vm.state.value.expectedCountText)

        vm.events.test {
            // Chưa sửa gì → thoát ngay không hỏi.
            vm.onBackPressed()
            assertEquals(PostComposerEvent.Close, awaitItem())

            // Sửa trường sự kiện → có thay đổi chưa lưu → hỏi xác nhận bỏ.
            vm.onEventTitleChanged("Giao lưu mới")
            vm.onEventAtChanged(newAt)
            vm.onEventLocationChanged("Sân B")
            vm.onExpectedCountChanged("12")
            vm.onContentChanged("Mô tả mới")
            vm.onBackPressed()
            assertEquals(PostComposerEvent.ConfirmDiscard, awaitItem())

            vm.submit()
            advanceUntilIdle()
            assertTrue(awaitItem() is PostComposerEvent.Posted)
        }

        coVerify(exactly = 1) { posts.update("g1", any()) }
        coVerify(exactly = 0) { posts.create(any(), any()) }
        assertEquals("e9", updatedDraft.captured.requestId)
        assertEquals(PostType.EVENT, updatedDraft.captured.type)
        assertEquals("Giao lưu mới", updatedDraft.captured.eventTitle)
        assertEquals(newAt, updatedDraft.captured.eventAt)
        assertEquals("Sân B", updatedDraft.captured.eventLocation)
        assertEquals(12, updatedDraft.captured.expectedCount)
        assertEquals("Mô tả mới", updatedDraft.captured.content)
    }

    // ---------- Fix vòng 2: NEW#1, NEW#3 ----------

    @Test
    fun `sua bai du 4 anh goc go 1 anh roi them anh moi vao cung slot thi Back hien ConfirmDiscard`() = runTest {
        stubUploadSuccess()
        val origPaths = (0..3).map { "g1/p9/$it.jpg" }
        val existing = post(id = "p9", content = "Đủ 4 ảnh", photoPaths = origPaths)
        coEvery { posts.getPost("g1", "p9") } returns Result.success(existing)

        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1", "postId" to "p9")))
        advanceUntilIdle()

        // Gỡ ảnh slot 3 rồi thêm ảnh mới → cấp lại đúng slot 3, danh sách remotePath trùng hệt ảnh gốc.
        vm.onRemovePhoto("g1/p9/3.jpg")
        vm.onPhotosPicked(listOf("content://replace-3"))
        advanceUntilIdle()

        assertEquals(origPaths, vm.state.value.photos.map { it.remotePath })
        assertEquals("content://replace-3", vm.state.value.photos.last().localUri)

        vm.events.test {
            vm.onBackPressed()
            assertEquals(PostComposerEvent.ConfirmDiscard, awaitItem())
        }
    }

    @Test
    fun `startUpload luon invalidate SignedUrlCache cho moi anh vua UPLOADED ke ca ngoai originalPhotoPaths`() = runTest {
        stubUploadSuccess()
        val existing = post(id = "p9", content = "Bài cũ", photoPaths = listOf("g1/p9/0.jpg"))
        coEvery { posts.getPost("g1", "p9") } returns Result.success(existing)

        val cache = SignedUrlCache(
            fetch = { paths ->
                Result.success(
                    SignedUrlBatch(
                        paths.associateWith { "https://cdn/$it?v=old" },
                        Instant.ofEpochMilli(now.toEpochMilli() + 60L * 60 * 1000),
                    ),
                )
            },
            clock = FakeClock { now.toEpochMilli() },
        )
        // Giả lập slot 1 đã từng được cache ở phiên sửa trước đó dù không còn trong originalPhotoPaths hiện tại.
        cache.resolve(listOf("g1/p9/1.jpg"))
        assertEquals("https://cdn/g1/p9/1.jpg?v=old", cache.peek("g1/p9/1.jpg"))

        val vm = viewModel(SavedStateHandle(mapOf("groupId" to "g1", "postId" to "p9")), signedUrls = cache)
        advanceUntilIdle()

        vm.onPhotosPicked(listOf("content://new-slot-1"))
        advanceUntilIdle()

        assertEquals("g1/p9/1.jpg", vm.state.value.photos.last().remotePath)
        assertNull(cache.peek("g1/p9/1.jpg"))
    }
}
